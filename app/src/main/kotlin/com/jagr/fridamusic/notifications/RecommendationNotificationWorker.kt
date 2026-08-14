package com.jagr.fridamusic.notifications

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.jagr.fridamusic.BuildConfig
import com.jagr.fridamusic.constants.InternalNotificationTestContentHistoryKey
import com.jagr.fridamusic.constants.InternalNotificationTestCursorKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecommendationNotificationWorkerEntryPoint {
    fun musicDatabase(): MusicDatabase
}

class RecommendationNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val preferences = applicationContext.dataStore.data.first()
            val isForcedInternalTest = inputData.getBoolean(FORCE_TEST_INPUT_KEY, false) &&
                BuildConfig.DEBUG &&
                BuildConfig.FLAVOR_abi == "universal" &&
                BuildConfig.FLAVOR_variant == "gms"
            if (!isForcedInternalTest) {
                NotificationPolicy.preflightSkipReason(preferences, now)?.let { reason ->
                    Timber.tag(TAG).d("Skipped: %s", reason)
                    return Result.success()
                }
            } else {
                Timber.tag(TAG).d("Internal test run: timing rules bypassed")
            }

            if (!RecommendationNotificationManager.canPost(applicationContext)) {
                Timber.tag(TAG).d("Skipped: notification_permission_or_channel")
                return Result.success()
            }

            val database = EntryPointAccessors.fromApplication(
                applicationContext,
                RecommendationNotificationWorkerEntryPoint::class.java,
            ).musicDatabase()
            val candidates = NotificationCandidateProvider(database).getCandidates(now)
            Timber.tag(TAG).d("Candidate count=%d", candidates.size)

            val selection = if (isForcedInternalTest) {
                val requestedType = inputData.getString(FORCE_TEST_TYPE_INPUT_KEY)?.let { name ->
                    runCatching { NotificationCandidateType.valueOf(name) }.getOrNull()
                }
                val testCandidate = requestedType?.let { type ->
                    internalTestCandidate(
                        candidates = candidates,
                        requestedType = type,
                        recentContentKeys = internalTestContentHistory(
                            value = preferences[InternalNotificationTestContentHistoryKey],
                        ),
                    )
                }
                NotificationSelection(
                    candidate = testCandidate,
                    skipReason = when {
                        requestedType == null -> "invalid_internal_test_type"
                        testCandidate == null -> "no_valid_internal_candidate:${requestedType.name}"
                        else -> null
                    },
                )
            } else {
                NotificationPolicy.select(
                    candidates = candidates,
                    preferences = preferences,
                    now = now,
                )
            }
            val candidate = selection.candidate
            if (candidate == null) {
                Timber.tag(TAG).d("Skipped: %s", selection.skipReason ?: "policy")
                return Result.success()
            }
            Timber.tag(TAG).d(
                "Selected candidate type=%s source=%s",
                candidate.type.name,
                candidate.source,
            )

            val message = NotificationMessageGenerator.generate(
                context = applicationContext,
                candidate = candidate,
                recentTemplateIds = NotificationPolicy.recentTemplateIds(preferences),
                now = now,
                forcedTemplateIndex = if (isForcedInternalTest) {
                    internalTestCursor(
                        value = preferences[InternalNotificationTestCursorKey],
                        type = candidate.type,
                    )
                } else {
                    null
                },
            )
            val artwork = candidate.artworkUrl?.let { url -> loadArtwork(url) }

            if (!RecommendationNotificationManager.show(applicationContext, candidate, message, artwork)) {
                Timber.tag(TAG).d("Skipped: notification_not_posted")
                return Result.success()
            }

            if (isForcedInternalTest) {
                applicationContext.dataStore.edit { settings ->
                    settings[InternalNotificationTestCursorKey] = advanceInternalTestCursor(
                        value = settings[InternalNotificationTestCursorKey],
                        type = candidate.type,
                    )
                    settings[InternalNotificationTestContentHistoryKey] = recordInternalTestContent(
                        value = settings[InternalNotificationTestContentHistoryKey],
                        type = candidate.type,
                        contentKey = candidate.internalTestContentKey(),
                    )
                }
            } else {
                applicationContext.dataStore.edit { settings ->
                    NotificationPolicy.recordDelivery(
                        settings = settings,
                        candidate = candidate,
                        templateId = message.templateId,
                        now = now,
                    )
                }
            }
            Timber.tag(TAG).d("Notification posted type=%s", candidate.type.name)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Recommendation notification work skipped after failure")
            Result.success()
        }
    }

    private suspend fun loadArtwork(url: String): Bitmap? =
        executeArtworkRequest(url, cacheOnly = true) ?: executeArtworkRequest(url, cacheOnly = false)

    private fun internalTestCandidate(
        candidates: List<NotificationCandidate>,
        requestedType: NotificationCandidateType,
        recentContentKeys: List<String>,
    ): NotificationCandidate? {
        val matchingCandidates = candidates.filter { candidate -> candidate.type == requestedType }
        if (matchingCandidates.isEmpty()) return null
        val recentPositions = recentContentKeys.withIndex().associate { (index, key) -> key to index }
        return matchingCandidates.firstOrNull { candidate ->
            candidate.internalTestContentKey() !in recentPositions
        } ?: matchingCandidates.maxByOrNull { candidate ->
            recentPositions[candidate.internalTestContentKey()] ?: -1
        }
    }

    private fun internalTestContentHistory(value: String?): List<String> =
        parseInternalTestContentHistory(value).map { entry -> entry.second }

    private fun recordInternalTestContent(
        value: String?,
        type: NotificationCandidateType,
        contentKey: String,
    ): String {
        val entries = buildList {
            add(type to contentKey)
            addAll(
                parseInternalTestContentHistory(value)
                    .filterNot { entry -> entry.second == contentKey },
            )
        }
        val retainedPerType = mutableMapOf<NotificationCandidateType, Int>()
        return entries.filter { (entryType, _) ->
            val count = retainedPerType[entryType] ?: 0
            retainedPerType[entryType] = count + 1
            count < INTERNAL_TEST_HISTORY_PER_TYPE
        }.joinToString("\n") { (entryType, id) -> "${entryType.name}|$id" }
    }

    private fun NotificationCandidate.internalTestContentKey(): String =
        "${contentType.name}:${contentId.replace('|', '_').replace('\n', '_')}"

    private fun parseInternalTestContentHistory(value: String?): List<Pair<NotificationCandidateType, String>> =
        value.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            val type = parts.getOrNull(0)?.let { name ->
                runCatching { NotificationCandidateType.valueOf(name) }.getOrNull()
            }
            val candidateId = parts.getOrNull(1)
            if (type == null || candidateId.isNullOrBlank()) null else type to candidateId
        }.toList()

    private fun internalTestCursor(value: String?, type: NotificationCandidateType): Int =
        value.orEmpty().lineSequence().firstNotNullOfOrNull { line ->
            val parts = line.split('|', limit = 2)
            if (parts.getOrNull(0) == type.name) parts.getOrNull(1)?.toIntOrNull() else null
        } ?: 0

    private fun advanceInternalTestCursor(value: String?, type: NotificationCandidateType): String {
        val cursors = value.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            val candidateType = parts.getOrNull(0)?.let { name ->
                runCatching { NotificationCandidateType.valueOf(name) }.getOrNull()
            }
            val cursor = parts.getOrNull(1)?.toIntOrNull()
            if (candidateType == null || cursor == null) null else candidateType to cursor
        }.toMap().toMutableMap()
        cursors[type] = (cursors[type] ?: 0) + 1
        return cursors.entries.joinToString("\n") { (candidateType, cursor) ->
            "${candidateType.name}|$cursor"
        }
    }

    private suspend fun executeArtworkRequest(url: String, cacheOnly: Boolean): Bitmap? {
        return try {
            val requestBuilder = ImageRequest.Builder(applicationContext)
                .data(url)
                .size(512, 512)
                .allowHardware(false)
            if (cacheOnly) requestBuilder.networkCachePolicy(CachePolicy.DISABLED)
            applicationContext.imageLoader.execute(requestBuilder.build()).image?.toBitmap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        internal const val FORCE_TEST_INPUT_KEY = "force_internal_notification_test"
        internal const val FORCE_TEST_TYPE_INPUT_KEY = "force_internal_notification_test_type"
        private const val INTERNAL_TEST_HISTORY_PER_TYPE = 16
        const val TAG = "RecommendationWorker"
    }
}

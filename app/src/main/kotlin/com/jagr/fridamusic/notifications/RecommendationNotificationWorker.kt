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
import com.jagr.fridamusic.constants.MusicRecommendationNotificationsKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.NotificationHistoryEntity
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.time.Instant
import com.jagr.fridamusic.constants.LastAppOpenAtKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

    override suspend fun doWork(): Result = deliveryMutex.withLock { evaluate() }

    private suspend fun evaluate(): Result {
        val isForcedInternalTest = inputData.getBoolean(FORCE_TEST_INPUT_KEY, false) &&
            BuildConfig.DEBUG &&
            BuildConfig.FLAVOR_abi == "universal" &&
            BuildConfig.FLAVOR_variant == "gms"
        val scheduledSlot = NotificationSlot.fromInput(inputData.getString(SLOT_INPUT_KEY))
        var reevaluate = true
        var cancelled = false
        return try {
            Timber.tag(TAG).d("evaluating slot=%s", scheduledSlot)
            val now = System.currentTimeMillis()
            val preferences = applicationContext.dataStore.data.first()
            val deliverySlot = scheduledSlot ?: NotificationSlot.forTime(now) ?: NotificationSlot.MORNING
            if (!isForcedInternalTest) {
                if (scheduledSlot == null) {
                    Timber.tag(TAG).d("Skipped: missing_slot")
                    return Result.success()
                }
                NotificationPolicy.preflightSkipReason(preferences, now, deliverySlot)?.let { reason ->
                    reevaluate = reason.startsWith("outside_slot") || reason == "quiet_hours"
                    Timber.tag(TAG).d("permanent_skip: %s", reason)
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
                .filter { RecommendationNotificationManager.canPost(applicationContext, it.type) }
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
                    slot = deliverySlot,
                )
            }
            val candidate = selection.candidate
            if (candidate == null) {
                Timber.tag(TAG).d("temporary_skip: %s", selection.skipReason ?: "policy")
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

            val deliveredAt = System.currentTimeMillis()
            if (!isForcedInternalTest) {
                val fresh = applicationContext.dataStore.data.first()
                if (NotificationPolicy.preflightSkipReason(fresh, deliveredAt, deliverySlot) != null ||
                    NotificationPolicy.select(listOf(candidate), fresh, deliveredAt, deliverySlot).candidate == null
                ) return Result.success()
            }
            withContext(NonCancellable) {
                if (!RecommendationNotificationManager.show(applicationContext, candidate, message, artwork)) {
                    Timber.tag(TAG).d("Skipped: notification_not_posted")
                    return@withContext false
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
                            now = deliveredAt,
                            slot = deliverySlot,
                        )
                    }
                }
                reevaluate = false
                if (!isForcedInternalTest) {
                    database.insertNotificationHistory(
                        NotificationHistoryEntity(
                            id = "${candidate.id}:$now",
                            candidateId = candidate.id,
                            type = candidate.type.name,
                            contentType = candidate.contentType.name,
                            contentId = candidate.contentId,
                            title = message.title,
                            body = message.body,
                            artworkUrl = candidate.artworkUrl,
                            deepLink = candidate.deepLink,
                            source = candidate.source,
                            reason = candidate.reason,
                            deliveredAt = deliveredAt,
                        ),
                    )
                    database.trimNotificationHistory(MAX_HISTORY_ENTRIES)

                }
                true
            }.also { posted -> if (!posted) return Result.success() }
            Timber.tag(TAG).d("Notification posted type=%s", candidate.type.name)
            Result.success()
        } catch (error: CancellationException) {
            cancelled = true
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Recommendation notification work skipped after failure")
            Result.success()
        } finally {
            if (!cancelled && !isForcedInternalTest && scheduledSlot != null) {
                withContext(NonCancellable) {
                    val stillEnabled = applicationContext.dataStore.data.first()[MusicRecommendationNotificationsKey] == true
                    if (stillEnabled) {
                        val now = ZonedDateTime.now()
                        val lastOpen = applicationContext.dataStore.data.first()[LastAppOpenAtKey] ?: 0L
                        val grace = Instant.ofEpochMilli(lastOpen.coerceAtMost(System.currentTimeMillis()) + NotificationPolicy.RECENT_USE_GRACE_PERIOD_MILLIS).atZone(now.zone)
                        RecommendationNotificationScheduler(applicationContext).scheduleNext(
                            scheduledSlot, if (reevaluate) NotificationTiming.retry(scheduledSlot, now, grace) else null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadArtwork(url: String): Bitmap? =
        withTimeoutOrNull(8_000) { executeArtworkRequest(url, cacheOnly = true) ?: executeArtworkRequest(url, cacheOnly = false) }

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
        private val deliveryMutex = Mutex()
        internal const val FORCE_TEST_INPUT_KEY = "force_internal_notification_test"
        internal const val FORCE_TEST_TYPE_INPUT_KEY = "force_internal_notification_test_type"
        internal const val SLOT_INPUT_KEY = "notification_slot"
        private const val INTERNAL_TEST_HISTORY_PER_TYPE = 16
        private const val MAX_HISTORY_ENTRIES = 200
        const val TAG = "RecommendationWorker"
    }
}

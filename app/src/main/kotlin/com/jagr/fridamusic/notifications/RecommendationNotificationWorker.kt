package com.jagr.fridamusic.notifications

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.jagr.fridamusic.constants.LastRecommendationNotificationAlbumIdKey
import com.jagr.fridamusic.constants.LastRecommendationNotificationAtKey
import com.jagr.fridamusic.constants.MusicRecommendationNotificationsKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

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
            val preferences = applicationContext.dataStore.data.first()
            if (preferences[MusicRecommendationNotificationsKey] != true) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val lastNotificationAt = preferences[LastRecommendationNotificationAtKey] ?: 0L
            if (lastNotificationAt > now - COOLDOWN_MILLIS) {
                return Result.success()
            }

            val lastAlbumId = preferences[LastRecommendationNotificationAlbumIdKey]
            val database = EntryPointAccessors.fromApplication(
                applicationContext,
                RecommendationNotificationWorkerEntryPoint::class.java,
            ).musicDatabase()

            val album = database.mostPlayedAlbums(
                fromTimeStamp = 0L,
                limit = CANDIDATE_LIMIT,
            ).first().firstOrNull { item ->
                !item.album.isLocal &&
                    item.id.isNotBlank() &&
                    item.id != lastAlbumId &&
                    item.title.isNotBlank() &&
                    !item.thumbnailUrl.isNullOrBlank()
            } ?: return Result.success()

            val candidate = RecommendationAlbumCandidate(
                id = album.id,
                title = album.title,
                artist = album.artists.joinToString(", ") { it.name },
                artworkUrl = album.thumbnailUrl!!,
            )
            val artwork = loadArtwork(candidate.artworkUrl) ?: return Result.retry()

            if (!RecommendationNotificationManager.show(applicationContext, candidate, artwork)) {
                return Result.success()
            }

            applicationContext.dataStore.edit { settings ->
                settings[LastRecommendationNotificationAtKey] = now
                settings[LastRecommendationNotificationAlbumIdKey] = candidate.id
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Recommendation notification work failed")
            Result.retry()
        }
    }

    private suspend fun loadArtwork(url: String): Bitmap? {
        val request = ImageRequest.Builder(applicationContext)
            .data(url)
            .size(512, 512)
            .allowHardware(false)
            .build()
        return applicationContext.imageLoader.execute(request).image?.toBitmap()
    }

    private companion object {
        const val TAG = "RecommendationWorker"
        const val CANDIDATE_LIMIT = 20
        val COOLDOWN_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}

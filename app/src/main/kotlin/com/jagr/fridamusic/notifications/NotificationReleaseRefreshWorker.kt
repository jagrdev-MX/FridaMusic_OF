package com.jagr.fridamusic.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jagr.fridamusic.constants.MusicRecommendationNotificationsKey
import com.jagr.fridamusic.constants.NewReleaseNotificationsKey
import com.jagr.fridamusic.utils.dataStore
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class NotificationReleaseRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.dataStore.data.first()
        if (preferences[MusicRecommendationNotificationsKey] != true || preferences[NewReleaseNotificationsKey] == false) return Result.success()
        val database = EntryPointAccessors.fromApplication(applicationContext, RecommendationNotificationWorkerEntryPoint::class.java).musicDatabase()
        val cache = applicationContext.getSharedPreferences("notification_release_refresh", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - cache.getLong("attempt", 0) in 0 until 20 * 60 * 60 * 1000L) return Result.success()
        cache.edit().putLong("attempt", now).apply()
        try {
            database.notificationFollowedArtists(10).first()
                .filter { it.isYouTubeArtist }.take(3).forEach { artist ->
                    withTimeoutOrNull(15_000) {
                        YouTube.artist(artist.id).getOrNull()?.let { page ->
                            val albums = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
                                .filter { it.year == java.time.Year.now().value }.distinctBy { it.browseId }.take(10)
                                .map { album ->
                                    if (album.artists.isNullOrEmpty()) album.copy(artists = listOf(com.music.innertube.models.Artist(artist.name, artist.id)))
                                    else album
                                }
                            database.cacheNotificationCandidates(null, albums)
                        }
                    }
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("RecommendationWorker").d("temporary_skip: release_refresh_failed %s", error.javaClass.simpleName)
        }
        return Result.success()
    }
}

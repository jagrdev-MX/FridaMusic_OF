package com.jagr.fridamusic.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jagr.fridamusic.constants.MusicRecommendationNotificationsKey
import com.jagr.fridamusic.utils.dataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class RecommendationNotificationScheduler(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    suspend fun observeAndSchedule() {
        applicationContext.dataStore.data
            .map { preferences -> preferences[MusicRecommendationNotificationsKey] ?: false }
            .distinctUntilChanged()
            .collect { enabled ->
                if (enabled) schedule() else cancel()
            }
    }

    private fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<RecommendationNotificationWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "music_recommendation_notifications"
        const val WORK_TAG = "music_recommendation"
        const val REPEAT_INTERVAL_HOURS = 24L
    }
}

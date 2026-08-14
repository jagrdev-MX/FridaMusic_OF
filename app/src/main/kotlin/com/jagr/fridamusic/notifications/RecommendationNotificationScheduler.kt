package com.jagr.fridamusic.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jagr.fridamusic.BuildConfig
import com.jagr.fridamusic.constants.MusicRecommendationNotificationsKey
import com.jagr.fridamusic.utils.dataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.ZonedDateTime
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
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<RecommendationNotificationWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun initialDelayMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
        var nextEvaluation = now.toLocalDate().atTime(DEFAULT_EVALUATION_HOUR, 0).atZone(now.zone)
        if (!nextEvaluation.isAfter(now)) nextEvaluation = nextEvaluation.plusDays(1)
        return Duration.between(now, nextEvaluation).toMillis()
    }

    private fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    internal fun enqueueInternalTestNotification(type: NotificationCandidateType): Boolean {
        if (!isInternalTestBuild()) return false

        val request = OneTimeWorkRequestBuilder<RecommendationNotificationWorker>()
            .setInputData(
                workDataOf(
                    RecommendationNotificationWorker.FORCE_TEST_INPUT_KEY to true,
                    RecommendationNotificationWorker.FORCE_TEST_TYPE_INPUT_KEY to type.name,
                ),
            )
            .addTag(INTERNAL_TEST_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            INTERNAL_TEST_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    private fun isInternalTestBuild(): Boolean =
        BuildConfig.DEBUG &&
            BuildConfig.FLAVOR_abi == "universal" &&
            BuildConfig.FLAVOR_variant == "gms"

    companion object {
        const val WORK_NAME = "music_recommendation_notifications"
        const val WORK_TAG = "music_recommendation"
        const val REPEAT_INTERVAL_HOURS = 24L
        const val DEFAULT_EVALUATION_HOUR = 10
        private const val INTERNAL_TEST_WORK_NAME = "music_recommendation_notification_internal_test"
        private const val INTERNAL_TEST_WORK_TAG = "music_recommendation_internal_test"
    }
}

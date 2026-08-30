package com.jagr.fridamusic.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
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
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        NotificationSlot.entries.forEach(::scheduleNext)
    }

    internal fun scheduleNext(
        slot: NotificationSlot,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<RecommendationNotificationWorker>()
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis(slot, now), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(RecommendationNotificationWorker.SLOT_INPUT_KEY to slot.name),
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            slot.workName(),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun initialDelayMillis(slot: NotificationSlot, now: ZonedDateTime): Long {
        var nextEvaluation = now.toLocalDate().atTime(slot.startHour, 0).atZone(now.zone)
        if (!nextEvaluation.isAfter(now)) nextEvaluation = nextEvaluation.plusDays(1)
        return Duration.between(now, nextEvaluation).toMillis()
    }

    private fun cancel() {
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        NotificationSlot.entries.forEach { slot -> workManager.cancelUniqueWork(slot.workName()) }
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
        private const val LEGACY_WORK_NAME = "music_recommendation_notifications"
        const val WORK_TAG = "music_recommendation"
        private const val INTERNAL_TEST_WORK_NAME = "music_recommendation_notification_internal_test"
        private const val INTERNAL_TEST_WORK_TAG = "music_recommendation_internal_test"
    }
}

private fun NotificationSlot.workName(): String = when (this) {
    NotificationSlot.MORNING -> "frida_notification_morning"
    NotificationSlot.AFTERNOON -> "frida_notification_afternoon"
    NotificationSlot.EVENING -> "frida_notification_evening"
}

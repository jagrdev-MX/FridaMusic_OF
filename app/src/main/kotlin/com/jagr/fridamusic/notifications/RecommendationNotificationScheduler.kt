package com.jagr.fridamusic.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import android.os.SystemClock
import com.jagr.fridamusic.constants.NewReleaseNotificationsKey
import com.jagr.fridamusic.constants.FridaReminderNotificationsKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
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
            .map { preferences -> Triple(preferences[MusicRecommendationNotificationsKey] ?: false, preferences[NewReleaseNotificationsKey] ?: true, preferences[FridaReminderNotificationsKey] ?: true) }
            .distinctUntilChanged()
            .collect { reconcile() }
    }

    suspend fun reconcile() = schedulingMutex.withLock {
        val state = applicationContext.getSharedPreferences("notification_schedule_v2", Context.MODE_PRIVATE)
        val now = ZonedDateTime.now()
        val clockBase = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val changed = state.getString("zone", null) != now.zone.id ||
            kotlin.math.abs(state.getLong("clock", 0L) - clockBase) > 60_000L
        val enabled = applicationContext.dataStore.data.first()[MusicRecommendationNotificationsKey] == true
        if (!state.getBoolean("migrated", false)) {
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
            NotificationSlot.entries.forEach { workManager.cancelUniqueWork(it.workName()) }
        }
        NotificationSlot.entries.forEach { slot ->
            if (!enabled) workManager.cancelUniqueWork(slot.workName() + "_v2")
            else enqueue(slot, now, if (changed) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
        }
        val releases = applicationContext.dataStore.data.first()[NewReleaseNotificationsKey] != false
        if (enabled && releases) {
            workManager.enqueueUniquePeriodicWork("frida_release_refresh_v1", androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                androidx.work.PeriodicWorkRequestBuilder<NotificationReleaseRefreshWorker>(24, TimeUnit.HOURS)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build())
        } else workManager.cancelUniqueWork("frida_release_refresh_v1")
        state.edit().putBoolean("migrated", true).putString("zone", now.zone.id)
            .putLong("clock", clockBase).apply()
    }

    internal suspend fun scheduleNext(slot: NotificationSlot, retryAt: ZonedDateTime? = null) = schedulingMutex.withLock {
        if (applicationContext.dataStore.data.first()[MusicRecommendationNotificationsKey] != true) return@withLock
        val now = ZonedDateTime.now()
        enqueue(slot, retryAt ?: now.toLocalDate().plusDays(1).atTime(slot.startHour, 0).atZone(now.zone),
            ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(slot: NotificationSlot, earliest: ZonedDateTime, policy: ExistingWorkPolicy) {
        val target = NotificationTiming.next(slot, earliest)
        val request = OneTimeWorkRequestBuilder<RecommendationNotificationWorker>()
            .setInitialDelay(Duration.between(ZonedDateTime.now(), target).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(RecommendationNotificationWorker.SLOT_INPUT_KEY to slot.name))
            .addTag(WORK_TAG).build()
        workManager.enqueueUniqueWork(slot.workName() + "_v2", policy, request)
        Timber.tag("RecommendationScheduler").d("scheduled slot=%s next_evaluation=%s", slot, target)
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
        private val schedulingMutex = Mutex()
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

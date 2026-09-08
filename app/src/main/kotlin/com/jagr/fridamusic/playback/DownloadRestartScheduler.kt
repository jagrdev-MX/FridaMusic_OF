package com.jagr.fridamusic.playback

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.jagr.fridamusic.utils.ForegroundServiceLaunch

/** Same persisted JobScheduler requirements as Media3's PlatformScheduler, with a guarded start. */
internal class DownloadRestartScheduler(private val context: Context, private val jobId: Int) : Scheduler {
    private val scheduler = context.getSystemService(JobScheduler::class.java)

    override fun schedule(requirements: Requirements, servicePackage: String, serviceAction: String): Boolean {
        val builder = JobInfo.Builder(jobId, ComponentName(context, DownloadRestartJobService::class.java))
            .setRequiresDeviceIdle(requirements.isIdleRequired)
            .setRequiresCharging(requirements.isChargingRequired)
            .setRequiresStorageNotLow(requirements.isStorageNotLowRequired)
            .setPersisted(true)
            .setExtras(PersistableBundle().apply {
                putInt("requirements", requirements.requirements)
                putString("service_action", serviceAction)
            })
        if (requirements.isUnmeteredNetworkRequired) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
        } else if (requirements.isNetworkRequired) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        }
        return scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
    }

    override fun cancel(): Boolean {
        scheduler.cancel(jobId)
        return true
    }

    override fun getSupportedRequirements(requirements: Requirements): Requirements =
        requirements.filterRequirements(Requirements.NETWORK or Requirements.NETWORK_UNMETERED or
            Requirements.DEVICE_IDLE or Requirements.DEVICE_CHARGING or Requirements.DEVICE_STORAGE_NOT_LOW)
}

class DownloadRestartJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val requirements = Requirements(params.extras.getInt("requirements"))
        val started = requirements.getNotMetRequirements(this) == 0 &&
            ForegroundServiceLaunch.start(this,
                Intent(this, ExoDownloadService::class.java).setAction(params.extras.getString("service_action")),
                "download_scheduler")
        if (!started) {
            // Native JobScheduler backoff, no polling; Media3's persisted downloads remain queued.
            // Acknowledge the running job before completing it with a reschedule request.
            Handler(Looper.getMainLooper()).post { jobFinished(params, true) }
            return true
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

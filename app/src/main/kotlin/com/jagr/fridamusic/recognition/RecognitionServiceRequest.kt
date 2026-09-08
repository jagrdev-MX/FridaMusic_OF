package com.jagr.fridamusic.recognition

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.ResultReceiver
import com.jagr.fridamusic.utils.ForegroundServiceLaunch
import com.jagr.fridamusic.widget.MusicRecognizerWidgetService

/** A fallback keeps the same request and destination; only a successful promotion consumes it. */
object RecognitionServiceRequest {
    const val TOKEN = "recognition_request"
    const val WIDGET = "recognition_widget"
    const val REPLY = "recognition_reply"
    const val ACCEPTED = 1
    const val REJECTED = 2
    const val PENDING_WIDGET_REQUEST = "pending_activity_request"
    private const val PREFS = "recognition_service_requests"

    @Synchronized
    fun token(context: Context, intent: Intent): String = intent.getStringExtra(TOKEN) ?: run {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getLong("next_request", 0L) + 1
        prefs.edit().putLong("next_request", next).apply()
        next.toString().also { intent.putExtra(TOKEN, it) }
    }

    @Synchronized
    fun consume(context: Context, intent: Intent): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val request = token(context, intent).toLong()
        // A watermark also rejects old Activity replays without retaining an unbounded token set.
        if (request <= prefs.getLong("last_started", 0L)) return false
        prefs.edit().putLong("last_started", request).apply()
        return true
    }

    fun serviceIntent(context: Context, request: Intent): Intent = Intent(
        context,
        if (request.getBooleanExtra(WIDGET, false)) MusicRecognizerWidgetService::class.java
        else RecognitionForegroundService::class.java,
    ).apply {
        putExtra(TOKEN, token(context, request))
        putExtra(WIDGET, request.getBooleanExtra(WIDGET, false))
        action = MusicRecognizerWidgetService.ACTION_START_RECOGNITION
    }

    fun activityIntent(context: Context, request: Intent): Intent =
        Intent(context, RecognitionLaunchActivity::class.java).apply {
            putExtra(TOKEN, token(context, request))
            putExtra(WIDGET, request.getBooleanExtra(WIDGET, false))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun pendingActivity(context: Context, request: Intent): PendingIntent = PendingIntent.getActivity(
        context, 24, activityIntent(context, request),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun openActivity(context: Context, request: Intent) {
        ForegroundServiceLaunch.log("recognition_widget", "activity_fallback")
        // The recovery PendingIntent remains available even if the OEM blocks this launch.
        try {
            context.startActivity(activityIntent(context, request))
        } catch (error: SecurityException) {
            ForegroundServiceLaunch.log("recognition_widget", "activity_rejected", error)
        }
    }

    @Suppress("DEPRECATION")
    fun reply(intent: Intent, result: Int): Boolean {
        val receiver = intent.getParcelableExtra<ResultReceiver>(REPLY) ?: return false
        receiver.send(result, null)
        return true
    }
}

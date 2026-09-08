package com.jagr.fridamusic.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.jagr.fridamusic.utils.ForegroundServiceLaunch

/** Media3 1.7.1's artwork callback bypasses MediaSessionService's synchronous catch. */
internal class GuardedMediaNotificationProvider(
    private val delegate: MediaNotification.Provider,
    private val onRejected: (MediaSession, MediaNotification) -> Unit,
) : MediaNotification.Provider by delegate {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification = delegate.createNotification(
        mediaSession, mediaButtonPreferences, actionFactory,
    ) { notification ->
        val update = Runnable {
            // Media3's mainExecutor runs inline on main, so its FGS start is inside this boundary.
            if (!ForegroundServiceLaunch.run("media_notification_artwork") {
                    onNotificationChangedCallback.onNotificationChanged(notification)
                }) {
                onRejected(mediaSession, notification)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update.run() else mainHandler.post(update)
    }
}

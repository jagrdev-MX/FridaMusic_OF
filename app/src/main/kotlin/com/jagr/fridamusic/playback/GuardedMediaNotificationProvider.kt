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
    isSessionActive: (MediaSession) -> Boolean,
    onRejected: (MediaSession, MediaNotification) -> Unit,
) : MediaNotification.Provider by delegate {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var released = false

    @Volatile
    private var isSessionActive: ((MediaSession) -> Boolean)? = isSessionActive

    @Volatile
    private var onRejected: ((MediaSession, MediaNotification) -> Unit)? = onRejected

    private fun canDispatch(mediaSession: MediaSession): Boolean =
        !released && isSessionActive?.invoke(mediaSession) == true

    fun release() {
        released = true
        isSessionActive = null
        onRejected = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val guardedCallback = MediaNotification.Provider.Callback { notification ->
            if (canDispatch(mediaSession)) {
                val update = Runnable {
                    if (!canDispatch(mediaSession)) return@Runnable

                    // Media3's mainExecutor runs inline on main, so its FGS start is inside this boundary.
                    if (!ForegroundServiceLaunch.run("media_notification_artwork") {
                            if (canDispatch(mediaSession)) {
                                onNotificationChangedCallback.onNotificationChanged(notification)
                            }
                        } && canDispatch(mediaSession)) {
                        onRejected?.invoke(mediaSession, notification)
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    update.run()
                } else {
                    mainHandler.post(update)
                }
            }
        }

        return delegate.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            guardedCallback,
        )
    }
}

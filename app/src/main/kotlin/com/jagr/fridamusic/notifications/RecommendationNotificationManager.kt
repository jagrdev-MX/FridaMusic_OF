package com.jagr.fridamusic.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jagr.fridamusic.MainActivity
import com.jagr.fridamusic.R

internal object RecommendationNotificationManager {
    const val HOME_DEEP_LINK_PATTERN = "fridamusic://home"
    const val ALBUM_DEEP_LINK_PATTERN = "fridamusic://album/{albumId}"
    const val ARTIST_DEEP_LINK_PATTERN = "fridamusic://artist/{artistId}"
    const val PLAYLIST_DEEP_LINK_PATTERN = "fridamusic://playlist/{playlistId}"
    const val LOCAL_PLAYLIST_DEEP_LINK_PATTERN = "fridamusic://local-playlist/{playlistId}"

    private const val CHANNEL_ID = "music_recommendations"
    private const val NOTIFICATION_ID = 2_101

    fun createChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.recommendation_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.recommendation_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        createChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        val systemNotificationManager = context.getSystemService(NotificationManager::class.java)
        return systemNotificationManager.getNotificationChannel(CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }

    fun show(
        context: Context,
        candidate: NotificationCandidate,
        message: GeneratedNotificationMessage,
        artwork: Bitmap?,
    ): Boolean {
        if (!canPost(context)) return false

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(candidate.deepLink),
            context,
            MainActivity::class.java,
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            candidate.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (artwork == null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
        } else {
            builder
                .setLargeIcon(artwork)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(artwork)
                        .setSummaryText(message.body),
                )
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        return true
    }
}

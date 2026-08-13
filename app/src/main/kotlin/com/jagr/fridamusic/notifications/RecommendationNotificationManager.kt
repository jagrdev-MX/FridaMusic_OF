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

internal data class RecommendationAlbumCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
)

internal object RecommendationNotificationManager {
    const val ALBUM_DEEP_LINK_PATTERN = "fridamusic://album/{albumId}"

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

    fun show(
        context: Context,
        candidate: RecommendationAlbumCandidate,
        artwork: Bitmap,
    ): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        createChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        val systemNotificationManager = context.getSystemService(NotificationManager::class.java)
        if (systemNotificationManager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }

        val deepLink = Uri.parse("fridamusic://album/${Uri.encode(candidate.id)}")
        val intent = Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            candidate.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val content = if (candidate.artist.isBlank()) {
            candidate.title
        } else {
            context.getString(
                R.string.recommendation_notification_content,
                candidate.title,
                candidate.artist,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setLargeIcon(artwork)
            .setContentTitle(context.getString(R.string.recommendation_notification_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return true
    }
}

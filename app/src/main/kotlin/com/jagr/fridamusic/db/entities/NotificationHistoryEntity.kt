package com.jagr.fridamusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["deliveredAt"]),
        Index(value = ["type"]),
        Index(value = ["readAt"]),
    ],
)
data class NotificationHistoryEntity(
    @PrimaryKey val id: String,
    val candidateId: String,
    val type: String,
    val contentType: String,
    val contentId: String?,
    val title: String,
    val body: String,
    val artworkUrl: String?,
    val deepLink: String?,
    val source: String?,
    val reason: String?,
    val deliveredAt: Long,
    val readAt: Long? = null,
    val dismissedAt: Long? = null,
)

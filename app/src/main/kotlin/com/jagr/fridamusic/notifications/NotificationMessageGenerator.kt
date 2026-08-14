package com.jagr.fridamusic.notifications

import android.content.Context
import com.jagr.fridamusic.R
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal data class GeneratedNotificationMessage(
    val templateId: String,
    val title: String,
    val body: String,
)

internal object NotificationMessageGenerator {
    fun generate(
        context: Context,
        candidate: NotificationCandidate,
        recentTemplateIds: Set<String>,
        now: Long,
        forcedTemplateIndex: Int? = null,
    ): GeneratedNotificationMessage {
        val (titleArray, bodyArray) = resourcesFor(candidate.type)
        val titles = context.resources.getStringArray(titleArray)
        val bodies = context.resources.getStringArray(bodyArray)
        val templates = (0 until minOf(titles.size, bodies.size)).map { index ->
            MessageTemplate(
                id = "${candidate.type.name}:$index",
                title = titles[index],
                body = bodies[index],
            )
        }.toMutableList()

        if (
            candidate.type == NotificationCandidateType.RECOMMENDED_SONG ||
            candidate.type == NotificationCandidateType.DAILY_DISCOVER ||
            candidate.type == NotificationCandidateType.KEEP_LISTENING
        ) {
            val timeIndex = timeOfDayIndex(now)
            templates += MessageTemplate(
                id = "${candidate.type.name}:time:$timeIndex",
                title = context.resources.getStringArray(R.array.notification_time_titles)[timeIndex],
                body = context.resources.getStringArray(R.array.notification_time_bodies)[timeIndex],
            )
        }

        val available = templates.filterNot { it.id in recentTemplateIds }.ifEmpty { templates }
        val index = forcedTemplateIndex?.let { value ->
            Math.floorMod(value, available.size)
        } ?: Math.floorMod(candidate.id.hashCode() + dayOfYear(now), available.size)
        val selected = available[index]
        val artist = candidate.artistName.ifBlank { context.getString(R.string.app_name) }
        return GeneratedNotificationMessage(
            templateId = selected.id,
            title = selected.title.format(candidate.title, artist),
            body = selected.body.format(candidate.title, artist),
        )
    }

    private fun resourcesFor(type: NotificationCandidateType): Pair<Int, Int> = when (type) {
        NotificationCandidateType.NEW_RELEASE ->
            R.array.notification_release_titles to R.array.notification_release_bodies
        NotificationCandidateType.RECOMMENDED_SONG ->
            R.array.notification_recommendation_titles to R.array.notification_recommendation_bodies
        NotificationCandidateType.DAILY_DISCOVER ->
            R.array.notification_discover_titles to R.array.notification_discover_bodies
        NotificationCandidateType.FORGOTTEN_FAVORITE ->
            R.array.notification_forgotten_titles to R.array.notification_forgotten_bodies
        NotificationCandidateType.KEEP_LISTENING ->
            R.array.notification_keep_listening_titles to R.array.notification_keep_listening_bodies
        NotificationCandidateType.ALBUM ->
            R.array.notification_album_titles to R.array.notification_album_bodies
        NotificationCandidateType.ARTIST ->
            R.array.notification_artist_titles to R.array.notification_artist_bodies
        NotificationCandidateType.PLAYLIST ->
            R.array.notification_playlist_titles to R.array.notification_playlist_bodies
        NotificationCandidateType.RETENTION ->
            R.array.notification_retention_titles to R.array.notification_retention_bodies
    }

    private fun String.format(contentTitle: String, artist: String): String =
        String.format(Locale.getDefault(), this, contentTitle, artist)

    private fun timeOfDayIndex(now: Long): Int = when (
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    ) {
        in 7..11 -> 0
        in 12..17 -> 1
        in 18..21 -> 2
        else -> 3
    }

    private fun dayOfYear(now: Long): Int =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).dayOfYear

    private data class MessageTemplate(
        val id: String,
        val title: String,
        val body: String,
    )
}

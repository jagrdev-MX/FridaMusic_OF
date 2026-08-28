package com.jagr.fridamusic.notifications

import android.net.Uri
import com.jagr.fridamusic.constants.AlbumSortType
import com.jagr.fridamusic.constants.ArtistSortType
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Song
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Year
import java.util.concurrent.TimeUnit

internal class NotificationCandidateProvider(
    private val database: MusicDatabase,
) {
    suspend fun getCandidates(now: Long): List<NotificationCandidate> {
        val relatedUnseenPool = database
            .notificationUnplayedRecommendations(limit = UNSEEN_QUERY_LIMIT)
            .first()
            .rotateForDay(now)
        val cachedUnseenPool = database
            .notificationUnplayedCachedSongs(limit = CACHED_UNSEEN_QUERY_LIMIT)
            .first()
            .rotateForDay(now, salt = DAILY_DISCOVER_ROTATION_SALT)
        val unseenPool = (relatedUnseenPool + cachedUnseenPool).distinctBy { song -> song.id }
        val artistArtworkFallbacks = buildMap {
            unseenPool.forEach { song ->
                val artworkUrl = song.thumbnailUrl ?: return@forEach
                song.artists.forEach { artist -> putIfAbsent(artist.id, artworkUrl) }
            }
        }
        val recommendations = unseenPool.take(TYPE_LIMIT)
        val recommendationIds = recommendations.mapTo(hashSetOf()) { song -> song.id }
        val dailyDiscover = cachedUnseenPool
            .filterNot { song -> song.id in recommendationIds }
            .ifEmpty { cachedUnseenPool }
            .take(TYPE_LIMIT)
        val releases = database.notificationNewReleaseAlbums(
            minimumYear = Year.now().value,
            cachedAfter = LocalDateTime.now().minusDays(RELEASE_CACHE_WINDOW_DAYS),
            familiarSince = now - ARTIST_FAMILIARITY_MILLIS,
            limit = TYPE_LIMIT,
        ).first()
        val strictForgottenFavorites = database.notificationForgottenFavorites(
            recentCutoff = now - FORGOTTEN_RECENT_WINDOW_MILLIS,
            limit = QUERY_LIMIT,
        ).first()
        val olderFavorites = database.notificationLeastRecentFavorites(
            staleBefore = now - FORGOTTEN_FALLBACK_WINDOW_MILLIS,
            limit = QUERY_LIMIT,
        ).first()
        val forgottenFavorites = (strictForgottenFavorites + olderFavorites)
            .distinctBy { song -> song.id }
            .take(TYPE_LIMIT)
        val recentlyPlayedSongs = database.notificationRecentlyPlayedSongs(
            limit = RECENT_SONG_QUERY_LIMIT,
        ).first()
        val recentlyPlayedIds = recentlyPlayedSongs.mapTo(hashSetOf()) { song -> song.id }
        val favoriteAlbums = database
            .albumsLiked(AlbumSortType.CREATE_DATE, descending = true)
            .first()
            .filterNot { album -> album.album.isLocal }
            .take(TYPE_LIMIT)
        val discoveryAlbums = database.notificationDiscoveryAlbums(limit = QUERY_LIMIT).first()
        val cachedAlbums = database.notificationUnplayedCachedAlbums(limit = ALBUM_QUERY_LIMIT).first()
        val albumDiscoveryPool = (discoveryAlbums + cachedAlbums)
            .distinctBy { album -> album.id }
            .rotateForDay(now, salt = ALBUM_ROTATION_SALT)
        val albums = (favoriteAlbums + albumDiscoveryPool)
            .distinctBy { album -> album.id }
            .take(TYPE_LIMIT)
        val followedArtists = database
            .artistsBookmarked(ArtistSortType.CREATE_DATE, descending = true)
            .first()
            .filterNot { artist -> artist.artist.isLocal }
            .take(TYPE_LIMIT)
        val artists = (
            followedArtists + database.notificationDiscoveryArtists(limit = QUERY_LIMIT).first()
        )
            .distinctBy { artist -> artist.id }
            .take(TYPE_LIMIT)
        val playlists = database.notificationDiscoveryPlaylists(limit = QUERY_LIMIT).first()

        val candidates = buildList {
            releases.mapNotNullTo(this) { album ->
                if (album.id.isBlank() || album.title.isBlank()) return@mapNotNullTo null
                val isFromFollowedArtist = album.artists.any { artist -> artist.bookmarkedAt != null }
                NotificationCandidate(
                    id = "new_release:${album.id}",
                    type = NotificationCandidateType.NEW_RELEASE,
                    priority = NotificationPolicy.Priority.NEW_RELEASE,
                    title = album.title,
                    contentId = album.id,
                    contentType = NotificationContentType.ALBUM,
                    artistName = album.artists.joinToString(", ") { artist -> artist.name },
                    artworkUrl = album.thumbnailUrl,
                    deepLink = deepLink("album", album.id),
                    source = if (isFromFollowedArtist) {
                        "followed_artist_current_year_release"
                    } else {
                        "local_current_year_unheard_release"
                    },
                    reason = if (isFromFollowedArtist) {
                        "Current-year unheard album from a followed artist, recently cached"
                    } else {
                        "Current-year unheard album from a familiar artist, recently cached"
                    },
                    timestamp = album.album.lastUpdateTime
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                )
            }
            recommendations.mapNotNullTo(this) { song ->
                song.toSongCandidate(
                    type = NotificationCandidateType.RECOMMENDED_SONG,
                    priority = NotificationPolicy.Priority.RECOMMENDATION,
                    source = "cached_unplayed_recommendations",
                    reason = "Unplayed cached recommendation related to listening history",
                    timestamp = now,
                )
            }
            dailyDiscover.mapNotNullTo(this) { song ->
                song.toSongCandidate(
                    type = NotificationCandidateType.DAILY_DISCOVER,
                    priority = NotificationPolicy.Priority.DAILY_DISCOVER,
                    source = "cached_unplayed_daily_discover",
                    reason = "Unplayed discovery candidate rotated for the local day",
                    timestamp = now,
                )
            }
            forgottenFavorites.mapNotNullTo(this) { song ->
                song.toSongCandidate(
                    type = NotificationCandidateType.FORGOTTEN_FAVORITE,
                    priority = NotificationPolicy.Priority.FORGOTTEN_FAVORITE,
                    source = "local_forgotten_favorites",
                    reason = "Previously played often but not played recently",
                    timestamp = now,
                )
            }
            recentlyPlayedSongs.mapNotNullTo(this) { song ->
                song.toSongCandidate(
                    type = NotificationCandidateType.KEEP_LISTENING,
                    priority = NotificationPolicy.Priority.KEEP_LISTENING,
                    source = "local_recent_history",
                    reason = "Recently played content",
                    timestamp = now,
                )
            }
            albums.mapNotNullTo(this) { album ->
                if (album.album.isLocal || album.id.isBlank() || album.title.isBlank()) return@mapNotNullTo null
                val isFavorite = album.album.bookmarkedAt != null
                NotificationCandidate(
                    id = "album:${album.id}",
                    type = NotificationCandidateType.ALBUM,
                    priority = NotificationPolicy.Priority.ALBUM,
                    title = album.title,
                    contentId = album.id,
                    contentType = NotificationContentType.ALBUM,
                    artistName = album.artists.joinToString(", ") { it.name },
                    artworkUrl = album.thumbnailUrl,
                    deepLink = deepLink("album", album.id),
                    source = if (isFavorite) "liked_album_library" else "cached_unheard_related_albums",
                    reason = if (isFavorite) {
                        "Album explicitly saved in the local library"
                    } else {
                        "Unheard album connected to cached recommendations"
                    },
                    timestamp = now,
                )
            }
            artists.mapNotNullTo(this) { artist ->
                if (
                    artist.artist.isLocal ||
                    !artist.artist.isYouTubeArtist ||
                    artist.id.isBlank() ||
                    artist.title.isBlank()
                ) return@mapNotNullTo null
                val isFollowed = artist.artist.bookmarkedAt != null
                NotificationCandidate(
                    id = "artist:${artist.id}",
                    type = NotificationCandidateType.ARTIST,
                    priority = NotificationPolicy.Priority.ARTIST,
                    title = artist.title,
                    contentId = artist.id,
                    contentType = NotificationContentType.ARTIST,
                    artworkUrl = artist.thumbnailUrl ?: artistArtworkFallbacks[artist.id],
                    deepLink = deepLink("artist", artist.id),
                    source = if (isFollowed) "followed_artist_library" else "cached_unheard_related_artists",
                    reason = if (isFollowed) {
                        "Artist explicitly followed in the local library"
                    } else {
                        "Unheard artist connected to cached recommendations"
                    },
                    timestamp = now,
                )
            }
            playlists.mapNotNullTo(this) { playlist ->
                if (playlist.id.isBlank() || playlist.title.isBlank()) {
                    return@mapNotNullTo null
                }
                val remoteId = playlist.playlist.browseId
                val destination = if (remoteId.isNullOrBlank()) {
                    deepLink("local-playlist", playlist.id)
                } else {
                    deepLink("playlist", remoteId)
                }
                NotificationCandidate(
                    id = "playlist:${playlist.id}",
                    type = NotificationCandidateType.PLAYLIST,
                    priority = NotificationPolicy.Priority.PLAYLIST,
                    title = playlist.title,
                    contentId = playlist.id,
                    contentType = NotificationContentType.PLAYLIST,
                    artworkUrl = playlist.thumbnails.firstOrNull(),
                    deepLink = destination,
                    source = "cached_unopened_playlists",
                    reason = "Remote playlist metadata cached by normal discovery flows",
                    timestamp = now,
                )
            }

            (recentlyPlayedSongs + cachedUnseenPool)
                .distinctBy { song -> song.id }
                .take(RETENTION_CANDIDATE_LIMIT)
                .forEach { recentSong ->
                add(
                    NotificationCandidate(
                        id = "retention:${recentSong.id}",
                        type = NotificationCandidateType.RETENTION,
                        priority = NotificationPolicy.Priority.RETENTION,
                        title = recentSong.title.ifBlank { "FridaMusic" },
                        contentId = "home:${recentSong.id}",
                        contentType = NotificationContentType.HOME,
                        artistName = recentSong.artists.joinToString(", ") { it.name },
                        artworkUrl = recentSong.thumbnailUrl,
                        deepLink = "fridamusic://home",
                        source = if (recentSong.id in recentlyPlayedIds) {
                            "local_listening_context"
                        } else {
                            "cached_unplayed_context"
                        },
                        reason = "Friendly contextual reminder",
                        timestamp = now,
                    ),
                )
            }
        }

        return candidates
            .filter(NotificationCandidate::isValid)
            .sortedByDescending(NotificationCandidate::priority)
            .distinctBy { candidate -> "${candidate.type}:${candidate.contentType}:${candidate.contentId}" }
    }

    private fun Song.toSongCandidate(
        type: NotificationCandidateType,
        priority: Int,
        source: String,
        reason: String,
        timestamp: Long,
    ): NotificationCandidate? {
        val albumId = album?.id
        if (song.isLocal || id.isBlank() || title.isBlank() || albumId.isNullOrBlank()) return null
        return NotificationCandidate(
            id = "${type.name.lowercase()}:$id",
            type = type,
            priority = priority,
            title = title,
            contentId = id,
            contentType = NotificationContentType.SONG,
            artistName = artists.joinToString(", ") { it.name },
            artworkUrl = thumbnailUrl,
            deepLink = deepLink("album", albumId),
            source = source,
            reason = reason,
            timestamp = timestamp,
        )
    }

    private fun <T> List<T>.rotateForDay(now: Long, salt: Int = 0): List<T> {
        if (size < 2) return this
        val dayOfYear = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).dayOfYear
        val offset = Math.floorMod(dayOfYear + salt, size)
        return drop(offset) + take(offset)
    }

    private fun deepLink(host: String, id: String): String =
        "fridamusic://$host/${Uri.encode(id)}"

    private companion object {
        const val QUERY_LIMIT = 20
        const val TYPE_LIMIT = 10
        const val UNSEEN_QUERY_LIMIT = 40
        const val CACHED_UNSEEN_QUERY_LIMIT = 80
        const val RECENT_SONG_QUERY_LIMIT = 40
        const val ALBUM_QUERY_LIMIT = 40
        const val RETENTION_CANDIDATE_LIMIT = 16
        const val DAILY_DISCOVER_ROTATION_SALT = 13
        const val ALBUM_ROTATION_SALT = 7
        const val RELEASE_CACHE_WINDOW_DAYS = 120L
        val FORGOTTEN_RECENT_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(30)
        val FORGOTTEN_FALLBACK_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(7)
        val ARTIST_FAMILIARITY_MILLIS = TimeUnit.DAYS.toMillis(180)
    }
}

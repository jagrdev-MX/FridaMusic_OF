package com.jagr.fridamusic.presentation.components

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.db.entities.SongWithStats
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.localmedia.LocalSongSortMetadata
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.models.toMediaMetadata
import com.jagr.fridamusic.utils.isLocalMediaId
import com.music.innertube.models.SongItem
import java.time.LocalDateTime
import java.time.ZoneId

enum class SongActionSource {
    LOCAL_FILE,
    YOUTUBE,
    SPOTIFY,
    REMOTE,
    UNKNOWN,
}

data class SongNavigationTarget(
    val name: String,
    val id: String? = null,
    val uri: String? = null,
) {
    val isNavigable: Boolean
        get() = !id.isNullOrBlank() || !uri.isNullOrBlank()
}

data class SongAlbumTarget(
    val title: String,
    val id: String? = null,
    val uri: String? = null,
) {
    val isNavigable: Boolean
        get() = !id.isNullOrBlank() || !uri.isNullOrBlank()
}

data class SongRemoteIds(
    val youtubeVideoId: String? = null,
    val spotifyId: String? = null,
    val spotifyUrl: String? = null,
    val otherUrls: Map<String, String> = emptyMap(),
)

data class SongLocalFile(
    val contentUri: String,
    val displayName: String? = null,
    val relativePath: String? = null,
    val absolutePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
) {
    val canShare: Boolean
        get() = contentUri.toUri().scheme.equals("content", ignoreCase = true) ||
            contentUri.toUri().scheme.equals("android.resource", ignoreCase = true)
    val canOpenFolder: Boolean
        get() = !relativePath.isNullOrBlank() || !absolutePath.isNullOrBlank()
    val canModify: Boolean
        get() = contentUri.toUri().scheme.equals("content", ignoreCase = true)
}

data class SongActionMetadata(
    val genres: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val audioFormat: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val fileSizeBytes: Long? = null,
    val dateModifiedEpochSeconds: Long? = null,
    val dateAddedEpochSeconds: Long? = null,
    val dateCreatedEpochSeconds: Long? = null,
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
)

data class SongActionContext(
    val mediaId: String,
    val title: String,
    val artists: List<SongNavigationTarget>,
    val album: SongAlbumTarget? = null,
    val albumArtists: List<SongNavigationTarget> = emptyList(),
    val artworkUrl: String? = null,
    val source: SongActionSource = SongActionSource.UNKNOWN,
    val localFile: SongLocalFile? = null,
    val remoteIds: SongRemoteIds = SongRemoteIds(),
    val metadata: SongActionMetadata = SongActionMetadata(),
    val durationMs: Long? = null,
    val mediaItem: MediaItem,
    val databaseSong: Song? = null,
    val playlistId: String? = null,
    val playlistEntryId: Int? = null,
) {
    fun withLocalMetadata(localMetadata: LocalSongSortMetadata?): SongActionContext {
        if (localMetadata == null || source != SongActionSource.LOCAL_FILE) return this
        val matchingAlbumArtists = splitMetadataValues(localMetadata.albumArtist)
            .map { albumArtistName ->
                artists.firstOrNull { it.name.equals(albumArtistName, ignoreCase = true) }
                    ?: SongNavigationTarget(name = albumArtistName)
            }
            .distinctBy { it.id ?: it.uri ?: it.name }
        return copy(
            album = album?.copy(title = localMetadata.album?.takeIf(String::isNotBlank) ?: album.title)
                ?: localMetadata.album?.takeIf(String::isNotBlank)?.let { SongAlbumTarget(title = it) },
            albumArtists = matchingAlbumArtists.ifEmpty { albumArtists },
            localFile = localFile?.copy(
                displayName = localMetadata.displayName ?: localFile.displayName,
                relativePath = localMetadata.relativePath ?: localFile.relativePath,
                absolutePath = localMetadata.absolutePath ?: localFile.absolutePath,
                mimeType = localMetadata.mimeType ?: localFile.mimeType,
                sizeBytes = localMetadata.sizeBytes ?: localFile.sizeBytes,
            ),
            metadata = metadata.copy(
                genres = splitMetadataValues(localMetadata.genre),
                composers = splitMetadataValues(localMetadata.composer),
                trackNumber = localMetadata.trackNumber,
                audioFormat = localMetadata.mimeType ?: metadata.audioFormat,
                fileSizeBytes = localMetadata.sizeBytes ?: metadata.fileSizeBytes,
                dateModifiedEpochSeconds = localMetadata.dateModifiedSeconds,
                dateAddedEpochSeconds = localMetadata.dateAddedSeconds,
                releaseYear = localMetadata.year ?: metadata.releaseYear,
            ),
            durationMs = localMetadata.durationMs?.takeIf { it >= 0L } ?: durationMs,
        )
    }

    fun toMediaMetadata(): MediaMetadata = mediaItem.metadata ?: MediaMetadata(
        id = mediaId,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
        duration = durationMs?.div(1_000L)?.toInt() ?: -1,
        thumbnailUrl = artworkUrl,
        album = album?.id?.let { albumId -> MediaMetadata.Album(albumId, album.title) },
    )
}

enum class SongActionCapability {
    PLAY_NEXT,
    ADD_TO_QUEUE,
    ADD_TO_PLAYLIST,
    PIN,
    GO_TO_ALBUM,
    GO_TO_ARTIST,
    GO_TO_ALBUM_ARTIST,
    OPEN_FOLDER,
    EDIT_METADATA,
    EDIT_LYRICS,
    BLACKLIST,
    DETAILS,
    SHARE_LINK,
    SHARE_FILE,
    SHARE_NOW_PLAYING_CARD,
    REMOVE_FROM_QUEUE,
    DELETE_LOCAL_FILE,
}

data class SongActionAvailability(
    val playerAvailable: Boolean,
    val playlistAvailable: Boolean,
    val pinAvailable: Boolean,
    val metadataEditorAvailable: Boolean,
    val lyricsEditorAvailable: Boolean,
    val blacklistAvailable: Boolean,
    val queueRemovalAvailable: Boolean,
    val deleteAvailable: Boolean,
)

fun resolveSongActionCapabilities(
    context: SongActionContext,
    availability: SongActionAvailability,
): Set<SongActionCapability> = buildSet {
    if (availability.playerAvailable) {
        add(SongActionCapability.PLAY_NEXT)
        add(SongActionCapability.ADD_TO_QUEUE)
    }
    if (availability.playlistAvailable) add(SongActionCapability.ADD_TO_PLAYLIST)
    if (availability.pinAvailable) add(SongActionCapability.PIN)
    if (context.album?.isNavigable == true) add(SongActionCapability.GO_TO_ALBUM)
    if (context.artists.any(SongNavigationTarget::isNavigable)) add(SongActionCapability.GO_TO_ARTIST)
    if (context.albumArtists.any(SongNavigationTarget::isNavigable)) {
        add(SongActionCapability.GO_TO_ALBUM_ARTIST)
    }
    if (context.localFile?.canOpenFolder == true) add(SongActionCapability.OPEN_FOLDER)
    if (context.localFile?.canModify == true && availability.metadataEditorAvailable) {
        add(SongActionCapability.EDIT_METADATA)
    }
    if (availability.lyricsEditorAvailable) add(SongActionCapability.EDIT_LYRICS)
    if (availability.blacklistAvailable) add(SongActionCapability.BLACKLIST)
    add(SongActionCapability.DETAILS)
    if (SongShareLinkResolver.resolveSongLinks(context).isNotEmpty()) add(SongActionCapability.SHARE_LINK)
    if (context.localFile?.canShare == true) add(SongActionCapability.SHARE_FILE)
    add(SongActionCapability.SHARE_NOW_PLAYING_CARD)
    if (availability.queueRemovalAvailable) add(SongActionCapability.REMOVE_FROM_QUEUE)
    if (context.localFile?.canModify == true && availability.deleteAvailable) {
        add(SongActionCapability.DELETE_LOCAL_FILE)
    }
}

data class SongShareLink(
    val label: String,
    val url: String,
)

object SongShareLinkResolver {
    private val youtubeVideoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val spotifyTrackId = Regex("^[A-Za-z0-9]{22}$")

    fun resolveSongLinks(context: SongActionContext): List<SongShareLink> = buildList {
        context.remoteIds.youtubeVideoId
            ?.takeIf { youtubeVideoId.matches(it) }
            ?.let { add(SongShareLink("YouTube Music", "https://music.youtube.com/watch?v=$it")) }
        context.remoteIds.spotifyUrl
            ?.takeIf { it.startsWith("https://open.spotify.com/track/") }
            ?.let { add(SongShareLink("Spotify", it)) }
        context.remoteIds.spotifyId
            ?.takeIf { spotifyTrackId.matches(it) }
            ?.let { add(SongShareLink("Spotify", "https://open.spotify.com/track/$it")) }
        context.remoteIds.otherUrls.forEach { (label, url) ->
            if (url.startsWith("https://") || url.startsWith("http://")) add(SongShareLink(label, url))
        }
    }.distinctBy(SongShareLink::url)
}

object FridaAppLinks {
    const val DEEP_LINK_HOME = "fridamusic://home"
    const val WEBSITE_URL = "https://frida-music-of.vercel.app/"
    const val playStoreUrl = "https://play.google.com/store/apps/details?id=com.jagr.fridamusic"

    // Promotional image QRs always invite the recipient to install FridaMusic.
    const val appCtaUrl = playStoreUrl
}

fun Song.toSongActionContext(
    localMetadata: LocalSongSortMetadata? = null,
    albumArtists: List<SongNavigationTarget> = emptyList(),
    playlistId: String? = null,
    playlistEntryId: Int? = null,
): SongActionContext {
    val item = toMediaItem()
    val isPhysicalLocal = song.isLocal && song.id.isLocalMediaId()
    val format = format
    val base = SongActionContext(
        mediaId = song.id,
        title = song.title,
        artists = artists.map { SongNavigationTarget(name = it.name, id = it.id) },
        album = (album?.id ?: song.albumId)?.takeIf(String::isNotBlank)?.let { albumId ->
            SongAlbumTarget(
                title = album?.title ?: song.albumName.orEmpty(),
                id = albumId,
            )
        },
        albumArtists = albumArtists.filter(SongNavigationTarget::isNavigable),
        artworkUrl = thumbnailUrl,
        source = when {
            isPhysicalLocal -> SongActionSource.LOCAL_FILE
            isYouTubeVideoId(song.id) -> SongActionSource.YOUTUBE
            else -> SongActionSource.REMOTE
        },
        localFile = if (isPhysicalLocal) {
            SongLocalFile(
                contentUri = song.id,
                mimeType = format?.mimeType,
                sizeBytes = format?.contentLength,
            )
        } else {
            null
        },
        remoteIds = SongRemoteIds(
            youtubeVideoId = song.id.takeIf(::isYouTubeVideoId),
        ),
        metadata = SongActionMetadata(
            audioFormat = format?.mimeType,
            codec = format?.codecs?.takeIf(String::isNotBlank),
            bitrate = format?.bitrate?.takeIf { it > 0 },
            sampleRate = format?.sampleRate?.takeIf { it > 0 },
            fileSizeBytes = format?.contentLength?.takeIf { it > 0L },
            dateModifiedEpochSeconds = song.dateModified?.toEpochSeconds(),
            dateAddedEpochSeconds = song.date?.toEpochSeconds(),
            releaseYear = song.year,
        ),
        durationMs = song.duration.takeIf { it >= 0 }?.times(1_000L),
        mediaItem = item,
        databaseSong = this,
        playlistId = playlistId,
        playlistEntryId = playlistEntryId,
    )
    return base.withLocalMetadata(localMetadata)
}

fun SongItem.toSongActionContext(playlistId: String? = null): SongActionContext {
    val item = toMediaItem()
    return SongActionContext(
        mediaId = id,
        title = title,
        artists = artists.map { SongNavigationTarget(name = it.name, id = it.id) },
        album = album?.let { SongAlbumTarget(title = it.name, id = it.id) },
        artworkUrl = thumbnail,
        source = SongActionSource.YOUTUBE,
        remoteIds = SongRemoteIds(youtubeVideoId = id.takeIf(::isYouTubeVideoId)),
        durationMs = duration?.takeIf { it >= 0 }?.times(1_000L),
        mediaItem = item,
        playlistId = playlistId,
    )
}

fun MediaItem.toSongActionContext(): SongActionContext {
    val tagged = metadata
    val platform = mediaMetadata
    val isPhysicalLocal = mediaId.isLocalMediaId()
    return SongActionContext(
        mediaId = mediaId,
        title = tagged?.title
            ?: platform.displayTitle?.toString()
            ?: platform.title?.toString()
            ?: mediaId,
        artists = tagged?.artists?.map { SongNavigationTarget(name = it.name, id = it.id) }
            ?: platform.artist?.toString()?.takeIf(String::isNotBlank)?.let {
                listOf(SongNavigationTarget(name = it))
            }.orEmpty(),
        album = tagged?.album?.let { SongAlbumTarget(title = it.title, id = it.id) }
            ?: platform.albumTitle?.toString()?.takeIf(String::isNotBlank)?.let { SongAlbumTarget(it) },
        artworkUrl = tagged?.thumbnailUrl ?: platform.artworkUri?.toString(),
        source = when {
            isPhysicalLocal -> SongActionSource.LOCAL_FILE
            isYouTubeVideoId(mediaId) -> SongActionSource.YOUTUBE
            else -> SongActionSource.REMOTE
        },
        localFile = mediaId.takeIf { isPhysicalLocal }?.let { SongLocalFile(contentUri = it) },
        remoteIds = SongRemoteIds(youtubeVideoId = mediaId.takeIf(::isYouTubeVideoId)),
        durationMs = tagged?.duration?.takeIf { it >= 0 }?.times(1_000L)
            ?: platform.extras?.getInt("duration_seconds", -1)?.takeIf { it >= 0 }?.times(1_000L),
        mediaItem = this,
    )
}

fun SongWithStats.toSongActionContext(): SongActionContext {
    val metadata = MediaMetadata(
        id = id,
        title = title,
        artists = artistName?.takeIf(String::isNotBlank)?.let {
            listOf(MediaMetadata.Artist(id = null, name = it))
        }.orEmpty(),
        duration = -1,
        thumbnailUrl = thumbnailUrl,
    )
    return metadata.toMediaItem().toSongActionContext()
}

private fun splitMetadataValues(raw: String?): List<String> = raw
    ?.split(Regex("[;/]"))
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?.distinct()
    .orEmpty()

private fun isYouTubeVideoId(value: String): Boolean = Regex("^[A-Za-z0-9_-]{11}$").matches(value)

private fun LocalDateTime.toEpochSeconds(): Long = atZone(ZoneId.systemDefault()).toEpochSecond()

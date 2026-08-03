

package com.jagr.fridamusic.localmedia

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.AlbumArtistMap
import com.jagr.fridamusic.db.entities.AlbumEntity
import com.jagr.fridamusic.db.entities.ArtistEntity
import com.jagr.fridamusic.db.entities.FormatEntity
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.db.entities.SongAlbumMap
import com.jagr.fridamusic.db.entities.SongArtistMap
import com.jagr.fridamusic.db.entities.SongEntity
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LocalSongScanSummary(
    val scannedSongs: Int,
    val removedSongs: Int,
)

data class LocalSongSortMetadata(
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val composer: String? = null,
    val dateModifiedSeconds: Long? = null,
    val dateAddedSeconds: Long? = null,
)

@Singleton
class LocalSongScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val audioFilter: LocalAudioFilter,
) {
    private val scanMutex = Mutex()
    private var lastAppliedConfig: LocalSongScanConfig? = null
    private var lastScanSummary: LocalSongScanSummary? = null

    suspend fun scanDevice(
        scanConfig: LocalSongScanConfig = LocalSongScanConfig(),
        force: Boolean = true,
    ): LocalSongScanSummary = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val sanitizedConfig = scanConfig.sanitized()
            if (!force && sanitizedConfig == lastAppliedConfig) {
                return@withLock lastScanSummary ?: LocalSongScanSummary(0, 0)
            }

            val snapshot = queryTracks(sanitizedConfig)
            var summary = LocalSongScanSummary(0, 0)
            database.withTransaction {
                val existingLocalIds = localSongIds()
                val scannedIds = snapshot.tracks.map(LocalTrackRecord::id)
                val scannedIdSet = scannedIds.toSet()
                val removedIds = existingLocalIds.filterNot(scannedIdSet::contains)

                if (scannedIds.isEmpty()) {
                    clearLocalSongs()
                } else {
                    removedIds.chunked(SqlBatchSize).forEach(::deleteSongsByIds)
                }

                val existingSongs = loadSongs(scannedIds)
                val existingArtists = loadArtists(snapshot.artists.map(LocalArtistRecord::id))
                val existingAlbums = loadAlbums(snapshot.albums.map(LocalAlbumRecord::id))

                snapshot.artists.forEach { artist ->
                    val existingArtist = existingArtists[artist.id]
                    insert(
                        ArtistEntity(
                            id = artist.id,
                            name = artist.name,
                            thumbnailUrl = existingArtist?.thumbnailUrl,
                            channelId = null,
                            lastUpdateTime = existingArtist?.lastUpdateTime ?: LocalDateTime.now(),
                            bookmarkedAt = existingArtist?.bookmarkedAt,
                            isLocal = true,
                        ),
                    )
                }

                snapshot.albums.forEach { album ->
                    val existingAlbum = existingAlbums[album.id]
                    insert(
                        AlbumEntity(
                            id = album.id,
                            playlistId = null,
                            title = album.title,
                            year = album.year ?: existingAlbum?.year,
                            thumbnailUrl = album.thumbnailUrl ?: existingAlbum?.thumbnailUrl?.takeIf {
                                !it.startsWith("content://media/external/audio/media/")
                            },
                            themeColor = existingAlbum?.themeColor,
                            songCount = album.songCount,
                            duration = album.duration,
                            explicit = false,
                            lastUpdateTime = LocalDateTime.now(),
                            bookmarkedAt = existingAlbum?.bookmarkedAt,
                            likedDate = existingAlbum?.likedDate,
                            inLibrary = existingAlbum?.inLibrary,
                            isLocal = true,
                        ),
                    )
                }

                snapshot.albums
                    .map(LocalAlbumRecord::id)
                    .distinct()
                    .chunked(SqlBatchSize)
                    .forEach(::deleteAlbumArtistMapsByAlbumIds)
                snapshot.albums.forEach { album ->
                    album.artistIds.forEachIndexed { index, artistId ->
                        insert(
                            AlbumArtistMap(
                                albumId = album.id,
                                artistId = artistId,
                                order = index,
                            ),
                        )
                    }
                }

                snapshot.tracks.forEach { track ->
                    val existingSong = existingSongs[track.id]?.song
                    insert(
                        SongEntity(
                            id = track.id,
                            title = track.title,
                            duration = track.durationSeconds,
                            thumbnailUrl = track.thumbnailUrl ?: existingSong?.thumbnailUrl?.takeIf {
                                !it.startsWith("content://media/external/audio/media/")
                            },
                            albumId = track.albumId,
                            albumName = track.albumName,
                            explicit = existingSong?.explicit ?: false,
                            year = track.year ?: existingSong?.year,
                            date = track.dateAdded ?: existingSong?.date,
                            dateModified = track.dateModified ?: existingSong?.dateModified,
                            liked = existingSong?.liked ?: false,
                            likedDate = existingSong?.likedDate,
                            totalPlayTime = existingSong?.totalPlayTime ?: 0L,
                            inLibrary = null,
                            dateDownload = existingSong?.dateDownload,
                            isLocal = true,
                        ),
                    )
                    upsert(
                        FormatEntity(
                            id = track.id,
                            itag = -1,
                            mimeType = track.mimeType,
                            codecs = "",
                            bitrate = 0,
                            sampleRate = null,
                            contentLength = track.sizeBytes,
                            loudnessDb = null,
                            perceptualLoudnessDb = null,
                            playbackUrl = null,
                        ),
                    )
                    deleteSongArtistMaps(track.id)
                    track.artists.forEachIndexed { index, artist ->
                        insert(
                            SongArtistMap(
                                songId = track.id,
                                artistId = artist.id,
                                position = index,
                            ),
                        )
                    }
                    deleteSongAlbumMaps(track.id)
                    track.albumId?.let { albumId ->
                        insert(
                            SongAlbumMap(
                                songId = track.id,
                                albumId = albumId,
                                index = 0,
                            ),
                        )
                    }
                }

                pruneLocalAlbums()
                pruneLocalArtists()
                pruneFormats()
                prunePlayCounts()

                summary = LocalSongScanSummary(
                    scannedSongs = snapshot.tracks.size,
                    removedSongs = removedIds.size,
                )
            }

            lastAppliedConfig = sanitizedConfig
            lastScanSummary = summary
            summary
        }
    }

    private suspend fun loadSongs(ids: List<String>): Map<String, Song> =
        ids.chunked(SqlBatchSize)
            .flatMap { chunk -> database.getSongsByIds(chunk) }
            .associateBy { item -> item.song.id }

    private suspend fun loadArtists(ids: List<String>): Map<String, ArtistEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getArtistEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

    private suspend fun loadAlbums(ids: List<String>): Map<String, AlbumEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getAlbumEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

    @Suppress("DEPRECATION")
    suspend fun querySortMetadata(): Map<String, LocalSongSortMetadata> = withContext(Dispatchers.IO) {
        val baseProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val commonAudioProjection = baseProjection + arrayOf(
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.COMPOSER,
        )
        val projections = listOf(commonAudioProjection, baseProjection)

        var metadata: Map<String, LocalSongSortMetadata>? = null
        for (projection in projections) {
            metadata = runCatching { querySortMetadata(projection) }.getOrNull()
            if (metadata != null) break
        }
        val availableMetadata = metadata ?: return@withContext emptyMap()
        val albumArtists = queryOptionalSortText(AlbumArtistColumn)
        val genres = queryOptionalSortText(GenreColumn)
        availableMetadata.mapValues { (songId, value) ->
            value.copy(
                albumArtist = albumArtists[songId],
                genre = genres[songId],
            )
        }
    }

    private fun queryOptionalSortText(column: String): Map<String, String> = runCatching {
        val values = linkedMapOf<String, String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, column),
            buildAvailableMediaSelection(),
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val valueIndex = cursor.getColumnIndex(column)
            while (cursor.moveToNext()) {
                val value = cursor.getStringOrNull(valueIndex).normalizedMetadataValue() ?: continue
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idIndex),
                ).toString()
                values[contentUri] = value
            }
        }
        values
    }.getOrDefault(emptyMap())

    private fun querySortMetadata(projection: Array<String>): Map<String, LocalSongSortMetadata> {
        val metadata = linkedMapOf<String, LocalSongSortMetadata>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            buildAvailableMediaSelection(),
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumArtistIndex = cursor.getColumnIndex(AlbumArtistColumn)
            val genreIndex = cursor.getColumnIndex(GenreColumn)
            val trackIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val composerIndex = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
            val dateModifiedIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddedIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val rawTrackNumber = cursor.getIntOrNull(trackIndex)?.takeIf { it > 0 }
                val trackNumber = rawTrackNumber?.let { value ->
                    (value % 1000).takeIf { it > 0 } ?: value
                }
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId,
                ).toString()
                metadata[contentUri] = LocalSongSortMetadata(
                    title = cursor.getStringOrNull(titleIndex).normalizedMetadataValue(),
                    album = cursor.getStringOrNull(albumIndex).normalizedMetadataValue(),
                    artist = cursor.getStringOrNull(artistIndex).normalizedMetadataValue(),
                    albumArtist = cursor.getStringOrNull(albumArtistIndex).normalizedMetadataValue(),
                    genre = cursor.getStringOrNull(genreIndex).normalizedMetadataValue(),
                    trackNumber = trackNumber,
                    durationMs = cursor.getLongOrNull(durationIndex)?.takeIf { it >= 0L },
                    year = cursor.getIntOrNull(yearIndex)?.takeIf { it > 0 },
                    composer = cursor.getStringOrNull(composerIndex).normalizedMetadataValue(),
                    dateModifiedSeconds = cursor.getLongOrNull(dateModifiedIndex)?.takeIf { it > 0L },
                    dateAddedSeconds = cursor.getLongOrNull(dateAddedIndex)?.takeIf { it > 0L },
                )
            }
        }
        return metadata
    }

    @Suppress("DEPRECATION")
    private fun queryTracks(scanConfig: LocalSongScanConfig): LocalScanSnapshot {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.IS_MUSIC)
            add(MediaStore.Audio.Media.IS_RINGTONE)
            add(MediaStore.Audio.Media.IS_ALARM)
            add(MediaStore.Audio.Media.IS_NOTIFICATION)
            add(MediaStore.Audio.Media.IS_PODCAST)
            add(MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.Audio.Media.IS_AUDIOBOOK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(MediaStore.Audio.Media.IS_RECORDING)
            }
        }.toTypedArray()
        val selection = buildAvailableMediaSelection()

        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownTitle = context.getString(R.string.unknown)
        val tracks = mutableListOf<LocalTrackRecord>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC, ${MediaStore.Audio.Media._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val isMusicIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
            val isRingtoneIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
            val isAlarmIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
            val isNotificationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
            val isPodcastIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_PODCAST)
            val isAudiobookIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK)
            val isRecordingIndex = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                val normalizedFolderPath = resolveNormalizedFolderPath(
                    relativePath = cursor.getStringOrNull(relativePathIndex),
                    absolutePath = cursor.getStringOrNull(dataPathIndex),
                )
                val displayName = cursor.getStringOrNull(displayNameIndex)
                val mimeType = cursor.getStringOrNull(mimeTypeIndex)
                    ?.takeIf(String::isNotBlank)
                    ?: "audio/*"
                if (!SupportedLocalAudio.isSupported(displayName, mimeType)) {
                    continue
                }
                val rawTitle = cursor.getStringOrNull(titleIndex)
                val rawArtist = cursor.getStringOrNull(artistIndex)
                val rawAlbum = cursor.getStringOrNull(albumIndex)
                val durationMs = cursor.getLongOrNull(durationIndex)
                val sizeBytes = cursor.getLongOrNull(sizeIndex)
                val filterResult = audioFilter.evaluate(
                    audio = LocalAudioFile(
                        directoryPath = normalizedFolderPath,
                        displayName = displayName,
                        title = rawTitle,
                        artist = rawArtist,
                        album = rawAlbum,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        isMusic = cursor.getBooleanOrNull(isMusicIndex),
                        isRingtone = cursor.getBooleanOrNull(isRingtoneIndex),
                        isAlarm = cursor.getBooleanOrNull(isAlarmIndex),
                        isNotification = cursor.getBooleanOrNull(isNotificationIndex),
                        isPodcast = cursor.getBooleanOrNull(isPodcastIndex),
                        isAudiobook = cursor.getBooleanOrNull(isAudiobookIndex),
                        isRecording = cursor.getBooleanOrNull(isRecordingIndex),
                    ),
                    config = scanConfig,
                )
                if (filterResult is AudioFilterResult.Excluded) continue

                val artistValue = normalizeArtistName(rawArtist, unknownArtist)
                val splitArtists = splitArtistNames(artistValue).ifEmpty { listOf(unknownArtist) }
                val mediaStoreArtistId = cursor.getLongOrNull(artistIdIndex)
                val artists = splitArtists.mapIndexed { index, name ->
                    LocalArtistRecord(
                        id = buildArtistId(mediaStoreArtistId, name, index, splitArtists.size),
                        name = name,
                    )
                }
                val mediaStoreAlbumId = cursor.getLongOrNull(albumIdIndex)
                val albumName = normalizeAlbumName(rawAlbum)
                val title = normalizeTitle(
                    title = rawTitle,
                    displayName = displayName,
                    fallback = unknownTitle,
                )
                tracks += LocalTrackRecord(
                    id = contentUri.toString(),
                    title = title,
                    artists = artists,
                    albumId = albumName?.let {
                        buildAlbumId(
                            mediaStoreAlbumId = mediaStoreAlbumId,
                            albumName = it,
                            primaryArtistId = artists.firstOrNull()?.id,
                        )
                    },
                    albumName = albumName,
                    durationSeconds = ((durationMs ?: 0L).coerceAtLeast(0L) / 1000L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    year = cursor.getIntOrNull(yearIndex)?.takeIf { it > 0 },
                    dateModified = cursor.getLongOrNull(dateModifiedIndex)
                        ?.takeIf { it > 0L }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) },
                    dateAdded = cursor.getLongOrNull(dateAddedIndex)
                        ?.takeIf { it > 0L }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) },
                    sizeBytes = (sizeBytes ?: 0L).coerceAtLeast(0L),
                    mimeType = mimeType,
                    thumbnailUrl = mediaStoreAlbumId?.takeIf { it > 0 }?.let {
                        ContentUris.withAppendedId(AlbumArtUri, it).toString()
                    },
                )
            }
        }

        val albums = tracks
            .filter { !it.albumId.isNullOrBlank() && !it.albumName.isNullOrBlank() }
            .groupBy { it.albumId!! }
            .map { (albumId, albumTracks) ->
                LocalAlbumRecord(
                    id = albumId,
                    title = albumTracks.first().albumName.orEmpty(),
                    year = albumTracks.mapNotNull(LocalTrackRecord::year).maxOrNull(),
                    thumbnailUrl = albumTracks.mapNotNull(LocalTrackRecord::thumbnailUrl).firstOrNull(),
                    songCount = albumTracks.size,
                    duration = albumTracks.sumOf(LocalTrackRecord::durationSeconds),
                    artistIds = albumTracks.flatMap { track -> track.artists.map(LocalArtistRecord::id) }.distinct(),
                )
            }

        return LocalScanSnapshot(
            tracks = tracks,
            artists = tracks.flatMap(LocalTrackRecord::artists).distinctBy(LocalArtistRecord::id),
            albums = albums,
        )
    }

    @Suppress("DEPRECATION")
    suspend fun queryAudioFolders(): List<LocalAudioFolder> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()
        val folderCounts = linkedMapOf<String, Pair<String, Int>>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            buildAvailableMediaSelection(),
            null,
            null,
        )?.use { cursor ->
            val displayNameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeTypeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val displayName = cursor.getStringOrNull(displayNameIndex)
                val mimeType = cursor.getStringOrNull(mimeTypeIndex)
                if (!SupportedLocalAudio.isSupported(displayName, mimeType)) continue

                val folderPath = resolveNormalizedFolderPath(
                    relativePath = cursor.getStringOrNull(relativePathIndex),
                    absolutePath = cursor.getStringOrNull(dataPathIndex),
                ) ?: continue
                val canonicalPath = LocalSongScanConfig.canonicalFolderEntry(folderPath)
                if (canonicalPath.isEmpty()) continue

                val current = folderCounts[canonicalPath]
                folderCounts[canonicalPath] = (current?.first ?: folderPath) to
                    ((current?.second ?: 0) + 1)
            }
        }

        folderCounts.values
            .map { (path, count) -> LocalAudioFolder(path = path, audioCount = count) }
            .sortedBy { LocalSongScanConfig.canonicalFolderEntry(it.path) }
    }

    private fun buildAvailableMediaSelection(): String? {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
            }
        }.joinToString(" AND ").takeIf(String::isNotEmpty)
    }

    private fun normalizeTitle(title: String?, displayName: String?, fallback: String): String {
        return title?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun normalizeArtistName(rawArtist: String?, fallback: String): String {
        val normalized = rawArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        return normalized ?: fallback
    }

    private fun normalizeAlbumName(rawAlbum: String?): String? {
        return rawAlbum?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
    }

    private fun splitArtistNames(rawArtist: String): List<String> {
        return rawArtist
            .split(ArtistSeparators)
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf(rawArtist) }
    }

    private fun buildArtistId(
        mediaStoreArtistId: Long?,
        artistName: String,
        index: Int,
        totalArtists: Int,
    ): String {
        val stableId = mediaStoreArtistId?.takeIf { it > 0L }
        return if (stableId != null && totalArtists == 1) {
            "LOCAL_ARTIST_$stableId"
        } else {
            "LOCAL_ARTIST_${stableHash("$artistName|$index")}"
        }
    }

    private fun buildAlbumId(
        mediaStoreAlbumId: Long?,
        albumName: String,
        primaryArtistId: String?,
    ): String {
        val stableId = mediaStoreAlbumId?.takeIf { it > 0L }
        return if (stableId != null) {
            "LOCAL_ALBUM_$stableId"
        } else {
            "LOCAL_ALBUM_${stableHash("$albumName|$primaryArtistId")}"
        }
    }

    private fun stableHash(source: String): String {
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
    }

    private fun resolveNormalizedFolderPath(relativePath: String?, absolutePath: String?): String? {
        val relativeFolder = LocalSongScanConfig.normalizeFolderEntry(relativePath.orEmpty())
        if (relativeFolder.isNotEmpty()) {
            return relativeFolder
        }

        val absoluteFolder = absolutePath
            ?.replace('\\', '/')
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            .orEmpty()
        val normalizedAbsoluteFolder = LocalSongScanConfig.normalizeFolderEntry(absoluteFolder)
        return normalizedAbsoluteFolder.takeIf(String::isNotEmpty)
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null
    }

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
    }

    private fun android.database.Cursor.getBooleanOrNull(columnIndex: Int): Boolean? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) != 0 else null
    }

    private fun String?.normalizedMetadataValue(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }

    private data class LocalScanSnapshot(
        val tracks: List<LocalTrackRecord>,
        val artists: List<LocalArtistRecord>,
        val albums: List<LocalAlbumRecord>,
    )

    private data class LocalTrackRecord(
        val id: String,
        val title: String,
        val artists: List<LocalArtistRecord>,
        val albumId: String?,
        val albumName: String?,
        val durationSeconds: Int,
        val year: Int?,
        val dateModified: LocalDateTime?,
        val dateAdded: LocalDateTime?,
        val sizeBytes: Long,
        val mimeType: String,
        val thumbnailUrl: String?,
    )

    private data class LocalArtistRecord(
        val id: String,
        val name: String,
    )

    private data class LocalAlbumRecord(
        val id: String,
        val title: String,
        val year: Int?,
        val thumbnailUrl: String?,
        val songCount: Int,
        val duration: Int,
        val artistIds: List<String>,
    )

    private companion object {
        val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
        val ArtistSeparators = Regex("[,;/&]")
        const val AlbumArtistColumn = "album_artist"
        const val GenreColumn = "genre"
        const val SqlBatchSize = 900
    }
}

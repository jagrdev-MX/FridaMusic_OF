package com.jagr.fridamusic.localmedia

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAudioFilterTest {
    private val filter = LocalAudioFilter()

    @Test
    fun defaultConfigMatchesLocalLibraryRecommendations() {
        val config = LocalSongScanConfig()

        assertEquals(true, config.hideShortAudio)
        assertEquals(60, config.minimumDurationSeconds)
        assertEquals(true, config.hideSmallFiles)
        assertEquals(500, config.minimumSizeKb)
        assertEquals(true, config.hideRecordings)
        assertEquals(true, config.hideSystemSounds)
        assertEquals(true, config.showFilesWithoutMetadata)
        assertEquals(emptySet<String>(), config.excludedFolders)
    }

    @Test
    fun defaultConfigExcludesKnownVoiceNoteFolderCaseInsensitively() {
        val result = filter.evaluate(
            audio = audio(
                directoryPath =
                    "Android/media/com.whatsapp/WhatsApp/Media/WHATSAPP VOICE NOTES/2026-07",
            ),
            config = LocalSongScanConfig(),
        )

        assertExcluded(LocalAudioExclusionReason.RECORDING_OR_VOICE_NOTE, result)
    }

    @Test
    fun recordingFoldersAreAccentNormalized() {
        val result = filter.evaluate(
            audio = audio(directoryPath = "Music/Grabaciónes"),
            config = LocalSongScanConfig(),
        )

        assertExcluded(LocalAudioExclusionReason.RECORDING_OR_VOICE_NOTE, result)
    }

    @Test
    fun songTitleWordsDoNotTriggerRecordingFilter() {
        val result = filter.evaluate(
            audio = audio(
                directoryPath = "Music/Albums",
                title = "Voice Record Live",
                artist = null,
                album = null,
            ),
            config = LocalSongScanConfig(showFilesWithoutMetadata = false),
        )

        assertEquals(AudioFilterResult.Included, result)
    }

    @Test
    fun systemFlagsAndFoldersAreExcludedWithoutRelyingOnIsMusic() {
        val flaggedResult = filter.evaluate(
            audio = audio(isMusic = true, isRingtone = true),
            config = LocalSongScanConfig(),
        )
        val folderResult = filter.evaluate(
            audio = audio(
                directoryPath = "Notifications",
                isMusic = false,
                isNotification = false,
            ),
            config = LocalSongScanConfig(),
        )
        val ordinaryMusicResult = filter.evaluate(
            audio = audio(directoryPath = "Download", isMusic = false),
            config = LocalSongScanConfig(),
        )
        val albumNamedNotificationsResult = filter.evaluate(
            audio = audio(directoryPath = "Music/Artist/Notifications"),
            config = LocalSongScanConfig(),
        )

        assertExcluded(LocalAudioExclusionReason.SYSTEM_SOUND, flaggedResult)
        assertExcluded(LocalAudioExclusionReason.SYSTEM_SOUND, folderResult)
        assertEquals(AudioFilterResult.Included, ordinaryMusicResult)
        assertEquals(AudioFilterResult.Included, albumNamedNotificationsResult)
    }

    @Test
    fun podcastAndAudiobookSignalsAvoidRecordingAndMetadataFalsePositives() {
        val podcastResult = filter.evaluate(
            audio = audio(
                directoryPath = "Telegram/Telegram Audio",
                title = null,
                artist = null,
                album = null,
                displayName = "AUD-20260728-0001.ogg",
                isPodcast = true,
            ),
            config = LocalSongScanConfig(showFilesWithoutMetadata = false),
        )
        val audiobookResult = filter.evaluate(
            audio = audio(
                directoryPath = "Recordings",
                title = null,
                artist = null,
                album = null,
                displayName = "20260728120100.m4b",
                isAudiobook = true,
            ),
            config = LocalSongScanConfig(showFilesWithoutMetadata = false),
        )

        assertEquals(AudioFilterResult.Included, podcastResult)
        assertEquals(AudioFilterResult.Included, audiobookResult)
    }

    @Test
    fun durationAndSizeUseStrictMinimumsOnlyWhenMetadataIsAvailable() {
        val tooShort = filter.evaluate(
            audio = audio(durationMs = 59_999L),
            config = LocalSongScanConfig(),
        )
        val tooSmall = filter.evaluate(
            audio = audio(sizeBytes = 499L * 1024L),
            config = LocalSongScanConfig(),
        )
        val exactLimits = filter.evaluate(
            audio = audio(durationMs = 60_000L, sizeBytes = 500L * 1024L),
            config = LocalSongScanConfig(),
        )
        val unavailableValues = filter.evaluate(
            audio = audio(durationMs = null, sizeBytes = null),
            config = LocalSongScanConfig(),
        )

        assertExcluded(LocalAudioExclusionReason.TOO_SHORT, tooShort)
        assertExcluded(LocalAudioExclusionReason.TOO_SMALL, tooSmall)
        assertEquals(AudioFilterResult.Included, exactLimits)
        assertEquals(AudioFilterResult.Included, unavailableValues)
    }

    @Test
    fun manuallyExcludedFoldersAreNormalizedAndApplyToChildren() {
        val config = LocalSongScanConfig(
            excludedFolders = setOf(" Music\\Personal ", "music/personal/"),
        )
        val result = filter.evaluate(
            audio = audio(directoryPath = "MUSIC/Personal/Album"),
            config = config,
        )

        assertEquals(1, config.sanitizedExcludedFolders.size)
        assertExcluded(LocalAudioExclusionReason.EXCLUDED_FOLDER, result)
    }

    @Test
    fun metadataFilterRequiresAllFieldsMissingAndGenericGeneratedName() {
        val genericRecording = filter.evaluate(
            audio = audio(
                displayName = "PTT-20260728-WA0001.opus",
                title = "PTT-20260728-WA0001",
                artist = "<unknown>",
                album = null,
            ),
            config = LocalSongScanConfig(showFilesWithoutMetadata = false),
        )
        val unknownArtistOnly = filter.evaluate(
            audio = audio(
                displayName = "A real song.flac",
                title = "A real song",
                artist = "<unknown>",
                album = null,
            ),
            config = LocalSongScanConfig(showFilesWithoutMetadata = false),
        )

        assertExcluded(LocalAudioExclusionReason.INSUFFICIENT_METADATA, genericRecording)
        assertEquals(AudioFilterResult.Included, unknownArtistOnly)
    }

    @Test
    fun disablingAllExclusionFiltersIncludesTheFile() {
        val result = filter.evaluate(
            audio = audio(
                directoryPath = "Notifications/WhatsApp Voice Notes",
                displayName = "AUD-20260728-0001.opus",
                title = null,
                artist = null,
                album = null,
                durationMs = 1_000L,
                sizeBytes = 1_000L,
                isRingtone = true,
                isRecording = true,
            ),
            config = LocalSongScanConfig(
                hideShortAudio = false,
                hideSmallFiles = false,
                hideRecordings = false,
                hideSystemSounds = false,
                showFilesWithoutMetadata = true,
            ),
        )

        assertEquals(AudioFilterResult.Included, result)
    }

    private fun assertExcluded(
        reason: LocalAudioExclusionReason,
        result: AudioFilterResult,
    ) {
        assertEquals(AudioFilterResult.Excluded(reason), result)
    }

    private fun audio(
        directoryPath: String? = "Music/Albums",
        displayName: String? = "Song.mp3",
        title: String? = "Song",
        artist: String? = "Artist",
        album: String? = "Album",
        durationMs: Long? = 180_000L,
        sizeBytes: Long? = 5L * 1024L * 1024L,
        isMusic: Boolean? = true,
        isRingtone: Boolean? = false,
        isAlarm: Boolean? = false,
        isNotification: Boolean? = false,
        isPodcast: Boolean? = false,
        isAudiobook: Boolean? = false,
        isRecording: Boolean? = false,
    ) = LocalAudioFile(
        directoryPath = directoryPath,
        displayName = displayName,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        isMusic = isMusic,
        isRingtone = isRingtone,
        isAlarm = isAlarm,
        isNotification = isNotification,
        isPodcast = isPodcast,
        isAudiobook = isAudiobook,
        isRecording = isRecording,
    )
}

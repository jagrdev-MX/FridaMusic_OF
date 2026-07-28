package com.jagr.fridamusic.localmedia

import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class LocalSongScanConfig(
    val hideShortAudio: Boolean = true,
    val minimumDurationSeconds: Int = DEFAULT_MINIMUM_DURATION_SECONDS,
    val hideSmallFiles: Boolean = true,
    val minimumSizeKb: Int = DEFAULT_MINIMUM_SIZE_KB,
    val hideRecordings: Boolean = true,
    val hideSystemSounds: Boolean = true,
    val showFilesWithoutMetadata: Boolean = true,
    val excludedFolders: Set<String> = emptySet(),
) {
    val sanitizedMinimumDurationSeconds: Int
        get() = minimumDurationSeconds.coerceAtLeast(0)

    val sanitizedMinimumSizeKb: Int
        get() = minimumSizeKb.coerceAtLeast(0)

    val minimumSizeBytes: Long
        get() = sanitizedMinimumSizeKb.toLong() * BYTES_PER_KB

    val sanitizedExcludedFolders: Set<String>
        get() = deduplicateFolderEntries(excludedFolders)

    fun sanitized(): LocalSongScanConfig = copy(
        minimumDurationSeconds = sanitizedMinimumDurationSeconds,
        minimumSizeKb = sanitizedMinimumSizeKb,
        excludedFolders = sanitizedExcludedFolders,
    )

    companion object {
        const val DEFAULT_MINIMUM_DURATION_SECONDS = 60
        const val DEFAULT_MINIMUM_SIZE_KB = 500
        val DurationOptionsSeconds = listOf(15, 30, 60, 90, 120)
        val SizeOptionsKb = listOf(100, 250, 500, 1024, 2048)

        private const val BYTES_PER_KB = 1024L
        private val DuplicateSlashRegex = Regex("/+")
        private val CombiningMarkRegex = Regex("\\p{M}+")
        private val DuplicateWhitespaceRegex = Regex("\\s+")

        fun normalizeFolderEntry(raw: String): String {
            return raw
                .trim()
                .replace('\\', '/')
                .replace(DuplicateSlashRegex, "/")
                .trim('/')
        }

        fun canonicalFolderEntry(raw: String): String {
            return normalizeFolderEntry(raw)
                .split('/')
                .map(::canonicalSegment)
                .filter(String::isNotEmpty)
                .joinToString("/")
        }

        fun deduplicateFolderEntries(entries: Iterable<String>): Set<String> {
            val deduplicated = linkedMapOf<String, String>()
            entries.forEach { entry ->
                val normalized = normalizeFolderEntry(entry)
                val canonical = canonicalFolderEntry(normalized)
                if (canonical.isNotEmpty()) {
                    deduplicated.putIfAbsent(canonical, normalized)
                }
            }
            return deduplicated.values.toSet()
        }

        private fun canonicalSegment(raw: String): String {
            return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(CombiningMarkRegex, "")
                .lowercase(Locale.ROOT)
                .trim()
                .replace(DuplicateWhitespaceRegex, " ")
        }
    }
}

data class LocalAudioFile(
    val directoryPath: String?,
    val displayName: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val isMusic: Boolean?,
    val isRingtone: Boolean?,
    val isAlarm: Boolean?,
    val isNotification: Boolean?,
    val isPodcast: Boolean?,
    val isAudiobook: Boolean?,
    val isRecording: Boolean?,
)

data class LocalAudioFolder(
    val path: String,
    val audioCount: Int,
)

sealed interface AudioFilterResult {
    data object Included : AudioFilterResult

    data class Excluded(
        val reason: LocalAudioExclusionReason,
    ) : AudioFilterResult
}

enum class LocalAudioExclusionReason {
    SYSTEM_SOUND,
    EXCLUDED_FOLDER,
    RECORDING_OR_VOICE_NOTE,
    TOO_SHORT,
    TOO_SMALL,
    INSUFFICIENT_METADATA,
}

class LocalAudioFilter
@Inject
constructor() {
    fun evaluate(
        audio: LocalAudioFile,
        config: LocalSongScanConfig,
    ): AudioFilterResult {
        val sanitizedConfig = config.sanitized()

        if (
            sanitizedConfig.hideSystemSounds &&
            (
                audio.isRingtone == true ||
                    audio.isAlarm == true ||
                    audio.isNotification == true ||
                    isKnownSystemSoundPath(audio.directoryPath, audio.isMusic)
                )
        ) {
            return AudioFilterResult.Excluded(LocalAudioExclusionReason.SYSTEM_SOUND)
        }

        if (isManuallyExcluded(audio.directoryPath, sanitizedConfig.sanitizedExcludedFolders)) {
            return AudioFilterResult.Excluded(LocalAudioExclusionReason.EXCLUDED_FOLDER)
        }

        if (
            sanitizedConfig.hideRecordings &&
            audio.isPodcast != true &&
            audio.isAudiobook != true &&
            (audio.isRecording == true || isKnownRecordingPath(audio.directoryPath))
        ) {
            return AudioFilterResult.Excluded(LocalAudioExclusionReason.RECORDING_OR_VOICE_NOTE)
        }

        val durationMs = audio.durationMs
        if (
            sanitizedConfig.hideShortAudio &&
            sanitizedConfig.sanitizedMinimumDurationSeconds > 0 &&
            durationMs != null &&
            durationMs > 0L &&
            durationMs < sanitizedConfig.sanitizedMinimumDurationSeconds * MILLIS_PER_SECOND
        ) {
            return AudioFilterResult.Excluded(LocalAudioExclusionReason.TOO_SHORT)
        }

        val sizeBytes = audio.sizeBytes
        if (
            sanitizedConfig.hideSmallFiles &&
            sanitizedConfig.minimumSizeBytes > 0L &&
            sizeBytes != null &&
            sizeBytes > 0L &&
            sizeBytes < sanitizedConfig.minimumSizeBytes
        ) {
            return AudioFilterResult.Excluded(LocalAudioExclusionReason.TOO_SMALL)
        }

        if (
            !sanitizedConfig.showFilesWithoutMetadata &&
            audio.isPodcast != true &&
            audio.isAudiobook != true &&
            hasInsufficientMetadata(audio)
        ) {
            return AudioFilterResult.Excluded(LocalAudioExclusionReason.INSUFFICIENT_METADATA)
        }

        return AudioFilterResult.Included
    }

    private fun isKnownSystemSoundPath(
        directoryPath: String?,
        isMusic: Boolean?,
    ): Boolean {
        val segments = canonicalSegments(directoryPath)
        if (segments.firstOrNull() in KnownSystemFolderNames) return true

        return isMusic != true &&
            "music" !in segments &&
            segments.lastOrNull() in KnownSystemFolderNames
    }

    private fun isKnownRecordingPath(directoryPath: String?): Boolean {
        return canonicalSegments(directoryPath).any(KnownRecordingFolderNames::contains)
    }

    private fun isManuallyExcluded(
        directoryPath: String?,
        excludedFolders: Set<String>,
    ): Boolean {
        val folderPath = LocalSongScanConfig.canonicalFolderEntry(directoryPath.orEmpty())
        if (folderPath.isEmpty() || excludedFolders.isEmpty()) return false

        return excludedFolders.any { excludedFolder ->
            val canonicalExcludedFolder = LocalSongScanConfig.canonicalFolderEntry(excludedFolder)
            canonicalExcludedFolder.isNotEmpty() &&
                (
                    folderPath == canonicalExcludedFolder ||
                        folderPath.startsWith("$canonicalExcludedFolder/") ||
                        folderPath.endsWith("/$canonicalExcludedFolder") ||
                        folderPath.contains("/$canonicalExcludedFolder/")
                    )
        }
    }

    private fun hasInsufficientMetadata(audio: LocalAudioFile): Boolean {
        val displayTitle = audio.displayName
            ?.let { displayName ->
                displayName.substringBeforeLast('.', missingDelimiterValue = displayName)
            }
        val hasValidTitle = sequenceOf(audio.title, displayTitle)
            .filterNotNull()
            .any(::isValidTitle)
        val hasValidArtist = isMeaningfulMetadata(audio.artist, ArtistPlaceholders)
        val hasValidAlbum = isMeaningfulMetadata(audio.album, AlbumPlaceholders)
        val hasGenericGeneratedName = sequenceOf(audio.title, displayTitle)
            .filterNotNull()
            .any(::isGenericGeneratedName)

        return !hasValidTitle &&
            !hasValidArtist &&
            !hasValidAlbum &&
            hasGenericGeneratedName
    }

    private fun isValidTitle(value: String): Boolean {
        return isMeaningfulMetadata(value, TitlePlaceholders) && !isGenericGeneratedName(value)
    }

    private fun isMeaningfulMetadata(
        value: String?,
        placeholders: Set<String>,
    ): Boolean {
        val normalized = normalizeWords(value.orEmpty())
        return normalized.isNotEmpty() && normalized !in placeholders
    }

    private fun isGenericGeneratedName(value: String): Boolean {
        val normalized = normalizeWords(value)
        if (normalized in ExactGenericNames) return true

        return GenericPrefixedIdRegex.matches(normalized) ||
            DateTimeNameRegex.matches(normalized) ||
            UuidOrHexNameRegex.matches(normalized)
    }

    private fun canonicalSegments(path: String?): List<String> {
        return LocalSongScanConfig.normalizeFolderEntry(path.orEmpty())
            .split('/')
            .map { segment -> normalizeWords(segment).replace(" ", "") }
            .filter(String::isNotEmpty)
    }

    private fun normalizeWords(raw: String): String {
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(CombiningMarkRegex, "")
            .lowercase(Locale.ROOT)
            .replace(WordSeparatorRegex, " ")
            .trim()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L

        val CombiningMarkRegex = Regex("\\p{M}+")
        val WordSeparatorRegex = Regex("[^a-z0-9]+")
        val GenericPrefixedIdRegex = Regex(
            """^(?:aud|audio|ptt|vn|rec|recording|voice note|voice memo|audio record|audiorecord|memo|grabacion|nota de voz)[ ]*[0-9][a-z0-9 ]{2,}$""",
        )
        val DateTimeNameRegex = Regex("""^[0-9]{8,14}(?: [0-9]{4,6})?$""")
        val UuidOrHexNameRegex = Regex("""^(?:[a-f0-9]{12,}|[a-f0-9]{8}(?: [a-f0-9]{4}){3} [a-f0-9]{12})$""")

        val KnownSystemFolderNames = setOf(
            "alarm",
            "alarms",
            "notification",
            "notifications",
            "ringtone",
            "ringtones",
        )
        val KnownRecordingFolderNames = setOf(
            "audiorecord",
            "callrecordings",
            "grabaciones",
            "grabadora",
            "notasdevoz",
            "recorder",
            "recordings",
            "telegramaudio",
            "telegramvoice",
            "voicenotes",
            "voicerecorder",
            "whatsappvoicenotes",
        )
        val TitlePlaceholders = setOf(
            "desconocido",
            "sin titulo",
            "unknown",
            "unknown title",
            "untitled",
        )
        val ArtistPlaceholders = setOf(
            "artista desconocido",
            "desconocido",
            "unknown",
            "unknown artist",
        )
        val AlbumPlaceholders = setOf(
            "album desconocido",
            "desconocido",
            "unknown",
            "unknown album",
        )
        val ExactGenericNames = setOf(
            "audio recording",
            "audiorecord",
            "grabacion",
            "grabadora",
            "memo",
            "nota de voz",
            "notas de voz",
            "recording",
            "voice memo",
            "voice note",
        )
    }
}

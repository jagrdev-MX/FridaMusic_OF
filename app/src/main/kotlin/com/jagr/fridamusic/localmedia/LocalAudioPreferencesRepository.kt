package com.jagr.fridamusic.localmedia

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jagr.fridamusic.constants.LocalSongsExcludedFoldersKey
import com.jagr.fridamusic.constants.LocalSongsBlacklistedIdsKey
import com.jagr.fridamusic.constants.LocalSongsHideRecordingsKey
import com.jagr.fridamusic.constants.LocalSongsHideShortAudioKey
import com.jagr.fridamusic.constants.LocalSongsHideSmallFilesKey
import com.jagr.fridamusic.constants.LocalSongsHideSystemSoundsKey
import com.jagr.fridamusic.constants.LocalSongsMinDurationSecondsKey
import com.jagr.fridamusic.constants.LocalSongsMinSizeKbKey
import com.jagr.fridamusic.constants.LocalSongsPinnedIdsKey
import com.jagr.fridamusic.constants.LocalSongsSortDescendingKey
import com.jagr.fridamusic.constants.LocalSongsSortTypeKey
import com.jagr.fridamusic.constants.LocalSongsShowFilesWithoutMetadataKey
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class LocalSongSortPreference(
    val typeName: String = "DATE_ADDED",
    val descending: Boolean = false,
)

@Singleton
class LocalAudioPreferencesRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    val scanConfig: Flow<LocalSongScanConfig> = context.dataStore.data
        .map(::readScanConfig)
        .distinctUntilChanged()

    val pinnedSongIds: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[LocalSongsPinnedIdsKey].orEmpty() }
        .distinctUntilChanged()

    val blacklistedSongIds: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[LocalSongsBlacklistedIdsKey].orEmpty() }
        .distinctUntilChanged()

    val sortPreference: Flow<LocalSongSortPreference> = context.dataStore.data
        .map { preferences ->
            LocalSongSortPreference(
                typeName = preferences[LocalSongsSortTypeKey] ?: "DATE_ADDED",
                descending = preferences[LocalSongsSortDescendingKey] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun currentScanConfig(): LocalSongScanConfig = scanConfig.first()

    suspend fun setHideShortAudio(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LocalSongsHideShortAudioKey] = enabled
            if (
                enabled &&
                (preferences[LocalSongsMinDurationSecondsKey] ?: 0) <= 0
            ) {
                preferences[LocalSongsMinDurationSecondsKey] =
                    LocalSongScanConfig.DEFAULT_MINIMUM_DURATION_SECONDS
            }
        }
    }

    suspend fun setMinimumDurationSeconds(seconds: Int) {
        if (seconds !in LocalSongScanConfig.DurationOptionsSeconds) return
        context.dataStore.edit { preferences ->
            preferences[LocalSongsMinDurationSecondsKey] = seconds
        }
    }

    suspend fun setHideSmallFiles(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LocalSongsHideSmallFilesKey] = enabled
        }
    }

    suspend fun setMinimumSizeKb(sizeKb: Int) {
        if (sizeKb !in LocalSongScanConfig.SizeOptionsKb) return
        context.dataStore.edit { preferences ->
            preferences[LocalSongsMinSizeKbKey] = sizeKb
        }
    }

    suspend fun setHideRecordings(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LocalSongsHideRecordingsKey] = enabled
        }
    }

    suspend fun setHideSystemSounds(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LocalSongsHideSystemSoundsKey] = enabled
        }
    }

    suspend fun setShowFilesWithoutMetadata(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LocalSongsShowFilesWithoutMetadataKey] = enabled
        }
    }

    suspend fun setExcludedFolders(folders: Set<String>) {
        val normalizedFolders = LocalSongScanConfig.deduplicateFolderEntries(folders)
        context.dataStore.edit { preferences ->
            if (normalizedFolders.isEmpty()) {
                preferences.remove(LocalSongsExcludedFoldersKey)
            } else {
                preferences[LocalSongsExcludedFoldersKey] = normalizedFolders
            }
        }
    }

    suspend fun togglePinnedSong(songId: String) {
        updateSongIdSet(LocalSongsPinnedIdsKey, songId, add = null)
    }

    suspend fun setSortType(typeName: String) {
        context.dataStore.edit { preferences -> preferences[LocalSongsSortTypeKey] = typeName }
    }

    suspend fun setSortDescending(descending: Boolean) {
        context.dataStore.edit { preferences -> preferences[LocalSongsSortDescendingKey] = descending }
    }

    suspend fun setSongBlacklisted(songId: String, blacklisted: Boolean) {
        updateSongIdSet(LocalSongsBlacklistedIdsKey, songId, add = blacklisted)
        if (blacklisted) {
            updateSongIdSet(LocalSongsPinnedIdsKey, songId, add = false)
        }
    }

    private suspend fun updateSongIdSet(
        key: Preferences.Key<Set<String>>,
        songId: String,
        add: Boolean?,
    ) {
        if (songId.isBlank()) return
        context.dataStore.edit { preferences ->
            val updated = preferences[key].orEmpty().toMutableSet()
            val shouldAdd = add ?: (songId !in updated)
            if (shouldAdd) updated += songId else updated -= songId
            if (updated.isEmpty()) preferences.remove(key) else preferences[key] = updated
        }
    }

    private fun readScanConfig(preferences: Preferences): LocalSongScanConfig {
        val legacyMinimumDuration = preferences[LocalSongsMinDurationSecondsKey]
        val hideShortAudio = preferences[LocalSongsHideShortAudioKey]
            ?: legacyMinimumDuration?.let { it > 0 }
            ?: true

        return LocalSongScanConfig(
            hideShortAudio = hideShortAudio,
            minimumDurationSeconds = legacyMinimumDuration
                ?.takeIf { it > 0 }
                ?: LocalSongScanConfig.DEFAULT_MINIMUM_DURATION_SECONDS,
            hideSmallFiles = preferences[LocalSongsHideSmallFilesKey] ?: true,
            minimumSizeKb = preferences[LocalSongsMinSizeKbKey]
                ?.takeIf { it > 0 }
                ?: LocalSongScanConfig.DEFAULT_MINIMUM_SIZE_KB,
            hideRecordings = preferences[LocalSongsHideRecordingsKey] ?: true,
            hideSystemSounds = preferences[LocalSongsHideSystemSoundsKey] ?: true,
            showFilesWithoutMetadata =
                preferences[LocalSongsShowFilesWithoutMetadataKey] ?: true,
            excludedFolders = preferences[LocalSongsExcludedFoldersKey].orEmpty(),
        ).sanitized()
    }
}

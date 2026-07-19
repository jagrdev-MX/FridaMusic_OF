package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.constants.*
import com.jagr.fridamusic.lyrics.LyricsProviderRegistry
import com.jagr.fridamusic.utils.rememberEnumPreference
import com.jagr.fridamusic.utils.rememberPreference
import com.jagr.fridamusic.viewmodels.AccountSettingsViewModel
import com.jagr.fridamusic.R
import java.net.Proxy

private enum class SettingsTab(val labelRes: Int, val icon: ImageVector) {
    ACCOUNT   (R.string.account,       Icons.Rounded.AccountCircle),
    PLAYBACK  (R.string.playback,      Icons.Rounded.PlayCircle),
    APPEARANCE(R.string.appearance,    Icons.Rounded.Palette),
    LYRICS    (R.string.lyrics,        Icons.Rounded.Lyrics),
    CONTENT   (R.string.content,       Icons.Rounded.FilterList),
    SERVICES  (R.string.services,      Icons.Rounded.Sync),
    PRIVACY   (R.string.privacy,       Icons.Rounded.Lock),
    NETWORK   (R.string.network,       Icons.Rounded.Wifi),
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
) {
    var activeTab by remember { mutableStateOf(SettingsTab.ACCOUNT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        PrimaryScrollableTabRow(
            selectedTabIndex = activeTab.ordinal,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
        ) {
            SettingsTab.entries.forEach { tab ->
                Tab(
                    selected = tab == activeTab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings_tab",
        ) { tab ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp),
            ) {
                when (tab) {
                    SettingsTab.ACCOUNT    -> accountItems(onNavigateToLogin)
                    SettingsTab.PLAYBACK   -> playbackItems()
                    SettingsTab.APPEARANCE -> appearanceItems()
                    SettingsTab.LYRICS     -> lyricsItems()
                    SettingsTab.CONTENT    -> contentItems()
                    SettingsTab.SERVICES   -> servicesItems()
                    SettingsTab.PRIVACY    -> privacyItems()
                    SettingsTab.NETWORK    -> networkItems()
                }
            }
        }
    }
}

private fun LazyListScope.accountItems(onNavigateToLogin: () -> Unit) {
    item {
        AccountSection(onNavigateToLogin = onNavigateToLogin)
    }
}

@Composable
private fun AccountSection(
    onNavigateToLogin: () -> Unit,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountHandle by rememberPreference(AccountChannelHandleKey, "")
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = cookie.isNotEmpty() && "SAPISID" in cookie

    var showLogoutDialog by remember { mutableStateOf(false) }

    SettingGroup(stringResource(R.string.group_yt_music)) {
        if (isLoggedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountName.ifEmpty { stringResource(R.string.google_account) },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (accountEmail.isNotEmpty()) {
                        Text(
                            text = accountEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (accountHandle.isNotEmpty()) {
                        Text(
                            text = accountHandle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            PrefSwitch(
                icon = Icons.Rounded.Sync,
                title = stringResource(R.string.sync_ytm),
                subtitle = stringResource(R.string.import_yt_info),
                key = YtmSyncKey,
                default = true,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.action_logout),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.iniciar_sesion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.login_google))
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.action_logout)) },
            text = { Text(stringResource(R.string.sync_action)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logoutAndClearSyncedContent(context) {}
                    showLogoutDialog = false
                }) {
                    Text(stringResource(R.string.logout_clear_data), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.logoutKeepData(context) {}
                    showLogoutDialog = false
                }) {
                    Text(stringResource(R.string.logout_keep_data))
                }
            },
        )
    }
}

private fun LazyListScope.playbackItems() {
    item {
        SettingGroup(stringResource(R.string.group_general)) {
            PrefSwitch(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.persistent_queue),
                stringResource(R.string.persistent_queue_desc), PersistentQueueKey, true)
            PrefSwitch(Icons.Rounded.SkipNext, stringResource(R.string.auto_skip_next_on_error),
                stringResource(R.string.auto_skip_next_on_error_desc), AutoSkipNextOnErrorKey, false)
            PrefSwitch(Icons.Rounded.FastForward, stringResource(R.string.preload_next_song),
                stringResource(R.string.preload_next_song_desc), PreloadNextSongEnabledKey, true)
            PrefSwitch(Icons.Rounded.WbSunny, stringResource(R.string.keep_screen_on),
                stringResource(R.string.keep_screen_on_desc), KeepScreenOn, false)
            PrefSwitch(Icons.Rounded.Stop, stringResource(R.string.stop_music_on_task_clear),
                stringResource(R.string.stop_music_on_task_clear_desc), StopMusicOnTaskClearKey, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_audio)) {
            PrefSwitch(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.skip_silence),
                stringResource(R.string.skip_silence_desc), SkipSilenceKey, false)
            PrefSwitch(Icons.Rounded.Equalizer, stringResource(R.string.audio_normalization),
                stringResource(R.string.audio_normalization_desc), AudioNormalizationKey, true)
            PrefSwitch(Icons.Rounded.Tune, stringResource(R.string.crossfade),
                stringResource(R.string.crossfade_desc), CrossfadeEnabledKey, false)
            PrefSwitch(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.pause_music_when_media_is_muted),
                stringResource(R.string.pause_on_mute_desc), PauseOnMute, false)
            PrefSwitch(Icons.Rounded.BatteryChargingFull, stringResource(R.string.audio_offload),
                stringResource(R.string.audio_offload_desc), AudioOffload, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_quality)) {
            PrefDropdownEnum<AudioQuality>(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.streaming_quality),
                options = listOf(
                    AudioQuality.OPUS.name to stringResource(R.string.opus_recommended),
                    AudioQuality.SAAVN.name to stringResource(R.string.saavn),
                    AudioQuality.LOSSLESS.name to stringResource(R.string.lossless),
                ),
                prefKey = AudioQualityKey,
                default = AudioQuality.OPUS,
            )
            PrefDropdownEnum<DownloadQuality>(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.download_quality),
                options = listOf(
                    DownloadQuality.YOUTUBE.name to stringResource(R.string.youtube),
                    DownloadQuality.SAAVN.name to stringResource(R.string.saavn),
                    DownloadQuality.LOSSLESS.name to stringResource(R.string.lossless),
                ),
                prefKey = DownloadQualityKey,
                default = DownloadQuality.YOUTUBE,
            )
        }
    }
}

private fun LazyListScope.appearanceItems() {
    item {
        SettingGroup(stringResource(R.string.group_theme)) {
            PrefDropdownString(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.color_mode),
                options = listOf(
                    SYSTEM_DEFAULT to stringResource(R.string.system_default),
                    "ON" to stringResource(R.string.dark),
                    "OFF" to stringResource(R.string.light),
                ),
                prefKey = DarkModeKey,
                default = SYSTEM_DEFAULT,
            )
            PrefSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.dynamic_colors),
                stringResource(R.string.dynamic_colors_desc), DynamicThemeKey, true)
            PrefSwitch(Icons.Rounded.Contrast, stringResource(R.string.pure_black),
                stringResource(R.string.pure_black_desc), PureBlackKey, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_language_region)) {
            LanguageSection()
        }
    }
}

@Composable
private fun LanguageSection() {
    var appLanguage by rememberPreference(AppLanguageKey, SYSTEM_DEFAULT)
    var contentLanguage by rememberPreference(ContentLanguageKey, SYSTEM_DEFAULT)
    var contentCountry by rememberPreference(ContentCountryKey, SYSTEM_DEFAULT)

    val languageOptions = listOf(SYSTEM_DEFAULT to stringResource(R.string.system_default)) +
            LanguageCodeToName.entries.map { it.key to it.value }

    val countryOptions = listOf(SYSTEM_DEFAULT to stringResource(R.string.system_default)) +
            CountryCodeToName.entries.map { it.key to it.value }

    SettingDropdown(
        icon = Icons.Rounded.Translate,
        title = stringResource(R.string.app_language),
        options = languageOptions,
        selectedKey = appLanguage,
        onSelect = { appLanguage = it },
    )
    SettingDropdown(
        icon = Icons.Rounded.Language,
        title = stringResource(R.string.language_content),
        options = languageOptions,
        selectedKey = contentLanguage,
        onSelect = { contentLanguage = it },
    )
    SettingDropdown(
        icon = Icons.Rounded.Public,
        title = stringResource(R.string.region_content),
        options = countryOptions,
        selectedKey = contentCountry,
        onSelect = { contentCountry = it },
    )
    Text(
        text = stringResource(R.string.restart_apply_changes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun LazyListScope.lyricsItems() {
    item {
        SettingGroup(stringResource(R.string.group_general)) {
            PrefSwitch(Icons.Rounded.Lyrics, stringResource(R.string.auto_lyrics),
                stringResource(R.string.auto_lyrics_desc), ShowLyricsKey, false)
            PrefSwitch(Icons.Rounded.Cached, stringResource(R.string.preload_lyrics),
                stringResource(R.string.preload_lyrics_desc), PreloadLyricsEnabledKey, true)
        }
    }
    item {
        SettingGroup(stringResource(R.string.lyrics_providers_label)) {
            LyricsProviderOrderSection()
        }
    }
}

private fun LazyListScope.contentItems() {
    item {
        SettingGroup(stringResource(R.string.filters)) {
            PrefSwitch(Icons.Rounded.Block, stringResource(R.string.hide_explicit),
                stringResource(R.string.hide_explicit_desc), HideExplicitKey, false)
            PrefSwitch(Icons.Rounded.VideocamOff, stringResource(R.string.hide_video_songs),
                stringResource(R.string.hide_video_songs_desc), HideVideoSongsKey, false)
            PrefSwitch(Icons.Rounded.VideoLibrary, stringResource(R.string.hide_youtube_shorts),
                stringResource(R.string.hide_youtube_shorts_desc), HideYoutubeShortsKey, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.sponsorblock)) {
            PrefSwitch(Icons.Rounded.Block, stringResource(R.string.enable_sponsorblock),
                stringResource(R.string.enable_sponsorblock_desc), SponsorBlockEnabledKey, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.downloads)) {
            PrefSwitch(Icons.Rounded.FavoriteBorder, stringResource(R.string.auto_download_on_like),
                stringResource(R.string.download_on_like_desc),
                AutoDownloadOnLikeKey, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.cache_storage)) {
            CacheSection()
        }
    }
}

@Composable
private fun CacheSection() {
    var imageCacheSize by rememberPreference(MaxImageCacheSizeKey, 512)
    var songCacheSize by rememberPreference(MaxSongCacheSizeKey, 1024)

    val imageSizeLabel = "${imageCacheSize} MB"
    val songSizeLabel = if (songCacheSize == -1) stringResource(R.string.unlimited) else "${songCacheSize} MB"

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.cache_images),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = imageSizeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = imageCacheSize.toFloat(),
            onValueChange = { imageCacheSize = it.toInt() },
            valueRange = 128f..2048f,
            steps = 14,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("128 MB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2048 MB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.cache_songs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = songSizeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = if (songCacheSize == -1) 5120f else songCacheSize.toFloat(),
            onValueChange = { value ->
                songCacheSize = if (value >= 5120f) -1 else value.toInt()
            },
            valueRange = 256f..5120f,
            steps = 18,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("256 MB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.unlimited), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(8.dp))
        PrefSwitch(
            icon = Icons.Rounded.PlaylistAddCheck,
            title = stringResource(R.string.show_cached_playlists),
            subtitle = stringResource(R.string.show_cached_playlists_desc),
            key = ShowCachedPlaylistKey,
            default = true,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.cache_apply_changes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun LazyListScope.servicesItems() {
    item {
        SettingGroup(stringResource(R.string.group_lastfm)) {
            LastFmSection()
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_listenbrainz)) {
            ListenBrainzSection()
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_discord)) {
            PrefSwitch(Icons.AutoMirrored.Rounded.Chat, stringResource(R.string.discord_integration),
                stringResource(R.string.discord_rpc_desc), EnableDiscordRPCKey, true)
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_echobrain)) {
            PrefSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.enable_echobrain),
                stringResource(R.string.enable_echobrain_desc), EchoBrainEnabledKey, false)
        }
    }
}

private fun LazyListScope.privacyItems() {
    item {
        SettingGroup(stringResource(R.string.group_history)) {
            PrefSwitch(Icons.Rounded.History, stringResource(R.string.pause_listen_history),
                stringResource(R.string.pause_listen_history_desc), PauseListenHistoryKey, false)
            PrefSwitch(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.pause_search_history),
                stringResource(R.string.pause_search_history_desc), PauseSearchHistoryKey, false)
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_screen)) {
            PrefSwitch(Icons.Rounded.Monitor, stringResource(R.string.block_screenshots),
                stringResource(R.string.block_screenshots_desc), DisableScreenshotKey, false)
        }
    }
}

private fun LazyListScope.networkItems() {
    item {
        SettingGroup(stringResource(R.string.group_proxy)) {
            ProxySettings()
        }
    }
}

@Composable
private fun SettingGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        content()
    }
}

@Composable
private fun PrefSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
    default: Boolean,
) {
    var value by rememberPreference(key, default)
    SettingSwitch(icon, title, subtitle, value) { value = it }
}

@Composable
private fun PrefDropdownString(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, String>>,
    prefKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    default: String,
) {
    var value by rememberPreference(prefKey, default)
    SettingDropdown(icon, title, options, value) { value = it }
}

@Composable
private inline fun <reified E : Enum<E>> PrefDropdownEnum(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, String>>,
    prefKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    default: E,
) {
    var value by rememberEnumPreference<E>(prefKey, default)
    SettingDropdown(icon, title, options, value.name) { value = enumValueOf(it) }
}

@Composable
private fun LyricsProviderOrderSection() {
    var orderString by rememberPreference(LyricsProviderOrderKey, "")
    val allProviders = LyricsProviderRegistry.providerNames

    val currentOrder = remember(orderString) {
        val saved = orderString.split(",").filter { it.isNotBlank() && it in allProviders }
        val missing = allProviders.filter { it !in saved }
        (saved + missing).toMutableStateList()
    }

    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.lyrics_order_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        currentOrder.forEachIndexed { index, name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = LyricsProviderRegistry.getDisplayName(name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (index > 0) {
                    IconButton(
                        onClick = {
                            val list = currentOrder.toMutableList()
                            val tmp = list[index - 1]
                            list[index - 1] = list[index]
                            list[index] = tmp
                            orderString = list.joinToString(",")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.action_up),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index < currentOrder.size - 1) {
                    IconButton(
                        onClick = {
                            val list = currentOrder.toMutableList()
                            val tmp = list[index + 1]
                            list[index + 1] = list[index]
                            list[index] = tmp
                            orderString = list.joinToString(",")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.action_down),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LastFmSection() {
    var enabled by rememberPreference(EnableLastFMScrobblingKey, false)
    var username by rememberPreference(LastFMUsernameKey, "")
    var nowPlaying by rememberPreference(LastFMUseNowPlaying, true)
    var sendLikes by rememberPreference(LastFMUseSendLikes, false)

    SettingSwitch(
        icon = Icons.Rounded.Radio,
        title = stringResource(R.string.lastfm_scrobbling),
        subtitle = if (username.isNotEmpty()) stringResource(R.string.lastfm_connected_as, username) else stringResource(R.string.lastfm_scrobbling_desc),
        checked = enabled,
        onCheckedChange = { enabled = it },
    )
    if (enabled) {
        Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp)) {
            SettingTextField(stringResource(R.string.username), stringResource(R.string.lastfm_user_hint), username) { username = it }
            SettingSwitch(Icons.Rounded.Podcasts, stringResource(R.string.now_playing),
                stringResource(R.string.now_playing_desc), nowPlaying) { nowPlaying = it }
            SettingSwitch(Icons.Rounded.Favorite, stringResource(R.string.sync_likes),
                stringResource(R.string.sync_likes_desc), sendLikes) { sendLikes = it }
        }
    }
}

@Composable
private fun ListenBrainzSection() {
    var enabled by rememberPreference(ListenBrainzEnabledKey, false)
    var token by rememberPreference(ListenBrainzTokenKey, "")

    SettingSwitch(
        icon = Icons.Rounded.MusicNote,
        title = stringResource(R.string.group_listenbrainz),
        subtitle = if (token.isNotEmpty()) stringResource(R.string.token_configured) else stringResource(R.string.listenbrainz_desc),
        checked = enabled,
        onCheckedChange = { enabled = it },
    )
    if (enabled) {
        Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp)) {
            SettingTextField(stringResource(R.string.token), stringResource(R.string.token_hint), token, isPassword = true) { token = it }
            Text(
                text = stringResource(R.string.get_token_at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ProxySettings() {
    var enabled by rememberPreference(ProxyEnabledKey, false)
    var url by rememberPreference(ProxyUrlKey, "")
    var type by rememberEnumPreference<Proxy.Type>(ProxyTypeKey, Proxy.Type.HTTP)
    var username by rememberPreference(ProxyUsernameKey, "")
    var password by rememberPreference(ProxyPasswordKey, "")

    SettingSwitch(Icons.Rounded.Key, stringResource(R.string.use_proxy),
        stringResource(R.string.use_proxy_desc), enabled) { enabled = it }

    if (enabled) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.proxy_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingDropdown(
                icon = Icons.Rounded.SettingsEthernet,
                title = stringResource(R.string.proxy_type),
                options = listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS"),
                selectedKey = type.name,
                onSelect = { type = Proxy.Type.valueOf(it) },
            )
            SettingTextField(stringResource(R.string.server), stringResource(R.string.server_hint), url) { url = it }
            SettingTextField(stringResource(R.string.user_optional), stringResource(R.string.username), username) { username = it }
            SettingTextField(stringResource(R.string.password_optional), stringResource(R.string.password), password, isPassword = true) { password = it }
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingDropdown(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.find { it.first == selectedKey }?.second ?: selectedKey

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, lbl) ->
                    DropdownMenuItem(
                        text = { Text(lbl) },
                        onClick = { onSelect(key); expanded = false },
                        trailingIcon = {
                            if (key == selectedKey) Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingTextField(
    label: String,
    placeholder: String,
    value: String,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
package com.jagr.fridamusic.presentation.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.BuildConfig
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.*
import com.jagr.fridamusic.lyrics.LyricsProviderRegistry
import com.jagr.fridamusic.notifications.NotificationCandidateType
import com.jagr.fridamusic.utils.CrashReporter
import com.jagr.fridamusic.notifications.RecommendationNotificationScheduler
import com.jagr.fridamusic.utils.rememberEnumPreference
import com.jagr.fridamusic.utils.rememberPreference
import com.jagr.fridamusic.viewmodels.AccountSettingsViewModel
import java.net.Proxy

private data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val accentColor: Color = Color.Unspecified,
    val onClick: () -> Unit,
)

private data class SettingsGroup(
    val title: String,
    val items: List<SettingsItem>,
)

private sealed class SettingsPage {
    object None : SettingsPage()
    object Account : SettingsPage()
    object Playback : SettingsPage()
    object Equalizer : SettingsPage()
    object Appearance : SettingsPage()
    object Lyrics : SettingsPage()
    object Content : SettingsPage()
    object Services : SettingsPage()
    object Privacy : SettingsPage()
    object Network : SettingsPage()
    object About : SettingsPage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    var currentPage: SettingsPage by remember { mutableStateOf(SettingsPage.None) }
    val rootListState = rememberLazyListState()

    BackHandler(enabled = currentPage != SettingsPage.None) {
        currentPage = SettingsPage.None
    }

    if (currentPage == SettingsPage.Equalizer) {
        EQScreen(onBack = { currentPage = SettingsPage.None })
        return
    }

    if (currentPage == SettingsPage.About) {
        onNavigateToAbout()
        currentPage = SettingsPage.None
        return
    }

    if (currentPage != SettingsPage.None) {
        SettingsDetailPage(
            page = currentPage,
            onBack = { currentPage = SettingsPage.None },
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToAbout = onNavigateToAbout,
        )
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val groups = listOf(
        SettingsGroup(
            title = stringResource(R.string.account_and_stats),
            items = listOf(
                SettingsItem(Icons.Rounded.AccountCircle, stringResource(R.string.account),
                    stringResource(R.string.login_sync_desc), primaryColor) {
                    currentPage = SettingsPage.Account
                },
                SettingsItem(Icons.Rounded.BarChart, stringResource(R.string.stats),
                    stringResource(R.string.stats_desc_short), primaryColor) {
                    onNavigateToStats()
                },
            ),
        ),
        SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(
                SettingsItem(Icons.Rounded.PlayCircle, stringResource(R.string.playback),
                    stringResource(R.string.playback_desc_short), tertiaryColor) {
                    currentPage = SettingsPage.Playback
                },
                SettingsItem(Icons.Rounded.GraphicEq, stringResource(R.string.equalizer),
                    stringResource(R.string.eq_desc_short), tertiaryColor) {
                    currentPage = SettingsPage.Equalizer
                },
                SettingsItem(Icons.Rounded.Palette, stringResource(R.string.appearance),
                    stringResource(R.string.appearance_desc_short), secondaryColor) {
                    currentPage = SettingsPage.Appearance
                },
                SettingsItem(Icons.Rounded.Lyrics, stringResource(R.string.lyrics),
                    stringResource(R.string.lyrics_desc_short), secondaryColor) {
                    currentPage = SettingsPage.Lyrics
                },
                SettingsItem(Icons.Rounded.FilterList, stringResource(R.string.content),
                    stringResource(R.string.content_language), primaryColor) {
                    currentPage = SettingsPage.Content
                },
            ),
        ),
        SettingsGroup(
            title = stringResource(R.string.integrations),
            items = listOf(
                SettingsItem(Icons.Rounded.Sync, stringResource(R.string.services),
                    stringResource(R.string.services_desc_short), secondaryColor) {
                    currentPage = SettingsPage.Services
                },
                SettingsItem(Icons.Rounded.Lock, stringResource(R.string.privacy),
                    stringResource(R.string.privacy_desc_short), primaryColor) {
                    currentPage = SettingsPage.Privacy
                },
                SettingsItem(Icons.Rounded.Wifi, stringResource(R.string.network),
                    stringResource(R.string.network_desc_short), tertiaryColor) {
                    currentPage = SettingsPage.Network
                },
            ),
        ),
        SettingsGroup(
            title = stringResource(R.string.about),
            items = listOf(
                SettingsItem(Icons.Rounded.Info, stringResource(R.string.about),
                    stringResource(R.string.about_desc_short), secondaryColor) {
                    currentPage = SettingsPage.About
                },
            ),
        ),
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = rootListState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            groups.forEach { group ->
                item(key = "group_${group.title}") {
                    SettingsGroupSection(group = group)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupSection(group: SettingsGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = group.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(
                MaterialTheme.typography.labelSmall.letterSpacing.value * 1.2f,
                MaterialTheme.typography.labelSmall.letterSpacing.type,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        group.items.forEachIndexed { index, item ->
            SettingsSegmentItem(item = item, index = index, count = group.items.size)
        }
    }
}

@Composable
private fun SettingsSegmentItem(
    item: SettingsItem,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (item.accentColor != Color.Unspecified) item.accentColor
    else MaterialTheme.colorScheme.primary

    val shape = segmentShape(index, count)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "scale_$index",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = item.onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = item.icon, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = item.subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp))
        }
    }
}

private fun segmentShape(index: Int, count: Int): Shape {
    val large = 24.dp
    val small = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
        index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
        else -> RoundedCornerShape(small)
    }
}

@Composable
private fun SettingsDetailPage(
    page: SettingsPage,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(text = pageTitle(page), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp),
        ) {
            when (page) {
                SettingsPage.Account    -> accountItems(onNavigateToLogin)
                SettingsPage.Playback   -> playbackItems()
                SettingsPage.Equalizer  -> {}
                SettingsPage.Appearance -> appearanceItems()
                SettingsPage.Lyrics     -> lyricsItems()
                SettingsPage.Content    -> contentItems()
                SettingsPage.Services   -> servicesItems()
                SettingsPage.Privacy    -> privacyItems()
                SettingsPage.Network    -> networkItems()
                SettingsPage.About      -> {}
                SettingsPage.None       -> {}
            }
        }
    }
}

@Composable
private fun pageTitle(page: SettingsPage) = when (page) {
    SettingsPage.Account    -> stringResource(R.string.account)
    SettingsPage.Playback   -> stringResource(R.string.playback)
    SettingsPage.Equalizer  -> stringResource(R.string.equalizer)
    SettingsPage.Appearance -> stringResource(R.string.appearance)
    SettingsPage.Lyrics     -> stringResource(R.string.lyrics)
    SettingsPage.Content    -> stringResource(R.string.content)
    SettingsPage.Services   -> stringResource(R.string.services)
    SettingsPage.Privacy    -> stringResource(R.string.privacy)
    SettingsPage.Network    -> stringResource(R.string.network)
    SettingsPage.About      -> stringResource(R.string.about)
    SettingsPage.None       -> ""
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountItems(onNavigateToLogin: () -> Unit) {
    item { AccountSection(onNavigateToLogin = onNavigateToLogin) }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Rounded.AccountCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = accountName.ifEmpty { stringResource(R.string.google_account) },
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    if (accountEmail.isNotEmpty())
                        Text(accountEmail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (accountHandle.isNotEmpty())
                        Text(accountHandle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            PrefSwitch(Icons.Rounded.Sync, stringResource(R.string.sync_ytm),
                stringResource(R.string.import_yt_info), YtmSyncKey, true)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showLogoutDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Logout, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                Text(text = stringResource(R.string.action_logout),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.iniciar_sesion), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Rounded.AccountCircle, contentDescription = null,
                        modifier = Modifier.size(18.dp))
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
                }) { Text(stringResource(R.string.logout_clear_data), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.logoutKeepData(context) {}
                    showLogoutDialog = false
                }) { Text(stringResource(R.string.logout_keep_data)) }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playbackItems() {
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
            PrefSwitch(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.pause_on_mute),
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
                    AudioQuality.OPUS.name to stringResource(R.string.quality_normal),
                    AudioQuality.SAAVN.name to stringResource(R.string.quality_medium),
                    AudioQuality.LOSSLESS.name to stringResource(R.string.quality_high),
                ),
                optionDescriptions = mapOf(
                    AudioQuality.OPUS.name to stringResource(R.string.quality_normal_description),
                    AudioQuality.SAAVN.name to stringResource(R.string.quality_medium_description),
                    AudioQuality.LOSSLESS.name to stringResource(R.string.quality_high_description),
                ),
                prefKey = AudioQualityKey,
                default = AudioQuality.OPUS,
            )
            PrefDropdownEnum<DownloadQuality>(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.download_quality),
                options = listOf(
                    DownloadQuality.YOUTUBE.name to stringResource(R.string.quality_normal),
                    DownloadQuality.SAAVN.name to stringResource(R.string.quality_medium),
                    DownloadQuality.LOSSLESS.name to stringResource(R.string.quality_high),
                ),
                optionDescriptions = mapOf(
                    DownloadQuality.YOUTUBE.name to stringResource(R.string.quality_normal_description),
                    DownloadQuality.SAAVN.name to stringResource(R.string.quality_medium_description),
                    DownloadQuality.LOSSLESS.name to stringResource(R.string.quality_high_description),
                ),
                prefKey = DownloadQualityKey,
                default = DownloadQuality.YOUTUBE,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appearanceItems() {
    item {
        SettingGroup(stringResource(R.string.group_theme)) {
            PrefSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.dynamic_colors),
                stringResource(R.string.dynamic_colors_desc), DynamicThemeKey, true)
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

    SettingDropdown(Icons.Rounded.Translate, stringResource(R.string.app_language),
        languageOptions, appLanguage) { appLanguage = it }
    SettingDropdown(Icons.Rounded.Language, stringResource(R.string.language_content),
        languageOptions, contentLanguage) { contentLanguage = it }
    SettingDropdown(Icons.Rounded.Public, stringResource(R.string.region_content),
        countryOptions, contentCountry) { contentCountry = it }
    Text(
        text = stringResource(R.string.restart_apply_changes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsItems() {
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

private fun androidx.compose.foundation.lazy.LazyListScope.contentItems() {
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
                stringResource(R.string.download_on_like_desc), AutoDownloadOnLikeKey, false)
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

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.cache_images), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text("$imageCacheSize MB", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
        Slider(value = imageCacheSize.toFloat(), onValueChange = { imageCacheSize = it.toInt() },
            valueRange = 128f..2048f, steps = 14, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("128 MB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2048 MB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.cache_songs), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text(if (songCacheSize == -1) stringResource(R.string.unlimited) else "$songCacheSize MB",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Slider(
            value = if (songCacheSize == -1) 5120f else songCacheSize.toFloat(),
            onValueChange = { songCacheSize = if (it >= 5120f) -1 else it.toInt() },
            valueRange = 256f..5120f, steps = 18, modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("256 MB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.unlimited), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.cache_apply_changes), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.servicesItems() {
    item {
        SettingGroup(stringResource(R.string.notifications)) {
            PrefSwitch(
                Icons.Rounded.Notifications,
                stringResource(R.string.music_recommendation_notifications),
                stringResource(R.string.music_recommendation_notifications_desc),
                MusicRecommendationNotificationsKey,
                false,
            )
            PrefSwitch(
                Icons.Rounded.MusicNote,
                stringResource(R.string.new_release_notifications),
                stringResource(R.string.new_release_notifications_desc),
                NewReleaseNotificationsKey,
                true,
            )
            PrefSwitch(
                Icons.Rounded.Favorite,
                stringResource(R.string.frida_reminder_notifications),
                stringResource(R.string.frida_reminder_notifications_desc),
                FridaReminderNotificationsKey,
                true,
            )
            if (
                BuildConfig.DEBUG &&
                BuildConfig.FLAVOR_abi == "universal" &&
                BuildConfig.FLAVOR_variant == "gms" &&
                booleanResource(R.bool.internal_notification_test_enabled)
            ) {
                InternalNotificationTestAction()
                InternalCrashlyticsTestAction()
            }
        }
    }
    item {
        SettingGroup(stringResource(R.string.group_lastfm)) { LastFmSection() }
    }
    item {
        SettingGroup(stringResource(R.string.group_listenbrainz)) { ListenBrainzSection() }
    }
    item {
        SettingGroup(stringResource(R.string.group_echobrain)) {
            PrefSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.enable_echobrain),
                stringResource(R.string.enable_echobrain_desc), EchoBrainEnabledKey, false)
        }
    }
}

@Composable
private fun InternalNotificationTestAction() {
    val context = LocalContext.current
    val types = remember { NotificationCandidateType.entries.toList() }
    val labels = remember(context) {
        context.resources.getStringArray(R.array.internal_notification_test_type_labels)
    }
    val options = remember(types, labels) {
        types.mapIndexed { index, type ->
            type.name to (labels.getOrNull(index) ?: type.name.replace('_', ' '))
        }
    }
    var selectedType by remember { mutableStateOf(NotificationCandidateType.RECOMMENDED_SONG) }
    val selectedLabel = options.first { it.first == selectedType.name }.second
    val queuedMessage = stringResource(R.string.internal_notification_test_queued, selectedLabel)
    val unavailableMessage = stringResource(R.string.internal_notification_test_unavailable)

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingDropdown(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.internal_notification_test_type),
            options = options,
            selectedKey = selectedType.name,
            onSelect = { value -> selectedType = NotificationCandidateType.valueOf(value) },
        )
        Button(
            onClick = {
                val queued = RecommendationNotificationScheduler(context)
                    .enqueueInternalTestNotification(selectedType)
                Toast.makeText(
                    context,
                    if (queued) queuedMessage else unavailableMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.Notifications, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.internal_notification_test_title),
            )
        }
        Text(
            text = stringResource(R.string.internal_notification_test_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun InternalCrashlyticsTestAction() {
    val context = LocalContext.current
    val nonFatalSentMessage = stringResource(R.string.internal_crashlytics_nonfatal_sent)
    var showFatalConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.internal_crashlytics_test_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Button(
            onClick = {
                CrashReporter.recordNonFatal(
                    RuntimeException("FridaMusic Crashlytics non-fatal test"),
                )
                Toast.makeText(context, nonFatalSentMessage, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.BugReport, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.internal_crashlytics_nonfatal))
        }
        OutlinedButton(
            onClick = { showFatalConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.BugReport, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.internal_crashlytics_fatal))
        }
        Text(
            text = stringResource(R.string.internal_notification_test_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showFatalConfirmation) {
        AlertDialog(
            onDismissRequest = { showFatalConfirmation = false },
            title = { Text(stringResource(R.string.internal_crashlytics_fatal_confirm_title)) },
            text = { Text(stringResource(R.string.internal_crashlytics_fatal_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        throw RuntimeException("FridaMusic Crashlytics fatal test")
                    },
                ) {
                    Text(
                        text = stringResource(R.string.internal_crashlytics_fatal_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFatalConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LastFmSection() {
    var enabled by rememberPreference(EnableLastFMScrobblingKey, false)
    var username by rememberPreference(LastFMUsernameKey, "")
    var nowPlaying by rememberPreference(LastFMUseNowPlaying, true)
    var sendLikes by rememberPreference(LastFMUseSendLikes, false)

    SettingSwitch(Icons.Rounded.Radio, stringResource(R.string.lastfm_scrobbling),
        if (username.isNotEmpty()) stringResource(R.string.lastfm_connected_as, username)
        else stringResource(R.string.lastfm_scrobbling_desc), enabled) { enabled = it }
    if (enabled) {
        Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp)) {
            SettingTextField(stringResource(R.string.username), stringResource(R.string.lastfm_user_hint), username) { username = it }
            SettingSwitch(Icons.Rounded.Podcasts, "Now Playing",
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

    SettingSwitch(Icons.Rounded.MusicNote, stringResource(R.string.group_listenbrainz),
        if (token.isNotEmpty()) stringResource(R.string.token_configured)
        else stringResource(R.string.listenbrainz_desc), enabled) { enabled = it }
    if (enabled) {
        Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp)) {
            SettingTextField(stringResource(R.string.token), stringResource(R.string.token_hint),
                token, isPassword = true) { token = it }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.privacyItems() {
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

private fun androidx.compose.foundation.lazy.LazyListScope.networkItems() {
    item { SettingGroup(stringResource(R.string.group_proxy)) { ProxySettings() } }
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
            Text(stringResource(R.string.proxy_restart_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            SettingDropdown(Icons.Rounded.SettingsEthernet, stringResource(R.string.proxy_type),
                listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS"), type.name) { type = Proxy.Type.valueOf(it) }
            SettingTextField(stringResource(R.string.server), stringResource(R.string.server_hint), url) { url = it }
            SettingTextField(stringResource(R.string.user_optional), stringResource(R.string.username), username) { username = it }
            SettingTextField(stringResource(R.string.password_optional), stringResource(R.string.password),
                password, isPassword = true) { password = it }
        }
    }
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
        Text(text = stringResource(R.string.lyrics_order_desc), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Spacer(modifier = Modifier.height(4.dp))
        currentOrder.forEachIndexed { index, name ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Text(LyricsProviderRegistry.getDisplayName(name), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                if (index > 0) {
                    IconButton(onClick = {
                        val list = currentOrder.toMutableList()
                        val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
                        orderString = list.joinToString(",")
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.action_up),
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (index < currentOrder.size - 1) {
                    IconButton(onClick = {
                        val list = currentOrder.toMutableList()
                        val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
                        orderString = list.joinToString(",")
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.action_down),
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
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
    optionDescriptions: Map<String, String> = emptyMap(),
    prefKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    default: E,
) {
    var value by rememberEnumPreference<E>(prefKey, default)
    SettingDropdown(icon, title, options, value.name, optionDescriptions) { value = enumValueOf(it) }
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
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
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
    optionDescriptions: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.find { it.first == selectedKey }?.second ?: selectedKey
    val selectedDescription = optionDescriptions[selectedKey]

    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            selectedDescription?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            Icon(Icons.Rounded.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, lbl) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(lbl)
                                optionDescriptions[key]?.let { description ->
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

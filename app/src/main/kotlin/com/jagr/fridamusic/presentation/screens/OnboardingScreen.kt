package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.AudioNormalizationKey
import com.jagr.fridamusic.constants.AudioOffload
import com.jagr.fridamusic.constants.AudioQuality
import com.jagr.fridamusic.constants.AudioQualityKey
import com.jagr.fridamusic.constants.DownloadQuality
import com.jagr.fridamusic.constants.DownloadQualityKey
import com.jagr.fridamusic.constants.EchoBrainEnabledKey
import com.jagr.fridamusic.constants.EnableLastFMScrobblingKey
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.PauseListenHistoryKey
import com.jagr.fridamusic.constants.PauseSearchHistoryKey
import com.jagr.fridamusic.constants.SkipSilenceKey
import com.jagr.fridamusic.constants.SponsorBlockEnabledKey
import com.jagr.fridamusic.presentation.theme.EchoLavender
import com.jagr.fridamusic.presentation.theme.FridaPurple
import com.jagr.fridamusic.utils.rememberEnumPreference
import com.jagr.fridamusic.utils.rememberPreference
import com.jagr.fridamusic.viewmodels.SetupStep
import com.jagr.fridamusic.viewmodels.SetupViewModel

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsState()
    val isFirst = step == SetupStep.WELCOME
    val isLast = step == SetupStep.DONE

    BackHandler(enabled = !isFirst, onBack = viewModel::back)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            FridaPurple.copy(alpha = 0.12f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isFirst) {
                    IconButton(onClick = viewModel::back) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SetupStep.entries.forEachIndexed { index, s ->
                        val active = s == step
                        val passed = index < SetupStep.entries.indexOf(step)
                        Box(
                            modifier = Modifier
                                .size(if (active) 24.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        active -> EchoLavender
                                        passed -> EchoLavender.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(300)) { if (forward) it else -it } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(300)) { if (forward) -it else it } + fadeOut(tween(200)))
                },
                label = "setup_step",
            ) { currentStep ->
                when (currentStep) {
                    SetupStep.WELCOME -> WelcomeStep()
                    SetupStep.QUALITY -> QualityStep()
                    SetupStep.PRIVACY -> PrivacyStep()
                    SetupStep.SERVICES -> ServicesStep()
                    SetupStep.LIBRARY -> LibraryStep()
                    SetupStep.FEATURES -> FeaturesStep()
                    SetupStep.DONE -> DoneStep()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Button(
                    onClick = {
                        if (isLast) {
                            viewModel.complete()
                            onFinish()
                        } else {
                            viewModel.next()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EchoLavender,
                        contentColor = Color(0xFF050510),
                    ),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(
                        text = if (isLast) stringResource(R.string.get_started) else stringResource(R.string.onboarding_continue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(R.drawable.fridamusic_onboarding_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        Spacer(modifier = Modifier.height(48.dp))

        val context = LocalContext.current
        val hasAudio = remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                else
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            )
        }
        val hasNotif = remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                else true
            )
        }
        val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAudio.value = it }
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotif.value = it }

        PermissionTile(
            icon = Icons.Rounded.LibraryMusic,
            label = stringResource(R.string.perm_audio_label),
            granted = hasAudio.value,
            onRequest = {
                audioLauncher.launch(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Manifest.permission.READ_MEDIA_AUDIO
                    else
                        Manifest.permission.READ_EXTERNAL_STORAGE
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PermissionTile(
            icon = Icons.Rounded.Notifications,
            label = stringResource(R.string.perm_notif_label),
            granted = hasNotif.value,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_branding),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QualityStep() {
    var audioQuality by rememberEnumPreference<AudioQuality>(AudioQualityKey, AudioQuality.OPUS)
    var downloadQuality by rememberEnumPreference<DownloadQuality>(DownloadQualityKey, DownloadQuality.YOUTUBE)

    var audioNorm by rememberPreference(AudioNormalizationKey, false)
    var skipSilence by rememberPreference(SkipSilenceKey, false)
    var audioOffload by rememberPreference(AudioOffload, false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(
            icon = Icons.Rounded.HighQuality,
            title = stringResource(R.string.onboarding_quality_title),
            subtitle = stringResource(R.string.onboarding_quality_subtitle),
        )

        SetupSectionLabel(stringResource(R.string.streaming_quality))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AudioQuality.values().forEach { q ->
                QualityPill(
                    label = when (q) {
                        AudioQuality.OPUS -> stringResource(R.string.quality_normal)
                        AudioQuality.SAAVN -> stringResource(R.string.quality_medium)
                        AudioQuality.LOSSLESS -> stringResource(R.string.quality_high)
                    },
                    selected = audioQuality == q,
                    modifier = Modifier.weight(1f),
                    onClick = { audioQuality = q },
                )
            }
        }
        Text(
            text = when (audioQuality) {
                AudioQuality.OPUS -> stringResource(R.string.quality_normal_description)
                AudioQuality.SAAVN -> stringResource(R.string.quality_medium_description)
                AudioQuality.LOSSLESS -> stringResource(R.string.quality_high_description)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))
        SetupSectionLabel(stringResource(R.string.download_quality))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DownloadQuality.values().forEach { q ->
                QualityPill(
                    label = when (q) {
                        DownloadQuality.YOUTUBE -> stringResource(R.string.quality_normal)
                        DownloadQuality.SAAVN -> stringResource(R.string.quality_medium)
                        DownloadQuality.LOSSLESS -> stringResource(R.string.quality_high)
                    },
                    selected = downloadQuality == q,
                    modifier = Modifier.weight(1f),
                    onClick = { downloadQuality = q },
                )
            }
        }
        Text(
            text = when (downloadQuality) {
                DownloadQuality.YOUTUBE -> stringResource(R.string.quality_normal_description)
                DownloadQuality.SAAVN -> stringResource(R.string.quality_medium_description)
                DownloadQuality.LOSSLESS -> stringResource(R.string.quality_high_description)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))
        SetupSectionLabel(stringResource(R.string.onboarding_extras_label))
        SetupToggle(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.audio_normalization),
            subtitle = stringResource(R.string.audio_normalization_desc),
            checked = audioNorm,
            onCheckedChange = { audioNorm = it },
        )
        SetupToggle(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.skip_silence),
            subtitle = stringResource(R.string.skip_silence_desc),
            checked = skipSilence,
            onCheckedChange = { skipSilence = it },
        )
        SetupToggle(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.audio_offload),
            subtitle = stringResource(R.string.audio_offload_desc),
            checked = audioOffload,
            onCheckedChange = { audioOffload = it },
        )
    }
}

@Composable
private fun PrivacyStep() {
    var pauseHistory by rememberPreference(PauseListenHistoryKey, false)
    var pauseSearch by rememberPreference(PauseSearchHistoryKey, false)
    var hideExplicit by rememberPreference(HideExplicitKey, false)

    Column(modifier = Modifier.fillMaxSize()) {
        StepTitle(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.onboarding_privacy_title),
            subtitle = stringResource(R.string.onboarding_privacy_subtitle),
        )
        SetupToggle(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.pause_listen_history),
            subtitle = stringResource(R.string.pause_listen_history_desc),
            checked = pauseHistory,
            onCheckedChange = { pauseHistory = it },
        )
        SetupToggle(
            icon = Icons.Rounded.Search,
            title = stringResource(R.string.pause_search_history),
            subtitle = stringResource(R.string.pause_search_history_desc),
            checked = pauseSearch,
            onCheckedChange = { pauseSearch = it },
        )
        SetupToggle(
            icon = Icons.Rounded.Block,
            title = stringResource(R.string.hide_explicit),
            subtitle = stringResource(R.string.hide_explicit_desc),
            checked = hideExplicit,
            onCheckedChange = { hideExplicit = it },
        )
    }
}

@Composable
private fun ServicesStep() {
    var lastFm by rememberPreference(EnableLastFMScrobblingKey, false)
    var sponsorBlock by rememberPreference(SponsorBlockEnabledKey, false)
    var echoBrain by rememberPreference(EchoBrainEnabledKey, false)

    Column(modifier = Modifier.fillMaxSize()) {
        StepTitle(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.onboarding_services_title),
            subtitle = stringResource(R.string.onboarding_services_subtitle),
        )
        SetupToggle(
            icon = Icons.Rounded.MusicNote,
            title = stringResource(R.string.lastfm_scrobbling),
            subtitle = stringResource(R.string.lastfm_scrobbling_desc),
            checked = lastFm,
            onCheckedChange = { lastFm = it },
        )
        SetupToggle(
            icon = Icons.Rounded.Block,
            title = stringResource(R.string.sponsorblock),
            subtitle = stringResource(R.string.enable_sponsorblock_desc),
            checked = sponsorBlock,
            onCheckedChange = { sponsorBlock = it },
        )
        SetupToggle(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.enable_echobrain),
            subtitle = stringResource(R.string.enable_echobrain_desc),
            checked = echoBrain,
            onCheckedChange = { echoBrain = it },
        )
    }
}

@Composable
private fun LibraryStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.onboarding_library_title),
            subtitle = stringResource(R.string.onboarding_library_subtitle),
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_library_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
                DiscoverFeatureRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.onboarding_discover_library_title),
                    subtitle = stringResource(R.string.onboarding_discover_library_desc),
                )
                DiscoverFeatureRow(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.onboarding_discover_experience_title),
                    subtitle = stringResource(R.string.onboarding_discover_experience_desc),
                )
                DiscoverFeatureRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.onboarding_discover_control_title),
                    subtitle = stringResource(R.string.onboarding_discover_control_desc),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_discover_settings_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_discover_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeaturesStep() {
    val context = LocalContext.current
    var hasMicrophone by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophone = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(
            icon = Icons.Rounded.Home,
            title = stringResource(R.string.onboarding_features_title),
            subtitle = stringResource(R.string.onboarding_features_subtitle),
        )

        SetupSectionLabel(stringResource(R.string.onboarding_features_home_section))
        FeatureGuideRow(
            icon = Icons.Rounded.VolunteerActivism,
            title = stringResource(R.string.onboarding_features_support_title),
            description = stringResource(R.string.onboarding_features_support_desc),
            emphasized = true,
        )
        FeatureGuideRow(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.history),
            description = stringResource(R.string.onboarding_features_history_desc),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.fridamusic_recap),
            description = stringResource(R.string.onboarding_features_recap_desc),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.NotificationsNone,
            title = stringResource(R.string.notifications),
            description = stringResource(R.string.onboarding_features_notifications_desc),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.settings),
            description = stringResource(R.string.onboarding_features_settings_desc),
        )

        Spacer(modifier = Modifier.height(20.dp))
        SetupSectionLabel(stringResource(R.string.onboarding_features_navigation_section))
        FeatureGuideRow(
            icon = Icons.Rounded.Home,
            title = stringResource(R.string.home),
            description = stringResource(R.string.onboarding_features_home_desc),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.Search,
            title = stringResource(R.string.search),
            description = stringResource(R.string.onboarding_features_search_desc),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.LibraryMusic,
            title = stringResource(R.string.onboarding_features_library_title),
            description = stringResource(R.string.onboarding_features_library_desc),
        )

        Spacer(modifier = Modifier.height(20.dp))
        SetupSectionLabel(stringResource(R.string.onboarding_features_fab_section))
        Text(
            text = stringResource(R.string.onboarding_features_fab_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.Shuffle,
            title = stringResource(R.string.shuffle),
            description = stringResource(R.string.onboarding_features_shuffle_desc),
        )
        FeatureGuideRow(
            icon = Icons.Rounded.Mic,
            title = stringResource(R.string.onboarding_features_recognition_title),
            description = stringResource(R.string.onboarding_features_recognition_desc),
            badge = stringResource(R.string.onboarding_features_recognition_powered_by),
            footer = {
                if (hasMicrophone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = EchoLavender,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.granted),
                            style = MaterialTheme.typography.labelMedium,
                            color = EchoLavender,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    ) {
                        Text(stringResource(R.string.onboarding_features_allow_microphone))
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureGuideRow(
    icon: ImageVector,
    title: String,
    description: String,
    emphasized: Boolean = false,
    badge: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (emphasized) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (emphasized) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    lineHeight = 18.sp,
                )
                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                footer?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    it()
                }
            }
        }
    }
}

@Composable
private fun DiscoverFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(FridaPurple, EchoLavender))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_done_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun StepTitle(icon: ImageVector, title: String, subtitle: String) {
    Spacer(modifier = Modifier.height(24.dp))
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = EchoLavender,
        modifier = Modifier.size(40.dp),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(28.dp))
}

@Composable
private fun SetupSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = EchoLavender,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun QualityPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) EchoLavender
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF050510) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionTile(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) EchoLavender else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (granted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.granted),
                    tint = EchoLavender,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                FilledTonalButton(onClick = onRequest) { Text(stringResource(R.string.grant)) }
            }
        }
    }
}

@Composable
private fun SetupToggle(
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (checked) EchoLavender.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) EchoLavender else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF050510),
                checkedTrackColor = EchoLavender,
            ),
        )
    }
}

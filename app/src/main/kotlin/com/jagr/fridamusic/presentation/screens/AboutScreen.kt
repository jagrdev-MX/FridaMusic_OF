package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.BuildConfig
import com.jagr.fridamusic.R

private data class Developer(
    val name: String,
    val role: String,
    val githubUrl: String,
    val githubHandle: String,
    val initials: String,
    val gradientColors: List<Color>,
)

private data class OpenSourceLib(
    val name: String,
    val license: String,
    val url: String,
)

private val developers = listOf(
    Developer(
        name = "JAGR",
        role = "Lead Developer",
        githubUrl = "https://github.com/jagrdev-MX",
        githubHandle = "@jagrdev-MX",
        initials = "JG",
        gradientColors = listOf(Color(0xFF0EA5E9), Color(0xFF06B6D4)),
    ),
    Developer(
        name = "Julio César",
        role = "Developer",
        githubUrl = "https://github.com/juliocps25",
        githubHandle = "@juliocps25",
        initials = "JC",
        gradientColors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
    ),
)

private val openSourceLibraries = listOf(
    OpenSourceLib("Jetpack Compose", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    OpenSourceLib("Material 3", "Apache 2.0", "https://m3.material.io"),
    OpenSourceLib("ExoPlayer / Media3", "Apache 2.0", "https://github.com/google/ExoPlayer"),
    OpenSourceLib("Hilt", "Apache 2.0", "https://dagger.dev/hilt"),
    OpenSourceLib("Room", "Apache 2.0", "https://developer.android.com/training/data-storage/room"),
    OpenSourceLib("DataStore", "Apache 2.0", "https://developer.android.com/topic/libraries/architecture/datastore"),
    OpenSourceLib("Coil", "Apache 2.0", "https://github.com/coil-kt/coil"),
    OpenSourceLib("Ktor", "Apache 2.0", "https://ktor.io"),
    OpenSourceLib("Retrofit", "Apache 2.0", "https://square.github.io/retrofit"),
    OpenSourceLib("OkHttp", "Apache 2.0", "https://square.github.io/okhttp"),
    OpenSourceLib("Kotlin Coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceLib("Kotlin Serialization", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceLib("Protobuf", "BSD 3-Clause", "https://github.com/protocolbuffers/protobuf"),
    OpenSourceLib("Timber", "Apache 2.0", "https://github.com/JakeWharton/timber"),
    OpenSourceLib("Haze", "Apache 2.0", "https://github.com/chrisbanes/haze"),
    OpenSourceLib("MaterialKolor", "Apache 2.0", "https://github.com/jordond/materialkolor"),
    OpenSourceLib("Lottie", "MIT", "https://github.com/airbnb/lottie-android"),
    OpenSourceLib("Jsoup", "MIT", "https://jsoup.org"),
    OpenSourceLib("uCrop", "Apache 2.0", "https://github.com/Yalantis/uCrop"),
    OpenSourceLib("Apache Commons Lang3", "Apache 2.0", "https://commons.apache.org/proper/commons-lang"),
    OpenSourceLib("Guava", "Apache 2.0", "https://github.com/google/guava"),
    OpenSourceLib("NewPipeExtractor", "GPL-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    OpenSourceLib("Kuromoji", "Apache 2.0", "https://github.com/atilika/kuromoji"),
    OpenSourceLib("TinyPinyin", "Apache 2.0", "https://github.com/promeG/TinyPinyin"),
    OpenSourceLib("FFmpegKit", "LGPL-3.0", "https://github.com/arthenica/ffmpeg-kit"),
    OpenSourceLib("Shimmer", "BSD", "https://github.com/facebookarchive/shimmer-android"),
    OpenSourceLib("Compose Reorderable", "Apache 2.0", "https://github.com/Calvin-LL/Reorderable"),
    OpenSourceLib("SmoothCorner", "MIT", "https://github.com/racra/smooth-corner-rect-android-compose"),
    OpenSourceLib("EchoMusic / ViMusic", "GPL-3.0", "https://github.com/vfsfitvnm/ViMusic"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppHeader()

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.about_team)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.about_licenses)) })
            }

            when (selectedTab) {
                0 -> TeamTab(onOpenUrl = { uriHandler.openUri(it) })
                1 -> LicensesTab(onOpenUrl = { uriHandler.openUri(it) })
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("F", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Text(
            text = "FridaMusic",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun TeamTab(onOpenUrl: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.about_made_with_love),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(developers) { dev ->
            DeveloperCard(dev = dev, onOpenGithub = { onOpenUrl(dev.githubUrl) })
        }
        item {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = null,
                        tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.about_fork_credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperCard(dev: Developer, onOpenGithub: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenGithub() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(dev.gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dev.initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dev.name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(text = dev.role, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = dev.githubHandle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                Icons.Rounded.Code, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LicensesTab(onOpenUrl: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.about_licenses_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
            ) {
                openSourceLibraries.forEachIndexed { index, lib ->
                    LicenseRow(lib = lib, onClick = { onOpenUrl(lib.url) })
                    if (index < openSourceLibraries.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(lib: OpenSourceLib, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = lib.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = lib.license, style = MaterialTheme.typography.labelSmall,
                color = licenseColor(lib.license))
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun licenseColor(license: String): Color = when {
    license.startsWith("Apache") -> Color(0xFF22C55E)
    license.startsWith("MIT") -> Color(0xFF3B82F6)
    license.startsWith("GPL") -> Color(0xFFF97316)
    license.startsWith("LGPL") -> Color(0xFFF59E0B)
    license.startsWith("BSD") -> Color(0xFF8B5CF6)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.constants.*
import com.jagr.fridamusic.lyrics.LyricsProviderRegistry
import com.jagr.fridamusic.utils.rememberEnumPreference
import com.jagr.fridamusic.utils.rememberPreference
import com.jagr.fridamusic.viewmodels.AccountSettingsViewModel
import java.net.Proxy

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    ACCOUNT   ("Cuenta",       Icons.Rounded.AccountCircle),
    PLAYBACK  ("Reproducción", Icons.Rounded.PlayCircle),
    APPEARANCE("Apariencia",   Icons.Rounded.Palette),
    LYRICS    ("Letras",       Icons.Rounded.Lyrics),
    CONTENT   ("Contenido",    Icons.Rounded.FilterList),
    SERVICES  ("Servicios",    Icons.Rounded.Sync),
    PRIVACY   ("Privacidad",   Icons.Rounded.Lock),
    NETWORK   ("Red",          Icons.Rounded.Wifi),
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
) {
    var activeTab by remember { mutableStateOf(SettingsTab.PLAYBACK) }

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
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        ScrollableTabRow(
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
                            text = tab.label,
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

private fun androidx.compose.foundation.lazy.LazyListScope.accountItems(
    onNavigateToLogin: () -> Unit,
) {
    item {
        AccountSection(onNavigateToLogin = onNavigateToLogin)
    }
}

@Composable
private fun AccountSection(
    onNavigateToLogin: () -> Unit,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountHandle by rememberPreference(AccountChannelHandleKey, "")
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = cookie.isNotEmpty() && "SAPISID" in cookie

    var showLogoutDialog by remember { mutableStateOf(false) }

    SettingGroup("YouTube Music") {
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
                        text = accountName.ifEmpty { "Cuenta de Google" },
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
                title = "Sincronizar biblioteca con YTM",
                subtitle = "Importa tus likes, álbumes y playlists de YouTube Music",
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
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Cerrar sesión",
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
                    text = "Iniciá sesión para acceder a tu biblioteca personal de YouTube Music, incluyendo likes, álbumes guardados y playlists.",
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
                    Text("Iniciar sesión con Google")
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Qué querés hacer con la música sincronizada de tu biblioteca?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logoutAndClearSyncedContent {}
                    showLogoutDialog = false
                }) {
                    Text("Cerrar sesión y borrar datos", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.logoutKeepData {}
                    showLogoutDialog = false
                }) {
                    Text("Cerrar sesión, conservar datos")
                }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playbackItems() {
    item {
        SettingGroup("General") {
            PrefSwitch(Icons.AutoMirrored.Rounded.QueueMusic, "Cola persistente",
                "Recuerda la cola al cerrar la app", PersistentQueueKey, true)
            PrefSwitch(Icons.Rounded.SkipNext, "Saltar si hay error",
                "Pasa a la siguiente canción si no puede reproducir", AutoSkipNextOnErrorKey, false)
            PrefSwitch(Icons.Rounded.FastForward, "Precargar siguiente canción",
                "Descarga en caché la siguiente canción antes de que empiece", PreloadNextSongEnabledKey, true)
            PrefSwitch(Icons.Rounded.WbSunny, "Mantener pantalla activa",
                "Evita que la pantalla se apague mientras suena música", KeepScreenOn, false)
            PrefSwitch(Icons.Rounded.Stop, "Detener al cerrar la app",
                "Para la música al limpiar la app del reciente", StopMusicOnTaskClearKey, false)
        }
    }
    item {
        SettingGroup("Audio") {
            PrefSwitch(Icons.AutoMirrored.Rounded.VolumeOff, "Omitir silencios",
                "Salta partes silenciosas automáticamente", SkipSilenceKey, false)
            PrefSwitch(Icons.Rounded.Equalizer, "Normalización de volumen",
                "Iguala el volumen entre canciones", AudioNormalizationKey, true)
            PrefSwitch(Icons.Rounded.Tune, "Crossfade entre canciones",
                "Transición suave al cambiar de canción", CrossfadeEnabledKey, false)
            PrefSwitch(Icons.AutoMirrored.Rounded.VolumeOff, "Pausar al silenciar",
                "Pausa la música cuando el volumen llega a 0", PauseOnMute, false)
            PrefSwitch(Icons.Rounded.BatteryChargingFull, "Audio offload",
                "Ahorra batería delegando la reproducción al hardware", AudioOffload, false)
        }
    }
    item {
        SettingGroup("Calidad") {
            PrefDropdownEnum<AudioQuality>(
                icon = Icons.Rounded.GraphicEq,
                title = "Calidad de streaming",
                options = listOf(
                    AudioQuality.OPUS.name    to "Opus (recomendado)",
                    AudioQuality.SAAVN.name   to "Saavn",
                    AudioQuality.LOSSLESS.name to "Sin pérdida",
                ),
                prefKey = AudioQualityKey,
                default = AudioQuality.OPUS,
            )
            PrefDropdownEnum<DownloadQuality>(
                icon = Icons.Rounded.Download,
                title = "Calidad de descarga",
                options = listOf(
                    DownloadQuality.YOUTUBE.name  to "YouTube",
                    DownloadQuality.SAAVN.name    to "Saavn",
                    DownloadQuality.LOSSLESS.name to "Sin pérdida",
                ),
                prefKey = DownloadQualityKey,
                default = DownloadQuality.YOUTUBE,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appearanceItems() {
    item {
        SettingGroup("Tema") {
            PrefDropdownString(
                icon = Icons.Rounded.DarkMode,
                title = "Modo de color",
                options = listOf(
                    "SYSTEM_DEFAULT" to "Según el sistema",
                    "ON"             to "Oscuro",
                    "OFF"            to "Claro",
                ),
                prefKey = DarkModeKey,
                default = "SYSTEM_DEFAULT",
            )
            PrefSwitch(Icons.Rounded.AutoAwesome, "Colores dinámicos",
                "Adapta los colores al artwork de la canción (Material You)", DynamicThemeKey, true)
            PrefSwitch(Icons.Rounded.Contrast, "Negro puro (AMOLED)",
                "Usa negro absoluto en el fondo para pantallas AMOLED", PureBlackKey, false)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsItems() {
    item {
        SettingGroup("General") {
            PrefSwitch(Icons.Rounded.Lyrics, "Buscar letras automáticamente",
                "Descarga letras al reproducir una canción", ShowLyricsKey, false)
            PrefSwitch(Icons.Rounded.Cached, "Precargar letras",
                "Descarga las letras antes de que empiece la canción", PreloadLyricsEnabledKey, true)
        }
    }
    item {
        SettingGroup("Proveedores") {
            LyricsProviderOrderSection()
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contentItems() {
    item {
        SettingGroup("Filtros") {
            PrefSwitch(Icons.Rounded.Block, "Ocultar contenido explícito",
                "Filtra canciones marcadas como explícitas", HideExplicitKey, false)
            PrefSwitch(Icons.Rounded.VideocamOff, "Ocultar canciones de video",
                "Filtra canciones provenientes de videos de YouTube", HideVideoSongsKey, false)
            PrefSwitch(Icons.Rounded.VideoLibrary, "Ocultar YouTube Shorts",
                "No incluye Shorts en los resultados", HideYoutubeShortsKey, false)
        }
    }
    item {
        SettingGroup("SponsorBlock") {
            PrefSwitch(Icons.Rounded.Block, "Activar SponsorBlock",
                "Salta segmentos de patrocinadores automáticamente", SponsorBlockEnabledKey, false)
        }
    }
    item {
        SettingGroup("Descargas") {
            PrefSwitch(Icons.Rounded.FavoriteBorder, "Descargar al dar like",
                "Descarga automáticamente la canción cuando la agregas a favoritos",
                AutoDownloadOnLikeKey, false)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.servicesItems() {
    item {
        SettingGroup("Last.fm") {
            LastFmSection()
        }
    }
    item {
        SettingGroup("ListenBrainz") {
            ListenBrainzSection()
        }
    }
    item {
        SettingGroup("Discord") {
            PrefSwitch(Icons.AutoMirrored.Rounded.Chat, "Discord Rich Presence",
                "Muestra la canción actual en tu estado de Discord", EnableDiscordRPCKey, true)
        }
    }
    item {
        SettingGroup("FridaMusic IA") {
            PrefSwitch(Icons.Rounded.AutoAwesome, "Habilitar EchoBrain",
                "Activa el asistente de IA para recomendaciones y análisis", EchoBrainEnabledKey, false)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.privacyItems() {
    item {
        SettingGroup("Historial") {
            PrefSwitch(Icons.Rounded.History, "Pausar historial de escucha",
                "No guarda canciones en el historial", PauseListenHistoryKey, false)
            PrefSwitch(Icons.AutoMirrored.Rounded.ManageSearch, "Pausar historial de búsqueda",
                "No guarda las búsquedas realizadas", PauseSearchHistoryKey, false)
        }
    }
    item {
        SettingGroup("Pantalla") {
            PrefSwitch(Icons.Rounded.Monitor, "Bloquear capturas de pantalla",
                "Impide hacer capturas dentro de la app", DisableScreenshotKey, false)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.networkItems() {
    item {
        SettingGroup("Proxy") {
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
            text = "El primer proveedor que encuentre letra sincronizada gana. Toca ↑↓ para reordenar.",
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
                            val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
                            orderString = list.joinToString(",")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Subir",
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (index < currentOrder.size - 1) {
                    IconButton(
                        onClick = {
                            val list = currentOrder.toMutableList()
                            val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
                            orderString = list.joinToString(",")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Bajar",
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LastFmSection() {
    var enabled  by rememberPreference(EnableLastFMScrobblingKey, false)
    var username by rememberPreference(LastFMUsernameKey, "")
    var nowPlaying by rememberPreference(LastFMUseNowPlaying, true)
    var sendLikes  by rememberPreference(LastFMUseSendLikes, false)

    SettingSwitch(
        icon = Icons.Rounded.Radio,
        title = "Scrobbling en Last.fm",
        subtitle = if (username.isNotEmpty()) "Conectado como $username" else "Registra las canciones que escuchas en Last.fm",
        checked = enabled,
        onCheckedChange = { enabled = it },
    )
    if (enabled) {
        Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp)) {
            SettingTextField("Usuario", "Tu nombre de usuario en Last.fm", username) { username = it }
            SettingSwitch(Icons.Rounded.Podcasts, "Now Playing",
                "Muestra la canción actual como 'escuchando ahora'", nowPlaying) { nowPlaying = it }
            SettingSwitch(Icons.Rounded.Favorite, "Sincronizar likes",
                "Manda un ♥ en Last.fm cuando das like en FridaMusic", sendLikes) { sendLikes = it }
        }
    }
}

@Composable
private fun ListenBrainzSection() {
    var enabled by rememberPreference(ListenBrainzEnabledKey, false)
    var token   by rememberPreference(ListenBrainzTokenKey, "")

    SettingSwitch(
        icon = Icons.Rounded.MusicNote,
        title = "ListenBrainz",
        subtitle = if (token.isNotEmpty()) "Token configurado" else "Envía scrobbles a ListenBrainz",
        checked = enabled,
        onCheckedChange = { enabled = it },
    )
    if (enabled) {
        Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp)) {
            SettingTextField("Token", "Pega tu token de ListenBrainz", token, isPassword = true) { token = it }
            Text(
                text = "Obtén tu token en listenbrainz.org/profile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}


@Composable
private fun ProxySettings() {
    var enabled  by rememberPreference(ProxyEnabledKey, false)
    var url      by rememberPreference(ProxyUrlKey, "")
    var type     by rememberEnumPreference<Proxy.Type>(ProxyTypeKey, Proxy.Type.HTTP)
    var username by rememberPreference(ProxyUsernameKey, "")
    var password by rememberPreference(ProxyPasswordKey, "")

    SettingSwitch(Icons.Rounded.Key, "Usar proxy",
        "Enruta el tráfico a través de un servidor proxy", enabled) { enabled = it }

    if (enabled) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = "Los cambios se aplican al reiniciar la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingDropdown(
                icon = Icons.Rounded.SettingsEthernet,
                title = "Tipo",
                options = listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS"),
                selectedKey = type.name,
                onSelect = { type = Proxy.Type.valueOf(it) },
            )
            SettingTextField("Servidor", "host:puerto", url) { url = it }
            SettingTextField("Usuario (opcional)", "Usuario", username) { username = it }
            SettingTextField("Contraseña (opcional)", "Contraseña", password, isPassword = true) { password = it }
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
        visualTransformation = if (isPassword) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
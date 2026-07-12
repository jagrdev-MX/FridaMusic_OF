package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jagr.fridamusic.constants.*
import com.jagr.fridamusic.utils.rememberEnumPreference
import com.jagr.fridamusic.utils.rememberPreference
import java.net.Proxy

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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

        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {

            item { SectionHeader("Reproducción", Icons.Rounded.PlayCircle) }
            item {
                var skipSilence by rememberPreference(SkipSilenceKey, false)
                SettingSwitch(icon = Icons.AutoMirrored.Rounded.VolumeOff, title = "Omitir silencios", subtitle = "Salta partes silenciosas automáticamente", checked = skipSilence, onCheckedChange = { skipSilence = it })
            }
            item {
                var audioNorm by rememberPreference(AudioNormalizationKey, true)
                SettingSwitch(icon = Icons.Rounded.Equalizer, title = "Normalización de volumen", subtitle = "Iguala el volumen entre canciones", checked = audioNorm, onCheckedChange = { audioNorm = it })
            }
            item {
                var persistentQueue by rememberPreference(PersistentQueueKey, true)
                SettingSwitch(icon = Icons.AutoMirrored.Rounded.QueueMusic, title = "Cola persistente", subtitle = "Recuerda la cola al cerrar la app", checked = persistentQueue, onCheckedChange = { persistentQueue = it })
            }
            item {
                var autoSkip by rememberPreference(AutoSkipNextOnErrorKey, false)
                SettingSwitch(icon = Icons.Rounded.SkipNext, title = "Saltar si hay error", subtitle = "Pasa a la siguiente canción si no puede reproducir", checked = autoSkip, onCheckedChange = { autoSkip = it })
            }
            item {
                var crossfade by rememberPreference(CrossfadeEnabledKey, false)
                SettingSwitch(icon = Icons.Rounded.Tune, title = "Crossfade entre canciones", subtitle = "Transición suave al cambiar de canción", checked = crossfade, onCheckedChange = { crossfade = it })
            }
            item {
                var pauseOnMute by rememberPreference(PauseOnMute, false)
                SettingSwitch(icon = Icons.AutoMirrored.Rounded.VolumeOff, title = "Pausar al silenciar", subtitle = "Pausa la música cuando el volumen llega a 0", checked = pauseOnMute, onCheckedChange = { pauseOnMute = it })
            }
            item {
                var stopOnTaskClear by rememberPreference(StopMusicOnTaskClearKey, false)
                SettingSwitch(icon = Icons.Rounded.Stop, title = "Detener al cerrar la app", subtitle = "Para la música al limpiar la app del reciente", checked = stopOnTaskClear, onCheckedChange = { stopOnTaskClear = it })
            }
            item {
                var keepScreenOn by rememberPreference(KeepScreenOn, false)
                SettingSwitch(icon = Icons.Rounded.WbSunny, title = "Mantener pantalla activa", subtitle = "Evita que la pantalla se apague mientras suena música", checked = keepScreenOn, onCheckedChange = { keepScreenOn = it })
            }
            item {
                var preloadNext by rememberPreference(PreloadNextSongEnabledKey, true)
                SettingSwitch(icon = Icons.Rounded.FastForward, title = "Precargar siguiente canción", subtitle = "Descarga en caché la siguiente canción antes de que empiece", checked = preloadNext, onCheckedChange = { preloadNext = it })
            }

            item { SectionHeader("Calidad de audio", Icons.Rounded.HighQuality) }
            item {
                var audioQuality by rememberEnumPreference<AudioQuality>(AudioQualityKey, AudioQuality.OPUS)
                SettingDropdown(icon = Icons.Rounded.GraphicEq, title = "Calidad de streaming", options = listOf("OPUS" to "Opus"), selectedKey = audioQuality.name, onSelect = { audioQuality = AudioQuality.valueOf(it) })
            }
            item {
                var downloadQuality by rememberEnumPreference<DownloadQuality>(DownloadQualityKey, DownloadQuality.YOUTUBE)
                SettingDropdown(icon = Icons.Rounded.Download, title = "Calidad de descarga", options = listOf("YOUTUBE" to "YouTube"), selectedKey = downloadQuality.name, onSelect = { downloadQuality = DownloadQuality.valueOf(it) })
            }
            item {
                var audioOffload by rememberPreference(AudioOffload, false)
                SettingSwitch(icon = Icons.Rounded.BatteryChargingFull, title = "Audio offload", subtitle = "Ahorra batería delegando la reproducción al hardware", checked = audioOffload, onCheckedChange = { audioOffload = it })
            }

            item { SectionHeader("Letras", Icons.Rounded.Lyrics) }
            item {
                var showLyrics by rememberPreference(ShowLyricsKey, false)
                SettingSwitch(icon = Icons.Rounded.Lyrics, title = "Buscar letras automáticamente", subtitle = "Descarga letras al reproducir", checked = showLyrics, onCheckedChange = { showLyrics = it })
            }
            item {
                var kugou by rememberPreference(EnableKugouKey, true)
                SettingSwitch(icon = Icons.Rounded.MusicNote, title = "Proveedor KuGou", subtitle = "Activa la búsqueda de letras en KuGou", checked = kugou, onCheckedChange = { kugou = it })
            }
            item {
                var lrclib by rememberPreference(EnableLrcLibKey, true)
                SettingSwitch(icon = Icons.Rounded.MusicNote, title = "Proveedor LRCLib", subtitle = "Activa la búsqueda de letras en LRCLib", checked = lrclib, onCheckedChange = { lrclib = it })
            }
            item {
                var preloadLyrics by rememberPreference(PreloadLyricsEnabledKey, true)
                SettingSwitch(icon = Icons.Rounded.Cached, title = "Precargar letras", subtitle = "Descarga las letras antes de que empiece la canción", checked = preloadLyrics, onCheckedChange = { preloadLyrics = it })
            }

            item { SectionHeader("Contenido", Icons.Rounded.FilterList) }
            item {
                var hideExplicit by rememberPreference(HideExplicitKey, false)
                SettingSwitch(icon = Icons.Rounded.Block, title = "Ocultar contenido explícito", subtitle = "Filtra canciones marcadas como explícitas", checked = hideExplicit, onCheckedChange = { hideExplicit = it })
            }
            item {
                var hideVideoSongs by rememberPreference(HideVideoSongsKey, false)
                SettingSwitch(icon = Icons.Rounded.VideocamOff, title = "Ocultar canciones de video", subtitle = "Filtra canciones provenientes de videos de YouTube", checked = hideVideoSongs, onCheckedChange = { hideVideoSongs = it })
            }
            item {
                var hideShorts by rememberPreference(HideYoutubeShortsKey, false)
                SettingSwitch(icon = Icons.Rounded.VideoLibrary, title = "Ocultar YouTube Shorts", subtitle = "No incluye Shorts en los resultados", checked = hideShorts, onCheckedChange = { hideShorts = it })
            }
            item {
                var sponsorBlock by rememberPreference(SponsorBlockEnabledKey, false)
                SettingSwitch(icon = Icons.Rounded.Block, title = "SponsorBlock", subtitle = "Salta segmentos de patrocinadores automáticamente", checked = sponsorBlock, onCheckedChange = { sponsorBlock = it })
            }

            item { SectionHeader("Privacidad", Icons.Rounded.Lock) }
            item {
                var pauseHistory by rememberPreference(PauseListenHistoryKey, false)
                SettingSwitch(icon = Icons.Rounded.History, title = "Pausar historial de escucha", subtitle = "No guarda canciones en el historial", checked = pauseHistory, onCheckedChange = { pauseHistory = it })
            }
            item {
                var pauseSearch by rememberPreference(PauseSearchHistoryKey, false)
                SettingSwitch(icon = Icons.AutoMirrored.Rounded.ManageSearch, title = "Pausar historial de búsqueda", subtitle = "No guarda las búsquedas realizadas", checked = pauseSearch, onCheckedChange = { pauseSearch = it })
            }
            item {
                var disableScreenshot by rememberPreference(DisableScreenshotKey, false)
                SettingSwitch(icon = Icons.Rounded.Monitor, title = "Bloquear capturas de pantalla", subtitle = "Impide hacer capturas dentro de la app", checked = disableScreenshot, onCheckedChange = { disableScreenshot = it })
            }

            item { SectionHeader("Caché", Icons.Rounded.Storage) }
            item {
                var autoDownloadOnLike by rememberPreference(AutoDownloadOnLikeKey, false)
                SettingSwitch(icon = Icons.Rounded.FavoriteBorder, title = "Descargar al dar like", subtitle = "Descarga automáticamente la canción cuando la agregas a favoritos", checked = autoDownloadOnLike, onCheckedChange = { autoDownloadOnLike = it })
            }

            item { SectionHeader("Servicios externos", Icons.Rounded.Sync) }
            item {
                var lastFmEnabled by rememberPreference(EnableLastFMScrobblingKey, false)
                SettingSwitch(icon = Icons.Rounded.Radio, title = "Last.fm Scrobbling", subtitle = "Registra las canciones que escuchás en Last.fm", checked = lastFmEnabled, onCheckedChange = { lastFmEnabled = it })
            }
            item {
                var discordRpc by rememberPreference(EnableDiscordRPCKey, true)
                SettingSwitch(icon = Icons.AutoMirrored.Rounded.Chat, title = "Discord Rich Presence", subtitle = "Muestra la canción actual en tu estado de Discord", checked = discordRpc, onCheckedChange = { discordRpc = it })
            }
            item {
                var listenBrainz by rememberPreference(ListenBrainzEnabledKey, false)
                SettingSwitch(icon = Icons.Rounded.MusicNote, title = "ListenBrainz", subtitle = "Envía scrobbles a ListenBrainz", checked = listenBrainz, onCheckedChange = { listenBrainz = it })
            }
            item {
                var echoBrain by rememberPreference(EchoBrainEnabledKey, false)
                SettingSwitch(icon = Icons.Rounded.AutoAwesome, title = "EchoBrain (IA)", subtitle = "Habilita el asistente de IA para recomendaciones y análisis", checked = echoBrain, onCheckedChange = { echoBrain = it })
            }

            item { SectionHeader("Red", Icons.Rounded.Wifi) }
            item {
                var proxyEnabled by rememberPreference(ProxyEnabledKey, false)
                SettingSwitch(icon = Icons.Rounded.Key, title = "Usar proxy", subtitle = "Enruta el tráfico a través de un servidor proxy", checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
            }
            item { ProxySettings() }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val selectedLabel = options.find { it.first == selectedKey }?.second ?: selectedKey

    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(text = selectedLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(key); expanded = false },
                        trailingIcon = { if (key == selectedKey) Icon(Icons.Rounded.Check, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxySettings() {
    var enabled by rememberPreference(ProxyEnabledKey, false)
    if (!enabled) return

    var url by rememberPreference(ProxyUrlKey, "")
    var type by rememberEnumPreference<Proxy.Type>(ProxyTypeKey, Proxy.Type.HTTP)
    var username by rememberPreference(ProxyUsernameKey, "")
    var password by rememberPreference(ProxyPasswordKey, "")

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = "La configuración se aplicará al reiniciar la aplicación.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SettingDropdown(
            icon = Icons.Rounded.SettingsEthernet,
            title = "Tipo de proxy",
            options = listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS"),
            selectedKey = type.name,
            onSelect = { type = Proxy.Type.valueOf(it) },
        )
        SettingTextField("Servidor", "host:puerto", url) { url = it }
        SettingTextField("Usuario (opcional)", "Usuario", username) { username = it }
        SettingTextField("Contraseña (opcional)", "Contraseña", password, isPassword = true) { password = it }
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
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

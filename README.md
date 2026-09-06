# FridaMusic

<p align="center">
  <img src="docs/assets/branding/fridamusic-logo.png" alt="Logo de FridaMusic" width="160" />
</p>

<p align="center">
  <strong>Tu música. Tu biblioteca. Tu experiencia.</strong><br />
  Reproductor musical para Android desarrollado por <strong>Frida Labs</strong>.
</p>

<p align="center">
  <a href="https://www.android.com/"><img alt="Android API 26+" src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" /></a>
  <a href="https://kotlinlang.org/"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" /></a>
  <a href="https://isocpp.org/"><img alt="C++" src="https://img.shields.io/badge/C%2B%2B-Nativo-00599C?style=for-the-badge&logo=cplusplus&logoColor=white" /></a>
  <a href="https://www.python.org/"><img alt="Python" src="https://img.shields.io/badge/Python-QA-3776AB?style=for-the-badge&logo=python&logoColor=white" /></a>
  <a href="https://gradle.org/"><img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.3.1-02303A?style=for-the-badge&logo=gradle&logoColor=white" /></a>
  <a href="https://cmake.org/"><img alt="CMake" src="https://img.shields.io/badge/CMake-Nativo-064F8C?style=for-the-badge&logo=cmake&logoColor=white" /></a>
</p>

<p align="center">
  <a href="https://github.com/jagrdev-MX/FridaMusic_OF/releases">GitHub Releases</a>
  &nbsp;•&nbsp;
  <a href="https://play.google.com/store/apps/details?id=com.jagr.fridamusic">Google Play</a>
  &nbsp;•&nbsp;
  <a href="https://frida-music-of.vercel.app/">Sitio oficial</a>
</p>

---

## Acerca de FridaMusic

FridaMusic es una aplicación Android enfocada en reproducir música local y contenido remoto desde una interfaz moderna basada en Jetpack Compose. El proyecto incluye biblioteca, búsqueda, playlists, letras, reconocimiento musical, estadísticas de escucha y herramientas para compartir canciones.

El repositorio es modular: la aplicación principal vive en `app/` y los conectores de contenido y letras se mantienen en módulos independientes. Las variantes de Android se definen en Gradle para separar distribuciones FOSS, GMS y GitHub.

## Características

- Biblioteca local basada en el contenido de audio disponible en el dispositivo.
- Búsqueda y navegación de canciones, artistas, álbumes y playlists.
- Favoritos, playlists, cola de reproducción y reproducción automática.
- Reproductor con AndroidX Media3, sesión multimedia, notificaciones y widgets.
- Letras con proveedores intercambiables y vista de karaoke cuando hay contenido compatible.
- Reconocimiento musical mediante el flujo de reconocimiento de FridaMusic y código nativo.
- FridaMusic Recap con estadísticas de escucha y tarjetas compartibles con QR.
- Copia de seguridad y restauración de los datos compatibles.
- Ecualizador, temporizador de suspensión, temas y opciones de personalización.
- Integraciones opcionales según variante, como servicios de Google, Cast, Firebase y Billing.

## Capturas

Las siguientes imágenes son recursos visuales originales proporcionados para la presentación pública del proyecto.

### Toda tu música, sin límites

<p align="center">
  <img src="docs/assets/screenshots/01-hero.png" alt="Presentación de FridaMusic" width="900" />
</p>

<table>
  <tr>
    <td align="center" width="50%">
      <strong>Home con estilo</strong><br />
      <img src="docs/assets/screenshots/02-home.png" alt="Pantalla Home de FridaMusic" width="480" />
    </td>
    <td align="center" width="50%">
      <strong>Navegación flotante moderna</strong><br />
      <img src="docs/assets/screenshots/03-navigation.png" alt="Navegación flotante y minirreproductor" width="480" />
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Reproductor, letras y cola</strong><br />
      <img src="docs/assets/screenshots/04-player.png" alt="Reproductor y cola de reproducción" width="480" />
    </td>
    <td align="center" width="50%">
      <strong>Control y letras sincronizadas</strong><br />
      <img src="docs/assets/screenshots/05-lyrics.png" alt="Controles y letras sincronizadas" width="480" />
    </td>
  </tr>
</table>

### Descarga FridaMusic

<p align="center">
  <img src="docs/assets/screenshots/06-download.png" alt="Opciones de descarga de FridaMusic" width="380" />
</p>

## Tecnología

Los badges superiores identifican lenguajes y herramientas realmente presentes en el checkout. Cada badge enlaza a la página oficial de su tecnología; las versiones mostradas provienen de `gradle/libs.versions.toml` y de la configuración Gradle del módulo `app`.

| Área | Tecnologías comprobadas en el repositorio |
| --- | --- |
| Lenguajes y build | Kotlin, Kotlin DSL, C++, CMake, Python para verificación, Gradle Wrapper |
| Android/UI | Android SDK 36, Jetpack Compose 1.10.2, Material 3 1.5.0-alpha18, AndroidX Lifecycle y Navigation Compose |
| Reproducción | AndroidX Media3/ExoPlayer 1.7.1, MediaSession, HLS y Media3 UI; Cast se añade en variantes GMS |
| Persistencia | Room 2.8.4 sobre SQLite, DataStore Preferences y KSP |
| Inyección y concurrencia | Hilt 2.59.1, Kotlin Coroutines/Flow y WorkManager |
| Red y multimedia | Ktor 3.4.0, Retrofit 2.11.0, OkHttp, Coil 3.3.0, Protobuf y FFmpegKit Audio |
| Integraciones | Firebase en variantes GMS seleccionadas, Google Play Services/Billing, proveedores de contenido y letras en módulos separados |

Referencias oficiales: [Kotlin](https://kotlinlang.org/docs/home.html), [Jetpack Compose](https://developer.android.com/jetpack/compose), [Material 3](https://m3.material.io/), [Media3](https://developer.android.com/media/media3), [Room](https://developer.android.com/training/data-storage/room), [Hilt](https://dagger.dev/hilt/), [Ktor](https://ktor.io/), [Retrofit](https://square.github.io/retrofit/), [Coil](https://coil-kt.github.io/coil/), [Protobuf](https://protobuf.dev/), [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started), [Gradle](https://docs.gradle.org/current/userguide/userguide.html) y [CMake](https://cmake.org/cmake/help/latest/).

## Estructura del repositorio

| Ruta | Responsabilidad |
| --- | --- |
| `app/` | Aplicación Android, UI Compose, playback, base de datos, servicios y variantes de distribución |
| `app/src/main/cpp/` | Código nativo C++ y puentes JNI para reconocimiento/procesamiento de audio |
| `innertube/`, `jiosaavn/`, `kugou/`, `lrclib/`, `betterlyrics/`, `paxsenixlyrics/`, `simpmusic/`, `youlyplus/`, `unison/` | Módulos de proveedores de contenido o letras |
| `canvas/`, `echomusiccanvas/`, `applecanvas/`, `artistvideo/`, `shazamkit/` | Módulos auxiliares de arte, vídeo y reconocimiento |
| `docs/` | Documentación del proyecto y auditorías técnicas |
| `docs/assets/` | Assets públicos usados por la documentación; consulta su [guía de organización](docs/assets/README.md) |
| `gradle/libs.versions.toml` | Catálogo central de versiones y dependencias |

## Compilar desde código fuente

### Requisitos

- Android Studio compatible con el Android Gradle Plugin configurado.
- JDK 21; el proyecto compila y apunta a JVM 21.
- Android SDK con API 36.
- Configuración local para las credenciales o servicios opcionales que requiera la variante elegida.

### Clonar y preparar

```bash
git clone https://github.com/jagrdev-MX/FridaMusic_OF.git
cd FridaMusic_OF
```

No subas `local.properties`, contraseñas, tokens ni claves. Para la configuración inicial puedes revisar [`gradle.properties.template`](gradle.properties.template); los valores de firma deben permanecer fuera del control de versiones.

### Compilar una variante FOSS de depuración

```bash
./gradlew :app:assembleUniversalFossDebug
```

En Windows:

```bat
gradlew.bat :app:assembleUniversalFossDebug
```

También existen variantes `Gms` y `Github`, además de salidas por ABI (`arm64`, `armeabi`, `x86` y `x86_64`). Los artefactos se generan en `app/build/outputs/`.

## Descargas y comunidad

- [GitHub Releases](https://github.com/jagrdev-MX/FridaMusic_OF/releases)
- [Google Play](https://play.google.com/store/apps/details?id=com.jagr.fridamusic)
- [Beta abierta](https://play.google.com/apps/testing/com.jagr.fridamusic)
- [Sitio oficial](https://frida-music-of.vercel.app/)
- [Telegram](https://t.me/FridaLabs)
- [Discord](https://discord.gg/Gyh7nfWK9k)
- [WhatsApp](https://chat.whatsapp.com/CrZyvVqoLPq6QGTbJSSrAX)
- [Issues](https://github.com/jagrdev-MX/FridaMusic_OF/issues)

> Comprueba siempre que una descarga provenga de un canal oficial de Frida Labs.

## Contribuciones

Las contribuciones, reportes y propuestas son bienvenidos mediante Issues y pull requests. Describe el alcance del cambio, conserva la separación entre variantes y evita incluir credenciales o recursos con restricciones de distribución.

## Licencia

Este checkout no contiene actualmente un archivo `LICENSE` rastreado. Por ello, la licencia del proyecto debe formalizarse en el repositorio antes de presentar el código como distribuido bajo GPL u otra licencia concreta. Las dependencias y recursos de terceros conservan sus propias licencias y avisos.

FridaMusic y Frida Labs son proyectos independientes. Las marcas y servicios externos mencionados pertenecen a sus respectivos propietarios.

<p align="center">
  <strong>FridaMusic</strong> forma parte del ecosistema de <strong>Frida Labs</strong>.<br />
  Hecho con ❤️ para la comunidad de FridaMusic.
</p>

# Auditoría del crash FGS en Android 16

Repositorio: FridaMusic_OF (checkout FridaMusic3). Rama: `fix/android16-foreground-service-crash`.
Versión conservada: 1.6.19.255 / 19; minSdk 26, targetSdk 36.

## Diagnóstico y límites de atribución

El candidato más fuerte es **Media3 1.7.1, actualización asíncrona de la carátula de la notificación**:

`DefaultMediaNotificationProvider.OnBitmapLoadedFutureCallback.onSuccess`
→ `MediaNotification.Provider.Callback.onNotificationChanged`
→ `MediaNotificationManager.onNotificationUpdated`
→ `updateNotificationInternal`
→ `startForeground`
→ `ContextCompat.startForegroundService`
→ `Context.startForegroundService`.

Se inspeccionaron los sources JAR de la versión resuelta, no una versión reciente distinta. El callback de carátula llega al main handler; el executor de MediaNotificationManager ejecuta inline si ya está en main. La ruta asíncrona no atraviesa el catch de `MediaSessionService.onUpdateNotificationInternal`. La secuencia explica `onSuccess`, Handler y Looper. La protección añadida rodea precisamente esa invocación, además de la actualización síncrona de la notificación.

WorkManager 2.10.0 sí tiene otra ruta real: `setForeground` → `WorkForegroundUpdater.setForegroundAsync` → executor serial → `Processor.startForeground` → `ContextCompat.startForegroundService`. Propaga el fallo al Future/coroutine. No presenta el callback `onSuccess` en ese camino; el catch vacío original del worker ocultaba el fallo y continuaba descargando. Es un defecto confirmado, pero encaja peor con los frames suministrados.

**No se deofuscó el crash.** No se recibió el stack completo ni su r8-map-id. Existen mappings locales, pero compartir versionName/versionCode no demuestra correspondencia binaria:

- universalGmsRelease: `6a644468ef59e9e676083bcc247bac31661b72c86e0400fae572b44fc147bb3f`.
- universalGmsGithub: `1361d402274d4026cd42393c3b8be22db69b4c7afc694762f00f443deb31e37a`.

La causa original sigue siendo **probable**, no confirmada por retrace ni por reproducción física.

## Rutas auditadas

| Ruta | Hallazgo y tratamiento |
|---|---|
| RecognitionLaunchActivity → RecognitionForegroundService | Inicio protegido; espera resumed + foco. La Activity permanece visible hasta que el servicio confirma su promoción. |
| Widget → MusicRecognizerWidgetService | Se conserva el inicio directo por gesto. Si falla, abre RecognitionLaunchActivity con el mismo token y destino widget. |
| Promoción de ambos servicios de reconocimiento | Conservan MICROPHONE desde API 30, micrófono, notificación y lógica existente de resultados. Revalidan permiso y manejan restricciones específicas. |
| Permiso de micrófono | La Activity solicita RECORD_AUDIO únicamente como consecuencia del gesto de reconocimiento. No depende del antiguo ACTION_RECOGNITION sin consumidor en MainActivity. |
| Recuperación del widget | Publica estado visible de error; el siguiente toque tiene PendingIntent de Activity directo, incluso si el OEM impide el primer salto desde background. No depende de POST_NOTIFICATIONS para ofrecer ese reintento. |
| Idempotencia | Token compartido entre inicio y fallback; se consume después de promoción y antes de capturar. Se conserva al recrear la Activity; se evita iniciar otro job si el servicio ya reconoce. |
| MusicService.onCreate | Promoción inicial protegida; se conserva el proveedor Media3 y el arranque del reproductor. |
| Media3, actualización síncrona | Protegida mediante onUpdateNotification existente de MediaSessionService. |
| Media3, carátula asíncrona | GuardedMediaNotificationProvider delega todo al proveedor existente y protege el callback en main. Conserva carátula y acciones. |
| Rechazo Media3 | Actualiza la notificación sin un nuevo arranque. Conserva reproducción si el servicio ya figura foreground; sólo pausa cuando Android ha rechazado el arranque y no existe un FGS activo, dejando sesión/cola y controles para reentrada del usuario. La consulta de estado es puntual, no polling. |
| MediaSession PendingIntents | DefaultActionFactory y MediaSessionLegacyStub usan getForegroundService para gestos de controles multimedia. Se conservan; la ejecución del PendingIntent la hace Android. |
| MediaButtonReceiver de Media3 y legacy | Sus callers internos manejan ForegroundServiceStartNotAllowedException; no hay receiver propio registrado que añada otra ruta sin protección. |
| SongActions | sendAddDownload y sendRemoveDownload conservan foreground=false. Sin cambios. |
| MusicService auto-download | sendAddDownload conserva foreground=false. No se toca cache, cola ni matching. |
| DownloadService.DownloadManagerHelper | El reinicio interno de Media3 captura IllegalStateException. Se conserva. |
| PlatformScheduler de Media3 | Su JobService llama a startForegroundService sin catch. El checkout tampoco declaraba ese servicio. Se sustituye sólo el adaptador Scheduler por DownloadRestartScheduler: mismo job ID, requisitos y persistencia, con JobService protegido y backoff nativo al rechazo. |
| ExoDownloadService | Conserva DownloadService, DownloadManager, cache y notificación dataSync. Añade log del comando y usa el adaptador de reinicio protegido. La promoción interna de notificación sigue siendo de Media3. |
| UpdateDownloadWorker | Se conserva, se inicializa su canal en proceso frío y no inicia I/O si falla setForeground. Restricción recuperable: hasta tres retries; permiso/estado no recuperable: failure. Propaga cancelación y cierra recursos en finally. |
| Reintento del actualizador | Notificación accionable abre las releases oficiales para reintentar. Es la misma fuente de releases usada por el checkout actual. No se agrega un scheduler de actualización nuevo. |
| RecommendationNotificationWorker / NotificationReleaseRefreshWorker | WorkManager activo de notificaciones, sin setForeground, ForegroundInfo ni expedited. Cambios previos del usuario preservados. |
| Reconocimiento Compose | MusicRecognitionScreen llama al reconocedor desde su coroutine; no llama a startForegroundService. Su flujo existente permanece. |
| AudioExportService | Usa startService; no es un FGS ni el callsite investigado. |

No se encontró scheduler ni referencia activa a UpdateDownloadWorker fuera de su declaración. CustomDownloadManager también carece de consumidores. La pantalla actual de versiones usa ReleaseRepository y abre las releases. Se retiene el worker para WorkRequests persistidos de instalaciones anteriores. No se introduce expedited a ciegas: no hay un scheduling user-initiated activo que modificar, y expedited no exime las restricciones de permisos o promoción.

Además se inspeccionaron los servicios añadidos por los manifests de Cast, Ads, Measurement, Auth, Firebase Sessions/Components, Room y DataTransport. Se escanearon los constant pools de los AAR/JAR que participan en el merge para referencias a startForegroundService/getForegroundService. Las coincidencias fueron AndroidX Core, Media/Media3 y WorkManager; no apareció otro caller directo en esos artefactos de Google/Firebase. Este análisis estático no garantiza comportamiento de módulos remotos del SDK ni sustituye QA real.

## Manifest GMS Release

Se ejecutó `:app:processUniversalGmsReleaseMainManifest` antes de editar y `:app:processUniversalGmsReleaseManifest` después.

Resultado inspeccionado:
`app/build/intermediates/merged_manifests/universalGmsRelease/processUniversalGmsReleaseManifest/AndroidManifest.xml`.

- FOREGROUND_SERVICE, RECORD_AUDIO, FOREGROUND_SERVICE_MEDIA_PLAYBACK y FOREGROUND_SERVICE_DATA_SYNC presentes.
- Se agrega FOREGROUND_SERVICE_MICROPHONE.
- Se agrega RECEIVE_BOOT_COMPLETED, permiso normal requerido por JobInfo.setPersisted(true).
- RecognitionForegroundService y MusicRecognizerWidgetService: microphone, exported=false, una declaración cada uno.
- RecognitionLaunchActivity: exported=false; registro del receiver y metadata del widget conservando RemoteViews y PendingIntents.
- Tile de reconocimiento: BIND_QUICK_SETTINGS_TILE; exported=true por su integración con SystemUI.
- SystemForegroundService: dataSync mediante tools:node="merge"; exported=false heredado.
- ExoDownloadService: dataSync, exported=false.
- MusicService: mediaPlayback, exported=true para clientes MediaSession existentes.
- DownloadRestartJobService: BIND_JOB_SERVICE y exported=true para JobScheduler; servicio breve, no permanente.
- Sin servicios duplicados, tipos sobrantes, systemExempted, permisos privilegiados ni exclusión de optimización de batería.

## Dependencias y Android

Sólo se cambia la versión declarada de WorkManager: **2.10.0 → 2.11.2**. Es estable y su minSdk 23 es compatible con el minSdk 26 del proyecto. Media3 permanece en 1.7.1. El manifest merger confirma ambas versiones resueltas.

| Android | Comportamiento esperado; requiere prueba física |
|---|---|
| 12 / 13 | Rechazos al inicio FGS se manejan en el caller. Se mantienen excepciones legales por interacción. |
| 14 | Se declaran permisos/tipos de micrófono; el inicio desde Activity espera estado visible y resumed. Se revalida RECORD_AUDIO, pero Android decide la elegibilidad while-in-use. |
| 15 | Se conserva lo anterior; no se eluden cuotas de dataSync ni restricciones de arranque desde boot. |
| 16 | Se conserva lo anterior. JobScheduler/WorkManager siguen sujetos a cuotas; no se promete ejecución ininterrumpida cuando Android la prohíbe. |

El fix de inicio no modifica los límites de duración internos de Media3 ni convierte la actualización de WorkManager en una garantía contra cualquier excepción de Android.

## Archivos de esta corrección

Todos bajo app/src salvo indicación:

- main/AndroidManifest.xml.
- main/kotlin/com/jagr/fridamusic/utils/ForegroundServiceLaunch.kt (nuevo).
- firebase, foss y noFirebase/kotlin/com/jagr/fridamusic/utils/CrashReporter.kt: breadcrumbs sin nonfatals artificiales ni datos sensibles.
- main/kotlin/com/jagr/fridamusic/playback/MusicService.kt.
- main/kotlin/com/jagr/fridamusic/playback/GuardedMediaNotificationProvider.kt (nuevo).
- main/kotlin/com/jagr/fridamusic/playback/ExoDownloadService.kt.
- main/kotlin/com/jagr/fridamusic/playback/DownloadRestartScheduler.kt (nuevo).
- main/kotlin/com/jagr/fridamusic/recognition/RecognitionLaunchActivity.kt.
- main/kotlin/com/jagr/fridamusic/recognition/RecognitionForegroundService.kt.
- main/kotlin/com/jagr/fridamusic/recognition/RecognitionServiceRequest.kt (nuevo).
- main/kotlin/com/jagr/fridamusic/widget/MusicRecognizerWidgetReceiver.kt.
- main/kotlin/com/jagr/fridamusic/widget/MusicRecognizerWidgetService.kt.
- main/kotlin/com/jagr/fridamusic/echomusic/updater/downloadmanager/UpdateDownloadWorker.kt.
- main/kotlin/com/jagr/fridamusic/echomusic/updater/downloadmanager/downloadnotificationmanager.kt.
- main/res/values/strings.xml y main/res/values-es-rMX/strings.xml: mensajes de recuperación.
- gradle/libs.versions.toml.
- docs/android16-foreground-service-audit.md.

Los cambios previos de notificaciones, MainActivity, DAO y pantallas no pertenecen a esta corrección. En los dos strings.xml compartidos se añadieron únicamente las cadenas de recuperación sin revertir las modificaciones anteriores.

## Validación y QA Samsung SM-A566E / SDK 36

Validación final:

- `git diff --check`: PASS, exit 0.
- `:app:compileUniversalFossDebugKotlin --no-daemon`: PASS.
- `:app:compileUniversalGmsReleaseKotlin --no-daemon`: PASS; configuración Firebase disponible.
- `:app:processUniversalGmsReleaseManifest --no-daemon`: PASS.
- Comprobación explícita del XML merged: PASS para targetSdk 36, permisos, tipos, exported, metadatos del widget y ausencia de servicios duplicados.
- Última ejecución combinada: BUILD SUCCESSFUL, 284 tareas (7 ejecutadas, 277 actualizadas).
- Búsqueda global final de startForegroundService/startForeground/setForeground/ForegroundInfo/SystemForegroundService: sólo queda un startForegroundService propio, dentro del helper protegido. Las otras promociones propias están protegidas; los inicios de librerías se detallan arriba.
- No había tests unitarios rápidos existentes de FGS/reconocimiento/descargas. No se añadió una dependencia de test ni se ejecutó una suite amplia. La revisión de callbacks, permisos y restricciones sigue necesitando dispositivo.
- Logs locales de esta ejecución: `.gradle/fgs-audit/build-final.log`, `manifest-validation.txt`, `library-starts.txt`, `final-global-search.txt` y `diff-check.log`.

Las pruebas físicas pendientes son:

1. Reproducción con carátula lenta, cambiar canción y pasar inmediatamente a Home/bloqueo. Verificar ausencia del fatal, carátula/controles, cola, MediaSession, pausa y reanudación.
2. Reconocer desde app y tile con permiso concedido. Enviar la Activity a background durante el arranque; la captura sólo debe comenzar tras un inicio legal.
3. Widget con app cerrada: reconocimiento directo cuando Android lo permite; micrófono, animación, cancelación, resultado e historial.
4. Rechazo/race desde widget: Activity visible; si el OEM bloquea el salto, el siguiente toque debe abrir directamente esa Activity. Un token no debe crear dos capturas/resultados.
5. Revocar RECORD_AUDIO; iniciar desde widget/tile, conceder, denegar y denegar permanentemente. Revisar recuperación desde Ajustes y que no aparezcan prompts al abrir normalmente la app.
6. Repetir con notificaciones denegadas, pantalla bloqueada, rotación y cambio rápido entre apps; verificar el reintento del widget sin depender de una notificación.
7. Descargas manuales y auto-download: cancelación, cache, pérdida/recuperación de red, requisitos de red/carga y reinicio del teléfono. Verificar reanudación mediante JobScheduler.
8. Instalación actualizada con un WorkRequest legado pendiente: promoción aceptada/rechazada, retries acotados, failure accionable, cierre/limpieza al cancelar y descarga APK/ZIP correcta.
9. Recomendaciones/WorkManager existentes, widgets y controles multimedia externos siguen funcionando.
10. Revisar breadcrumbs FGS source/event/sdk/lifecycle/exception en Crashlytics GMS. La compilación no acredita recepción remota.

No se ha hecho push, merge, release, version bump ni APK/AAB firmado.

## Fuentes

- Media3 1.7.1, MediaNotificationManager: https://github.com/androidx/media/blob/1.7.1/libraries/session/src/main/java/androidx/media3/session/MediaNotificationManager.java
- WorkManager estable y notas: https://developer.android.com/jetpack/androidx/releases/work
- Restricciones y excepciones por interacción: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Workers largos y Android 16: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

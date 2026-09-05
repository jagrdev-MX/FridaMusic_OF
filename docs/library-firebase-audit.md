# Biblioteca y Firebase: auditoría del worktree actual

Fecha: 2026-09-05. Repositorio: jagrdev-MX/FridaMusic_OF.

## Billing

Los archivos locales de Billing se conservaron byte por byte, comprobados mediante SHA-256 contra una captura al comenzar esta tarea. Esto incluye SupportBillingManager (GMS/FOSS), SupportBilling, SupportCenterSheet, UniversalGmsDebugSupportTools y SupportAccountIdentity. Los cambios de versión ya estaban en el worktree: versionCode 18 / versionName 1.5.18.254; esta tarea no los modificó. No se hizo commit, push, publicación, cambios en Play Console ni cambios de precios/PayPal.

15 tests existentes pasaron: 7 de catálogo/estado y 8 de identidad. Cubren cancelación, reapertura, compra siguiente, completed, errores recuperables, identidad A/B, logout, normalización y ausencia del correo literal. Esto prueba las funciones de estado/identidad; no sustituye una compra real ni el popup de Google Play. Se conservaron LocalActivity.current, validación RESUMED, ProductDetails frescos, oferta determinista, exclusión de compras concurrentes, logs seguros y Billing 9.1.0. Las capacidades compiladas siguen deshabilitando Billing en GitHub/FOSS.

## Playlists: fuente, persistencia y valor desconocido

Ruta rastreada: YouTube.library(FEmusic_liked_playlists) → LibraryPage/PlaylistItem → SyncUtils.executeSyncSavedPlaylists / DatabaseDao / PlaylistsViewModel → PlaylistEntity.remoteSongCount + playlist_song_map → Playlist.effectiveSongCount → Biblioteca/Android Auto.

Causas demostrables en código:

- LibraryPage asumía que el último run del subtítulo era el count; no siempre lo es.
- Algunas escrituras tomaban sólo la primera coincidencia de dígitos: 1,234 terminaba como 1. Otras concatenaban todos los dígitos, incluso de texto que no era un total exacto.
- Una actualización de catálogo con metadata sin count sobrescribía un count previo con null.
- OnlinePlaylistViewModel mostraba las canciones remotas sin persistir su count en la playlist guardada. Abrirla no materializa necesariamente relaciones locales.
- El modelo convertía ausencia de count remoto y ausencia de relaciones en el mismo 0 que una playlist vacía confirmada.

Ahora PlaylistItem.songCount usa un parser único para totales enteros exactos, incluidos separadores de miles y etiquetas EN/ES. No interpreta 1.2K, duraciones ni otros números como totales exactos. El catálogo busca el run con count, conserva metadata conocida cuando la respuesta no trae count y persiste el count al importar/guardar.

Al abrir el detalle se persiste la metadata disponible. Si no existe, se persiste el número de entradas recibidas cuando la paginación termina de forma normal; nunca el tamaño de una página parcial ni de la lista filtrada por preferencias. La actualización SQL sólo cambia remoteSongCount para el browseId remoto, es idempotente y no pisa nombre/bookmark. El PlaylistItem del detalle recibe también el count para una playlist que se guarde después de cargarla. La sincronización explícita completa conserva primero el total de metadata; usa las canciones cargadas como fallback.

Playlist.effectiveSongCount es nullable: máximo de relaciones locales y count remoto cuando hay evidencia; null para un remoto sin evidencia; 0 se conserva para locales vacíos o un total remoto 0 explícito. El formato compartido omite por completo la línea secundaria cuando el count es null y usa plurales para números conocidos. Se aplica a cards/listas/grids, menú/compacta del Mix, HomeCollection y Android Auto. No se suman counts superpuestos. No se añadieron fetches por card ni full sync de catálogo.

## Artistas

La consulta cuenta SongArtistMap unido a Song con song.inLibrary IS NOT NULL. SongArtistMap tiene clave primaria (songId, artistId): no duplica una canción por artista. Los artistas favoritos pueden existir sin esas relaciones; el contenido remoto del detalle no constituye un total de discografía. ArtistItem/ArtistPage no entregan un total fiable de canciones; subscribers/monthly listeners son métricas distintas.

Artist.effectiveSongCount conserva la semántica de biblioteca: count positivo conocido; null para un artista remoto sin canciones materializadas; 0 para uno local vacío. La etiqueta es “N canciones en biblioteca”, nunca un total inventado de discografía. No se añadió una columna remota sin fuente fiable ni se descargaron discografías. Android Auto usa el mismo formato. ArtistCircleCard del Mix no mostraba un count, por lo que no tenía un cero que corregir.

La pestaña conserva únicamente el selector de vista: lista de una columna o grid fijo de dos columnas. Se retiraron filtros y ordenamiento de esta superficie. Ambas vistas conservan apertura, long press, menú y el count efectivo; cuando el count es desconocido omiten esa línea.

## Álbumes

La pestaña conserva únicamente el selector de vista: lista de una columna o grid fijo de dos columnas. La portada es cuadrada; muestra título y artista, y conserva apertura, menú y long press. Se retiraron filtros y controles de orden de esta superficie.

Se corrigió un bug adicional del DAO: ordenar Subidos por nombre/año/count/duración/play time cambiaba a álbumes favoritos porque las consultas usaban bookmarkedAt. Ahora filtran album.isUploaded = 1. La UI sólo expone los cinco órdenes anteriores.

Ambas pestañas usan directamente el mismo LazyVerticalGrid con GridCells.Fixed(1) para lista y GridCells.Fixed(2) para grid, junto con LibraryViewModeToggle. Artistas y Álbumes, igual que Canciones y Local, conservan su selección de vista en claves independientes del DataStore existente. No se añadió infraestructura genérica ni otro DataStore. Se retiraron los colectores de enriquecimiento por elemento de LibraryArtistsViewModel/LibraryAlbumsViewModel. Las pantallas de detalle mantienen la actualización explícita de metadata.

## Firebase / Crashlytics

El JSON real existe en app/google-services.json, está ignorado y no está versionado. Se leyó de forma selectiva; no se copió ni publicó. Tiene dos clientes en el mismo proyecto Firebase:

| Variante | Package | Sufijo del Firebase App ID | Recursos generados |
|---|---|---|---|
| universalGmsDebug | com.jagr.fridamusic.debug | …b7a44739 | Coinciden con su cliente JSON |
| universalGmsRelease | com.jagr.fridamusic | …19a44739 | Coinciden con su cliente JSON |

Los plugins google-services 4.4.2 y Crashlytics Gradle 3.0.2 están aplicados realmente. Son plugins de proyecto: no se aplican individualmente a un flavor. Sus tareas están habilitadas en GMS Debug/Release y deshabilitadas en FOSS/GitHub. La aplicación de los plugins ya no depende de la existencia del JSON raíz: el plugin resuelve los JSON por variante y falla si falta configuración o cliente compatible. Esto también admite archivos por build type sin exigir el JSON raíz.

Las dependencias/source sets de Firebase se limitan a GMS Debug/Release para todas las ABI existentes. CrashReporter se trasladó de src/gms a src/firebase, compartido por esas variantes. FOSS/GitHub usan implementaciones no-op. El runtime de GitHub no contiene Crashlytics, Analytics ni FirebaseInitProvider; mantiene encoders auxiliares transitivos usados por GMS. FOSS no tiene dependencias Firebase. Los manifiestos regenerados lo confirman. Se evita reutilizar recursos Google generados por builds anteriores de FOSS/GitHub.

Se conservó BOM 33.1.0 (Crashlytics Android 19.0.1). No se encontró una razón demostrada para actualizarlo.

No hay setCrashlyticsCollectionEnabled ni flags de desactivación/consentimiento Firebase en las fuentes auditadas. La configuración usa el comportamiento predeterminado del SDK. Esto no prueba el estado efectivo de una instalación que pueda conservar un override antiguo. No se fuerza la recolección ni se llama sendUnsentReports. El SDK actual no tiene getter público de recolección de Crashlytics: el diagnóstico informa firebaseDefaultCollection y remite a los logs del SDK para el estado efectivo.

CrashlyticsInit registra package, build type, flavor, inicialización Firebase, sufijo del App ID y estado predeterminado. No registra keys, tokens, correos ni installation IDs. El non-fatal existente llama recordException y confirma únicamente registro local en el SDK, no recepción remota. El fatal existente lanza una RuntimeException real; sigue limitado a universalGmsDebug mediante BuildConfig y el recurso de herramientas internas. No hay botón de crash para release.

CrashHandler conserva persistencia local síncrona y delegación al handler anterior. No se convierte el fatal en non-fatal. No se ejecutaron los botones en el teléfono ni se instaló una build en esta tarea: non-fatal/fatal y recepción en Console siguen pendientes de prueba manual. La lectura diagnóstica limitada del dispositivo no aportó confirmación de envío.

### Mapping y native

Existe mapping.txt de una build release anterior, en app/build/outputs/mapping/universalGmsRelease/mapping.txt (165774824 bytes; fecha local 2026-09-05 01:36:30). Esta tarea no ejecutó R8 para regenerarlo: compileKotlin no lo hace.

Se verificó uploadCrashlyticsMappingFileUniversalGmsRelease: existe y está habilitada. El grafo real de bundleUniversalGmsRelease --dry-run contiene minifyUniversalGmsReleaseWithR8 y uploadCrashlyticsMappingFileUniversalGmsRelease. Por tanto la ruta estándar del AAB de Play incluye el upload automático. El texto SKIPPED del dry-run significa simulación del grafo, no upload deshabilitado. No se generó/publicó un AAB ni se ejecutó un upload en esta tarea. No puede afirmarse que la build histórica publicada haya subido su mapping.

No hay firebase-crashlytics-ndk en los runtimes resueltos. ndk.debugSymbolLevel=FULL genera símbolos de packaging/Play; no demuestra envío ni simbolización de crashes nativos con Crashlytics. La integración auditada es Java/Kotlin.

### Causa de la ausencia de eventos y comprobación manual

Está confirmada la fragilidad de build: antes, un entorno sin JSON podía omitir ambos plugins silenciosamente. El entorno local actual sí tiene clientes compatibles. No hay evidencia suficiente para atribuir a esa fragilidad todos los eventos ausentes en debug/producción; faltan logs de la instalación/build afectada y Firebase Console.

1. Instalar la build universalGmsDebug validada y activar temporalmente `adb shell setprop log.tag.FirebaseCrashlytics DEBUG`.
2. Abrir Ajustes → herramientas internas. Revisar CrashlyticsInit y la línea del SDK que informa recolección automática y su origen; respetar un estado disabled.
3. Pulsar non-fatal: comprobar despacho local. Pulsar fatal: debe cerrar el proceso; reiniciar para permitir envío y comprobar la delegación de CrashHandler.
4. Revisar `adb logcat -s CrashlyticsInit FirebaseCrashlytics CrashHandler`, filtrando cualquier identificador antes de compartir logs. Buscar confirmación de upload del SDK y el evento correspondiente en la Firebase App debug.
5. Verificar producción con la próxima build interna/closed track autorizada; comprobar App ID de producción y stacks desofuscados. No añadir botón visible a release. Esta tarea no modifica Play Console.
6. Restaurar el nivel de logs a INFO. Los logs de envío y la recepción en Console son evidencias separadas de la compilación.

Fuentes: [prueba oficial de Crashlytics](https://firebase.google.com/docs/crashlytics/android/test-implementation), [mapping y desofuscación](https://firebase.google.com/docs/crashlytics/android/get-deobfuscated-reports), [recolección y consentimiento](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports).

## Validación ejecutada

- compileUniversalGmsDebugKotlin: PASS.
- compileUniversalGmsReleaseKotlin: PASS.
- compileUniversalFossDebugKotlin: PASS.
- compileUniversalGmsGithubKotlin: PASS.
- SupportBillingStateTest: 7/7; SupportAccountIdentityTest: 8/8.
- LibraryCountTest: 4/4; PlaylistSongCountTest: 3/3. Total JUnit: 22, sin fallos.
- python -X utf8 app/src/test/scripts/verify_library_queries.py: PASS; ejecuta SQL extraído del DAO en SQLite en memoria. Counts, orden, filtros, ausencia de duplicados y persistencia idempotente que conserva otras columnas.
- Manifiestos FOSS/GitHub regenerados: sin FirebaseInitProvider.
- Recursos Google GMS Debug/Release: App IDs coinciden con los clientes correctos.
- Auditoría Gradle de plugins, runtime y tareas: PASS.
- Configuración ausente simulada mediante un init script temporal que vacía los inputs JSON, sin mover/eliminar el archivo real: GMS Debug y Release fallan con “File google-services.json is missing”; FOSS/GitHub pasan con tareas Firebase deshabilitadas.
- Grafo de bundleUniversalGmsRelease mediante --dry-run: incluye R8 y upload de mapping.
- git diff --check: PASS.

Hubo errores de implementación durante las iteraciones (source set Kotlin, getter no disponible y calificación SQL), corregidos antes de la ejecución final exitosa. Persisten warnings preexistentes de APIs/Room; no se presentan como pruebas de dispositivo.

## Archivos de esta tarea

Los archivos de Billing preexistentes no se incluyen en esta lista porque no fueron editados. app/build.gradle.kts sí contiene cambios previos de versión que se conservaron.

- `app/build.gradle.kts`
- `app/src/firebase/kotlin/com/jagr/fridamusic/utils/CrashReporter.kt`
- `app/src/foss/kotlin/com/jagr/fridamusic/utils/CrashReporter.kt`
- `app/src/gms/kotlin/com/jagr/fridamusic/utils/CrashReporter.kt` (trasladado a src/firebase)
- `app/src/main/kotlin/com/jagr/fridamusic/App.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/db/DatabaseDao.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/db/entities/Artist.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/db/entities/Playlist.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/playback/MediaLibrarySessionCallback.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/presentation/components/PlaylistLibraryComponents.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/presentation/screens/HomeCollectionScreen.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/presentation/screens/LibraryScreen.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/presentation/screens/SettingsScreen.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/utils/LibraryCountText.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/utils/SyncUtils.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/viewmodels/LibraryViewModels.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/viewmodels/OnlinePlaylistViewModel.kt`
- `app/src/main/kotlin/com/jagr/fridamusic/viewmodels/PlaylistsViewModel.kt`
- `app/src/main/res/values-es-rMX/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/noFirebase/kotlin/com/jagr/fridamusic/utils/CrashReporter.kt`
- `app/src/test/kotlin/com/jagr/fridamusic/db/entities/LibraryCountTest.kt`
- `app/src/test/scripts/verify_library_queries.py`
- `innertube/src/main/kotlin/com/music/innertube/YouTube.kt`
- `innertube/src/main/kotlin/com/music/innertube/models/PlaylistSongCount.kt`
- `innertube/src/main/kotlin/com/music/innertube/models/YTItem.kt`
- `innertube/src/main/kotlin/com/music/innertube/pages/LibraryPage.kt`
- `innertube/src/test/kotlin/com/music/innertube/models/PlaylistSongCountTest.kt`

- `docs/library-firebase-audit.md` (este informe)

## Riesgos y siguiente paso

Los totales remotos desconocidos permanecen neutrales hasta recibir metadata exacta o terminar la carga explícita. No se rehidrata masivamente el catálogo. El parser exacto cubre EN/ES y separadores de miles; formatos aproximados u otros textos no reconocidos permanecen desconocidos. La revisión visual/responsive y TalkBack requiere dispositivo. Las compras reales y recepción Firebase debug/release necesitan QA manual; no se afirma que Firebase esté reparado sólo por compilar.

Conventional Commit propuesto (no creado): `fix(biblioteca): corrige conteos y vistas y valida Crashlytics por variante`

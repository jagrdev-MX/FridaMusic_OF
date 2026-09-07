# Contribuir a FridaMusic

Gracias por tu interés en mejorar FridaMusic. Este repositorio acepta contribuciones mediante **Pull Requests**. La rama `master` se considera estable y no debe recibir cambios directos de colaboradores.

## Flujo obligatorio

1. Haz fork del repositorio o crea una rama si tienes permisos de colaboración.
2. Crea una rama específica para tu cambio.
3. Implementa únicamente el alcance necesario.
4. Ejecuta las validaciones mínimas indicadas abajo.
5. Sube tu rama.
6. Abre un Pull Request contra `master`.
7. Espera revisión y resuelve las conversaciones pendientes.
8. El mantenedor decidirá cuándo integrar el cambio después de revisar y, cuando corresponda, probarlo localmente en un dispositivo real.

No hagas push directo a `master` para contribuciones normales.

## Nombres de ramas sugeridos

- `fix/descripcion-corta`
- `feat/descripcion-corta`
- `perf/descripcion-corta`
- `refactor/descripcion-corta`
- `docs/descripcion-corta`
- `ci/descripcion-corta`

## Validación mínima antes del PR

Desde la raíz del proyecto:

```bash
git diff --check
./gradlew :app:compileUniversalFossDebugKotlin
```

En Windows PowerShell:

```powershell
git diff --check
.\gradlew.bat :app:compileUniversalFossDebugKotlin
```

No es necesario ejecutar una batería completa de pruebas para cada cambio pequeño. Si modificas una función crítica, añade o ejecuta una prueba focalizada cuando sea razonable. Las pruebas manuales relevantes deben describirse en el Pull Request.

## Probar un Pull Request localmente

Con GitHub CLI:

```bash
gh pr checkout NUMERO_DEL_PR
```

Sin GitHub CLI:

```bash
git fetch origin pull/NUMERO_DEL_PR/head:pr-NUMERO_DEL_PR
git switch pr-NUMERO_DEL_PR
```

Después puedes abrir la rama en Android Studio y probarla en un dispositivo antes del merge.

## Reglas de alcance

- Evita refactors masivos dentro de un PR que pretende corregir un bug pequeño.
- No cambies `versionCode` o `versionName` salvo que un mantenedor lo haya solicitado explícitamente.
- No añadas dependencias sin justificar por qué la solución no puede implementarse con la infraestructura existente.
- Mantén la separación entre variantes GMS, FOSS y GitHub.
- No desactives controles de seguridad, privacidad, licencias o compatibilidad para hacer que una compilación pase.
- No introduzcas telemetría, trackers o recopilación adicional de datos sin discusión previa.
- Los cambios de reproducción, Audio Focus, MediaSession, base de datos, migraciones, Billing, Firebase y seguridad requieren una explicación técnica clara en el PR.

## Secretos y datos sensibles

Nunca incluyas en commits, issues o Pull Requests:

- contraseñas;
- tokens de acceso;
- claves privadas;
- keystores;
- `local.properties`;
- cookies o cabeceras de autorización;
- credenciales de proveedores;
- archivos de configuración privados;
- datos personales de usuarios.

Si encuentras una vulnerabilidad, sigue `SECURITY.md` y usa el reporte privado de seguridad. No abras un Issue público con instrucciones de explotación.

## Commits

Usa mensajes claros. Se recomienda Conventional Commits:

```text
feat(library): añade filtro por artista
fix(playback): corrige pérdida transitoria de audio focus
perf(database): reduce consultas redundantes
refactor(ui): centraliza componente reutilizable
docs: actualiza guía de compilación
ci: agrega validación de pull requests
```

No es necesario reescribir todo el historial antes de abrir el PR; el proyecto prioriza **squash merge** para mantener `master` limpio.

## Pull Requests

Un buen PR debe explicar:

- qué problema resuelve;
- cuál fue la causa raíz cuando se trata de un bug;
- qué archivos o módulos afecta;
- cómo fue validado;
- qué riesgos o casos pendientes quedan;
- capturas o video si cambia la UI.

Mantén el PR enfocado. Si aparecen cambios no relacionados, sepáralos en otro PR.

## Uso de herramientas de IA

Se permite utilizar asistentes de código, pero la responsabilidad sigue siendo del autor del Pull Request. Debes poder explicar el cambio, revisar el diff y validar que no haya código inventado, dependencias innecesarias, secretos, problemas de licencia o cambios fuera de alcance.

## Licencia

Al contribuir aceptas que tu contribución se distribuya bajo la licencia **GNU General Public License v3.0 (GPL-3.0)** del proyecto, salvo componentes que indiquen explícitamente otra licencia compatible. También debes respetar las licencias y avisos de terceros.

## Código de conducta

Toda participación debe respetar `CODE_OF_CONDUCT.md`.

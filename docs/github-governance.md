# Gobierno de GitHub para FridaMusic

Este documento describe la configuración esperada del repositorio público `jagrdev-MX/FridaMusic_OF` para mantener `master` estable y revisar todo cambio mediante Pull Request.

## Objetivo

El flujo esperado es:

```text
fork o rama
    ↓
Pull Request contra master
    ↓
CI mínima
    ↓
revisión del mantenedor
    ↓
prueba local/dispositivo cuando aplique
    ↓
squash merge
    ↓
master
```

Los colaboradores no deben subir cambios directamente a `master`.

## Ruleset recomendado

Crear un **Branch ruleset** con:

- Nombre: `Protect master — Pull Requests Only`
- Enforcement: `Active`
- Target: rama por defecto / `master`

Reglas:

- Restrict deletions: activado.
- Block force pushes: activado.
- Require linear history: activado.
- Require a pull request before merging: activado.
- Required approvals: `1`.
- Dismiss stale pull request approvals when new commits are pushed: activado.
- Require approval of the most recent reviewable push: activado cuando GitHub lo permita para la configuración elegida.
- Require conversation resolution before merging: activado.
- Require review from Code Owners: activado.
- Require status checks to pass before merging: activar después de que el workflow `PR Checks` se haya ejecutado al menos una vez.

Status check esperado:

```text
PR Checks / Compile FOSS Debug
```

No se recomienda exigir commits firmados mientras el historial y flujo actuales continúen generando commits no firmados.

## Bypass del mantenedor

Si GitHub permite `Bypass list`, el mantenedor principal puede añadirse con bypass **sólo para Pull Requests**.

La intención es permitir resolver emergencias o repositorios con un único mantenedor sin habilitar push directo normal a `master`.

No conceder bypass permanente a colaboradores ni a GitHub Actions para escribir directamente a `master`.

## Merge settings

En `Settings → General → Pull Requests`:

- Allow squash merging: activado.
- Allow merge commits: desactivado.
- Allow rebase merging: desactivado.
- Allow auto-merge: desactivado salvo decisión posterior.
- Automatically delete head branches: activado.

El objetivo es que cada PR aceptado produzca un único commit limpio en `master`.

## GitHub Actions

En `Settings → Actions → General`:

- Mantener permisos por defecto lo más restrictivos posible.
- Permitir que workflows concretos soliciten sólo los permisos que necesiten.
- Activar `Allow GitHub Actions to create and approve pull requests` únicamente porque `update-contributors.yml` necesita crear/actualizar su PR automático. El workflow no aprueba su propio PR.

`update-contributors.yml` no debe hacer push directo a `master`; debe usar la rama:

```text
automation/update-contributor-stats
```

y abrir un Pull Request.

## Pull Request CI

`.github/workflows/pr-checks.yml` valida Pull Requests no draft contra `master` con:

```bash
git diff --check "origin/<base>...HEAD"
./gradlew :app:compileUniversalFossDebugKotlin --no-daemon
```

Se usa FOSS Debug para no depender de secretos, Firebase o configuraciones privadas que los forks externos no poseen.

La CI no sustituye las pruebas manuales de reproducción, Audio Focus, Bluetooth, Billing, Firebase, UI o comportamiento específico de fabricantes.

## Pruebas locales de un PR

Con GitHub CLI:

```bash
gh pr checkout NUMERO
```

Sin GitHub CLI:

```bash
git fetch origin pull/NUMERO/head:pr-NUMERO
git switch pr-NUMERO
```

Después se puede abrir la rama en Android Studio y ejecutar la variante adecuada en dispositivo físico.

## CODEOWNERS

`.github/CODEOWNERS` define a `@jagrdev-MX` como propietario general. Si en el futuro otros mantenedores asumen responsabilidad estable sobre áreas concretas, se deben asignar rutas específicas en vez de otorgar ownership global sin necesidad.

## Issues y contribuciones

Los formularios oficiales separan:

- bugs reproducibles;
- propuestas de funcionalidad.

Los blank issues permanecen desactivados para reducir reportes sin contexto. Los enlaces de soporte comunitario y seguridad se ofrecen como canales alternativos.

## Seguridad

Activar en `Settings → Security` la opción **Private vulnerability reporting**.

Los reportes sensibles deben usar:

```text
https://github.com/jagrdev-MX/FridaMusic_OF/security/advisories/new
```

No deben publicarse exploits, tokens o datos personales en Issues abiertos.

## Sponsors

`.github/FUNDING.yml` configura el enlace de apoyo voluntario mediante PayPal.

Si GitHub muestra una opción adicional de Sponsorships/Sponsor button en Settings, debe permanecer activada para que el botón se renderice en el repositorio.

## Archivos de gobierno

El repositorio mantiene:

- `CONTRIBUTING.md`
- `CODE_OF_CONDUCT.md`
- `SECURITY.md`
- `.github/SUPPORT.md`
- `.github/CODEOWNERS`
- `.github/FUNDING.yml`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/ISSUE_TEMPLATE/`
- `.github/workflows/pr-checks.yml`

Estos archivos documentan el proceso; el Ruleset es el control técnico que realmente impide cambios directos a `master`.

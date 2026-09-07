# FridaMusic Web

Este directorio contiene el sitio web público oficial de FridaMusic y sus recursos exclusivos.

## Estructura

- `index.html`: landing principal.
- `beta.html` / `beta.js`: página del programa beta.
- `privacy.html` / `privacy.js`: política de privacidad.
- `styles.css`: estilos compartidos del sitio.
- `script.js`: lógica principal de la landing.
- `logo1.png`: logo utilizado por el sitio.
- `app-ads.txt`: archivo público requerido para publicidad.
- `assets/`: banners y recursos web específicos.
- `capturas web/`: capturas consumidas por la landing. El nombre se conserva por compatibilidad con las rutas ya publicadas.
- `release/`: configuración, plugin local y changelog del track de releases web.
- `scripts/`: utilidades exclusivas del despliegue web.

## Publicación en Vercel

El proyecto de Vercel continúa conectado a la raíz del repositorio. Para mantener el repositorio ordenado sin cambiar ninguna URL pública, `web/scripts/build-vercel.cjs` genera durante el build una carpeta temporal `.vercel-static/` con únicamente los archivos que deben publicarse.

`vercel.json` usa esa carpeta como `outputDirectory`. `.vercel-static/` es un artefacto generado y está excluido mediante `.gitignore`; nunca debe versionarse.

Se preservan estas URLs públicas:

- `/`
- `/beta`
- `/privacy`
- `/app-ads.txt`
- `/styles.css`
- `/script.js`
- `/beta.js`
- `/privacy.js`
- `/logo1.png`
- `/assets/*`
- `/capturas web/*`

El contenido de `release/`, `scripts/` y este README no forma parte del sitio desplegado.

# Assets de documentación

Esta carpeta contiene únicamente recursos públicos usados por la documentación del repositorio. Los recursos que la aplicación necesita para ejecutarse deben permanecer en `app/src/main/res/` o `app/src/main/assets/`, según corresponda.

## Organización

```text
docs/assets/
├── branding/       Identidad visual oficial de FridaMusic
└── screenshots/    Capturas y composiciones para README/documentación
```

## Convención para nuevos archivos

- Usa nombres en minúsculas, sin espacios ni acentos.
- Usa nombres descriptivos y, para galerías, un prefijo numérico de dos dígitos (`01-`, `02-`, etc.).
- Conserva el formato original salvo que exista una razón documentada para convertirlo.
- Añade cada recurso al README o documento que lo consuma.
- No guardes aquí tokens, claves, capturas con datos personales ni recursos generados durante el build.

Los logos de tecnologías externas se muestran en el README mediante badges enlazados a sus fuentes oficiales; no se copian marcas de terceros a esta carpeta sin revisar sus condiciones de uso.

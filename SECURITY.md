# Política de seguridad

La seguridad y privacidad de los usuarios de FridaMusic se consideran parte del mantenimiento del proyecto.

## Versiones cubiertas

Se prioriza la versión estable más reciente publicada y, cuando corresponda, la rama de desarrollo activa que dará lugar a la siguiente versión. Las versiones antiguas pueden dejar de recibir correcciones cuando el código afectado ya haya cambiado sustancialmente.

## Cómo reportar una vulnerabilidad

**No abras un Issue público** si el reporte contiene una vulnerabilidad explotable, credenciales, tokens, datos personales, bypasses de seguridad o instrucciones que puedan poner a usuarios en riesgo.

Usa el reporte privado de vulnerabilidades de GitHub:

https://github.com/jagrdev-MX/FridaMusic_OF/security/advisories/new

Incluye, cuando sea posible:

- descripción técnica del problema;
- versión o commit afectado;
- pasos mínimos para reproducir;
- impacto estimado;
- evidencia o logs ya saneados;
- propuesta de mitigación si la tienes.

No incluyas datos reales de terceros. Usa valores de prueba y elimina secretos antes de adjuntar logs.

## Qué ocurre después

El mantenedor revisará el reporte, intentará reproducirlo y determinará severidad, alcance y estrategia de corrección. Una corrección puede mantenerse privada hasta que exista una versión segura disponible cuando la divulgación anticipada aumente el riesgo para usuarios.

No se garantiza un SLA comercial, pero los reportes con impacto real sobre usuarios, credenciales, ejecución de código, exposición de datos o cadena de suministro tendrán prioridad.

## Alcance útil

Son especialmente relevantes los reportes relacionados con:

- exposición de credenciales, tokens o claves;
- componentes WebView, intents o deep links inseguros;
- lectura/escritura de archivos fuera del alcance esperado;
- ejecución de código no confiable;
- dependencias comprometidas;
- autenticación o sesiones;
- Firebase, Google Play Billing u otras integraciones que puedan exponer información sensible;
- exportación de logs o reportes de crash con datos privados;
- configuraciones de CI/CD que permitan modificar releases o `master` sin autorización.

## Fuera de alcance habitual

Normalmente no se consideran vulnerabilidades por sí solas:

- detecciones heurísticas sin evidencia reproducible;
- problemas que requieren un dispositivo ya completamente comprometido;
- ingeniería social sin relación con el código o infraestructura oficial;
- fallos visuales o crashes sin impacto de seguridad.

Aun así, si no estás seguro y el posible impacto es sensible, utiliza el canal privado.

## Divulgación responsable

Agradecemos que des tiempo razonable para investigar y corregir antes de publicar detalles técnicos de explotación. Los créditos al investigador pueden incluirse en notas de versión o documentación si la persona lo desea y la divulgación es segura.

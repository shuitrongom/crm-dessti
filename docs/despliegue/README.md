# Paquete de despliegue — CRM Dess-TI / plataforma-multigiro (Windows Server + IIS)

Este paquete contiene todo lo necesario para desplegar el CRM en un servidor
**Windows Server** con **IIS** actuando como proxy inverso: IIS termina TLS,
sirve la SPA de Angular en `/` y enruta `/api` al backend Spring Boot que
escucha localmente en `http://127.0.0.1:8080` (context-path `/api/v1`), con
**PostgreSQL** como base de datos. El backend nunca se expone directamente.

**URL de acceso para pruebas:** https://dessti-aplicaciones.ddns.net:8056

## Archivos de este paquete

| Archivo | Propósito |
| --- | --- |
| `guia-despliegue-iis.html` | Guía principal paso a paso (portada, arquitectura, requisitos, BD, backend, frontend, IIS/TLS/ARR/firewall, servicio de Windows, verificación, problemas, seguridad y actualizaciones). Ábrela en un navegador. |
| `web.config` | Configuración de IIS para la raíz del sitio del frontend (`C:\inetpub\wwwroot\crm`). Proxy inverso de `/api` al backend + *fallback* de la SPA + MIME/cache. |
| `instalar-servicio-backend.ps1` | Script PowerShell que instala el backend como servicio de Windows (`CRM-Backend`) usando **nssm** (recomendado) o **sc.exe** (respaldo). |
| `parametros.example.ps1` | Plantilla de parámetros de arranque del backend (rutas, BD, JWT, cifrado, emisor). Cópiala como `parametros.ps1` y rellena los `[CORCHETES]`. **No versionar** `parametros.ps1`. |
| `README.md` | Este índice. |

## Orden de lectura recomendado

1. **`guia-despliegue-iis.html`** — léela completa primero; es la referencia principal.
2. **`parametros.example.ps1`** — cópiala a `parametros.ps1` y completa tus valores reales de producción.
3. **`web.config`** — colócalo en `C:\inetpub\wwwroot\crm` junto al contenido del frontend (Paso 3 de la guía).
4. **`instalar-servicio-backend.ps1`** — ejecútalo como administrador para crear el servicio del backend (Paso 5 de la guía).

## Notas importantes

- Genera **claves nuevas de producción** para JWT y cifrado; no reutilices las de desarrollo.
- **No abras el puerto 8080** en el firewall: el backend es solo local.
- Habilita **Enable proxy** en ARR; sin esto, `/api` fallará (502/504).
- El frontend usa la base de API relativa `/api/v1` (mismo origen), por lo que **no requiere recompilarse** para el servidor.

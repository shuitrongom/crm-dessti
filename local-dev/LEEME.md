# Ejecucion local del CRM (backend + frontend + Swagger)

Guia para levantar el sistema completo en tu maquina: backend (Spring Boot,
perfil dev) contra TU PostgreSQL, y el frontend (Angular) servido en desarrollo.

## Requisitos
- JDK 21.
- Node.js 20+ y npm.
- PostgreSQL 16 (el TUYO, con tu usuario/clave) — o Docker Desktop si prefieres
  levantarlo en contenedor con el parametro `-UsarDocker`.

## 1) Base de datos
La plataforma usa DOS roles de PostgreSQL (separacion de privilegios, Req 23):

- `dessti_migrator` — rol MIGRADOR (owner del esquema, con BYPASSRLS). Ejecuta
  las migraciones Flyway y siembra las filas de PLATAFORMA (p. ej. roles
  predefinidos con `tenant_id NULL`) que la Row-Level Security rechazaria de otro
  modo. Se usa SOLO para migrar (`spring.flyway.user`).
- `dessti_app` — rol de APLICACION (runtime, NOSUPERUSER, NOBYPASSRLS). Es la
  conexion del backend en operacion, de modo que la RLS multi-tenant (Capa 2)
  SIEMPRE aplica a las consultas de negocio.

Provisiona ambos roles y la base ejecutando UNA VEZ, como superusuario `postgres`:

    psql -h localhost -p 5433 -U postgres -f local-dev\crear-base.sql

Esto crea los roles `dessti_migrator` y `dessti_app`, la base `dessti_plataforma`
(owner: `dessti_migrator`) y los permisos DML del rol de aplicacion. El script es
idempotente (puede re-ejecutarse). Flyway crea y versiona el esquema (migraciones
V1..V48) automaticamente al arrancar el backend. No ejecutes SQL manual del esquema.

> Nota de seguridad: separar el rol migrador (BYPASSRLS, solo DDL/semillas) del
> rol de runtime (NOBYPASSRLS) es lo que hace que la RLS realmente proteja los
> datos de negocio. NUNCA le des BYPASSRLS al rol de aplicacion.

## 2) Backend (API + Swagger) — perfil dev
En una ventana de PowerShell, desde la raiz del proyecto:

    # Usando TU PostgreSQL (ajusta usuario/clave/base):
    .\local-dev\arrancar-backend.ps1 -DbUser postgres -DbPassword TU_CLAVE -DbNombre crm

    # O, si prefieres PostgreSQL en contenedor Docker:
    .\local-dev\arrancar-backend.ps1 -UsarDocker

Parametros disponibles: -DbHost (localhost), -DbPort (5433),
-DbNombre (dessti_plataforma), -DbUser (dessti_app), -DbPassword (Pa55worD),
-DbMigradorUser (dessti_migrator), -DbMigradorPassword (Pa55worD),
-UsarDocker, -JavaHome, -MvnCmd.

Cuando arranque Spring Boot:
- API base:  http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- Health:     http://localhost:8080/api/v1/actuator/health

Las llaves JWT/cifrado de estos scripts son SOLO para desarrollo local; los
secretos reales se resuelven fuera del codigo (Req 11).

## 3) Frontend (Angular) — otra ventana
Con el backend arriba, en una segunda ventana de PowerShell:

    .\local-dev\arrancar-frontend.ps1

La primera vez instala dependencias. Luego sirve la app en:
- http://localhost:4200  (el proxy reenvia /api -> http://localhost:8080)

Inicia sesion; la app enruta por ambito segun el rol del Usuario:
- super_admin  -> /plataforma (empresas, planes/suscripciones, offboarding)
- usuarios de empresa -> /empresa (inicio, comercial, finanzas, RH, social, reportes, ...)
- cliente_portal -> /portal (mis cotizaciones, pruebas de diseno, proyectos, tickets, facturas)

## 4) Verificacion end-to-end (opcional)
Con el backend arriba:

    # Health + OpenAPI:
    .\local-dev\verificar-local.ps1

    # Ademas login + Tablero con datos vivos (Usuario con permiso tablero:leer):
    .\local-dev\verificar-local.ps1 -Usuario admin@empresa -Clave TU_CLAVE

Comprueba que health=UP, que OpenAPI esta publicado, y (si das credenciales)
que el Tablero devuelve indicadores calculados por adaptadores reales (datos
vivos, sin placeholders en cero; el aislamiento por tenant lo garantizan la RLS
de PostgreSQL y el filtro global de Hibernate).

## 5) Detener
- Backend: Ctrl+C en su ventana.
- Frontend: Ctrl+C en su ventana.
- Si usaste `-UsarDocker` para la base:  `.\local-dev\detener-local.ps1`
  (si usaste TU PostgreSQL, sigue corriendo; no requiere accion).

## Pruebas automatizadas (referencia)
- Backend:  `mvn -o clean verify`  (unitarias + integracion con Testcontainers; requiere Docker).
- Frontend: `npm test`  en `frontend/` (unitarias + accesibilidad axe-core).
- E2E frontend (Playwright, opcional, con la pila arriba):
  en `frontend/`:  `npm run e2e:install`  y luego  `npm run e2e`
  (ver `frontend/e2e/README.md` para variables E2E_BASE_URL/E2E_USER/E2E_PASSWORD).

## Scripts
- `arrancar-backend.ps1`  — backend perfil dev contra tu PostgreSQL (o -UsarDocker).
- `arrancar-frontend.ps1` — frontend Angular (ng serve con proxy a /api).
- `verificar-local.ps1`   — health + OpenAPI + (opcional) Tablero con datos vivos.
- `arrancar-local.ps1`    — compatibilidad: delega en arrancar-backend con -UsarDocker.
- `detener-local.ps1`     — detiene el contenedor de PostgreSQL (solo modo Docker).
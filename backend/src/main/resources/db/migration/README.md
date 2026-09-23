# Migraciones de base de datos (Flyway)

Este directorio contiene las migraciones versionadas del esquema PostgreSQL del
CRM de Anuncios Luminosos. Flyway las aplica en orden por su versión (`V1`, `V2`, ...).

## Migraciones

| Versión | Archivo | Contenido |
|---------|---------|-----------|
| V1 | `V1__baseline_plataforma_seguridad.sql` | Baseline: esquema BASE de **plataforma y seguridad** (`empresa`, `plan`, `suscripcion`, `usuario`, `rol`, `permiso`, `rol_permiso`, `usuario_rol`). |

> El esquema de negocio (comercial, operación, facturación, etc.) y la
> **habilitación de Row-Level Security (RLS)** se añaden en migraciones
> posteriores (RLS corresponde a la Tarea 4.2).

## Convenciones

- **PK UUID** generada por la BD con `gen_random_uuid()` (extensión `pgcrypto`,
  creada por `V1`). La aplicación puede también asignar el `id` explícitamente.
- **Tenant-scoped**: las tablas de negocio llevan `tenant_id UUID NOT NULL`,
  `version BIGINT NOT NULL DEFAULT 0` (concurrencia optimista, Req 49) e índice
  por `tenant_id`. La tabla `usuario` y la tabla `rol` permiten `tenant_id NULL`
  para el `super_admin` y los roles predefinidos de sistema, respectivamente.
- **Auditoría temporal** en UTC: `created_at`, `updated_at` (`timestamptz`),
  `created_by`, `updated_by`.
- **Nomenclatura** `snake_case`.

## Decisiones

- **Generación de UUID**: `pgcrypto` + `DEFAULT gen_random_uuid()`.
- **Unicidad de login**: el `identificador_acceso` de `usuario` es **único global**
  (no por tenant), para simplificar el login sin exigir el tenant y evitar
  ambigüedad con el `super_admin` (`tenant_id NULL`).

## Rol de aplicación de PostgreSQL (IMPORTANTE)

El usuario de base de datos con el que se conecta la aplicación **NO debe ser
superusuario ni tener `BYPASSRLS`**. De lo contrario, las políticas de
Row-Level Security que se añadirán en la Tarea 4.2 no se aplicarían y se
perdería la segunda capa de aislamiento multi-tenant (Req 23).

Un guion de referencia (no ejecutado automáticamente por Flyway) está en
`../roles/app_role.sql`. Debe ejecutarse manualmente por un administrador de la
base de datos al aprovisionar el entorno, ajustando la contraseña por fuera del
control de versiones (Req 11, gestión de secretos).

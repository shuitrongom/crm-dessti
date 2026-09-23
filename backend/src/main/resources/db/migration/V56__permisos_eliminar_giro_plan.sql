-- ============================================================================
-- V56__permisos_eliminar_giro_plan.sql
--
-- Habilita la ELIMINACION definitiva de Giros y Planes por el Super_Administrador
-- (plataforma-multigiro). Cubre dos aspectos:
--
--   1) RBAC: siembra los permisos atomicos de plataforma `giro:eliminar` y
--      `plan:eliminar` y los asigna EXCLUSIVAMENTE al rol predefinido
--      `super_admin` (UUID fijo a0000000-0000-0000-0000-000000000001, V5),
--      replicando el patron de asignacion por clave natural (recurso, operacion)
--      de V50 (recurso `giro`) y V5 (recurso `plan`). Ningun rol de empresa los
--      recibe: Spring Security responde 403 (denegacion por defecto) a cualquier
--      otro usuario.
--
--   2) Unicidad del Giro por NOMBRE VISIBLE: crea un indice unico funcional
--      sobre `lower(nombre_visible)`, de modo que no puedan coexistir dos Giros
--      cuyo nombre visible solo difiera en mayusculas/minusculas. Complementa la
--      unicidad ya existente de la clave canonica (`uq_giro_clave`, V50). El
--      servicio (`ServicioGiros.crear`) anticipa el conflicto con 409 y traduce
--      ademas una eventual violacion de este indice en carreras concurrentes.
--
-- No se modifican politicas RLS: `giro` y `plan` son datos de PLATAFORMA sin
-- Row-Level Security (coherente con V50/V1). Migracion idempotente en sus
-- siembras (ON CONFLICT DO NOTHING) y en el indice (IF NOT EXISTS).
--
-- Requisitos cubiertos:
--   - Eliminacion de Giro: solo Giros SIN reglas de negocio programadas y SIN
--     Empresas asociadas (la regla la aplica el servicio; aqui solo el permiso).
--   - Eliminacion de Plan: solo Planes SIN Suscripciones que los referencien
--     (la regla la aplica el servicio; aqui solo el permiso).
--   - Unicidad del Giro por nombre visible (ademas de por clave).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) PERMISOS `giro:eliminar` y `plan:eliminar`
--   Se siembran en el catalogo de permisos atomicos. Idempotente por la clave
--   natural (recurso, operacion) (uq_permiso_recurso_operacion, V1).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES
    ('giro', 'eliminar'),
    ('plan', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2) ASIGNACION a super_admin (id fijo de V5)
--   Enlace por subconsulta sobre la clave natural (recurso, operacion) para no
--   depender del UUID de permiso, replicando V50 seccion 3 y V5 seccion 3.1.
-- ----------------------------------------------------------------------------
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE (p.recurso = 'giro' AND p.operacion = 'eliminar')
   OR (p.recurso = 'plan' AND p.operacion = 'eliminar')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) UNICIDAD DEL GIRO POR NOMBRE VISIBLE (ademas de por clave, V50)
--   Indice unico funcional sobre lower(nombre_visible): impide dos Giros cuyo
--   nombre visible solo difiera en mayusculas/minusculas. No deberia haber
--   duplicados actuales (el unico Giro sembrado es 'Anuncios Luminosos', V50);
--   si los hubiera, la creacion del indice fallaria, alertando de la anomalia
--   antes de continuar. IF NOT EXISTS lo hace idempotente.
-- ----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_giro_nombre_visible
    ON giro (lower(nombre_visible));

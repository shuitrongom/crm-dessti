-- ============================================================================
-- V10__permiso_offboarding_propio.sql
--
-- Permiso de nivel EMPRESA para que el rol predefinido `admin_empresa` solicite
-- la EXPORTACION de los datos de su PROPIA Empresa (Tarea 14.4, Req 69.1).
--
-- ----------------------------------------------------------------------------
-- CONTEXTO
-- ----------------------------------------------------------------------------
--   * El Req 69.1 permite la exportacion de datos de una Empresa "por el
--     Super_Administrador con Permiso, o la Empresa a traves de su
--     Administrador_Empresa".
--   * V5 ya sembro los permisos de NIVEL PLATAFORMA ('offboarding','exportar')
--     y ('offboarding','cambiar_estado'), asignados EXCLUSIVAMENTE al
--     `super_admin`. `offboarding` es un recurso reservado a plataforma
--     (ClasificadorRecursosPlataforma / V5 bloque 3.1), por lo que NO puede
--     concederse a un rol de empresa sin violar el Req 27.7.
--   * Para habilitar la ruta del Administrador_Empresa (Req 69.1) se introduce
--     un recurso de NIVEL EMPRESA distinto, `offboarding_propio`, con la unica
--     operacion `exportar`. La capa de aplicacion (ServicioOffboarding) exige
--     ademas que el `admin_empresa` solo exporte SU tenant (si no, 404,
--     Req 23.3); la eliminacion definitiva NO se concede a nivel empresa.
--
-- Estilo consistente con V5/V9: INSERT del permiso con ON CONFLICT DO NOTHING
-- (clave natural (recurso, operacion), uq_permiso_recurso_operacion de V1) y
-- enlace rol_permiso por subconsulta al UUID fijo del rol predefinido
-- `admin_empresa` (a0000000-0000-0000-0000-000000000002, V5).
-- ============================================================================

-- 1) Catalogo: permiso atomico de nivel empresa ('offboarding_propio','exportar') -- Req 3.1, 69.1
INSERT INTO permiso (recurso, operacion)
VALUES ('offboarding_propio', 'exportar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- 2) Asignacion al rol predefinido `admin_empresa` (UUID fijo de V5) -- Req 69.1, 27.10
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000002', p.id
FROM permiso p
WHERE p.recurso = 'offboarding_propio' AND p.operacion = 'exportar'
ON CONFLICT DO NOTHING;

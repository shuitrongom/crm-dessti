-- ============================================================================
-- V9__permiso_branding_leer.sql
--
-- Permiso de LECTURA de la personalizacion de marca (branding) para el rol
-- predefinido `admin_empresa` (Tarea 14.3, Req 26.2, 27.10).
--
-- ----------------------------------------------------------------------------
-- CONTEXTO
-- ----------------------------------------------------------------------------
--   * La migracion V5 ya sembro el permiso ('branding', 'actualizar') y lo
--     asigno al rol predefinido `admin_empresa` (bloque 3.2), que cubre la
--     configuracion del branding (Req 26.1, 27.10).
--   * Para que el `admin_empresa` pueda CONSULTAR el branding vigente de su
--     Empresa y la interfaz lo aplique (Req 26.2), el endpoint
--     `GET /empresa/branding` exige el permiso ('branding', 'leer'), que V5 no
--     incluia. Esta migracion lo agrega y lo enlaza al MISMO rol.
--   * `branding` es un recurso de NIVEL EMPRESA (no de plataforma): NO se
--     concede al `super_admin` (Req 24.3) ni a otros roles. Req 26 nombra al
--     Administrador_Empresa, por lo que solo `admin_empresa` lo recibe.
--
-- Estilo consistente con V5: INSERT del permiso con ON CONFLICT DO NOTHING
-- (clave natural (recurso, operacion), uq_permiso_recurso_operacion de V1) y
-- enlace rol_permiso por subconsulta al UUID fijo del rol predefinido.
-- ============================================================================

-- 1) Catalogo: permiso atomico ('branding', 'leer') -- Req 3.1, 26.2
INSERT INTO permiso (recurso, operacion)
VALUES ('branding', 'leer')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- 2) Asignacion al rol predefinido `admin_empresa` (UUID fijo de V5) -- Req 26.2, 27.10
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000002', p.id
FROM permiso p
WHERE p.recurso = 'branding' AND p.operacion = 'leer'
ON CONFLICT DO NOTHING;

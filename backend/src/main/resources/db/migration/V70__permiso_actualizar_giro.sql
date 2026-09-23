-- ============================================================================
-- V70__permiso_actualizar_giro.sql
--
-- Bugfix (edicion de Giro): habilita la EDICION de los datos de un Giro
-- (nombre visible y descripcion; la clave canonica es inmutable) por el
-- Super_Administrador. Siembra el permiso atomico giro:actualizar y lo asigna
-- EXCLUSIVAMENTE al rol super_admin (UUID fijo a0000000-...-001, V5), replicando
-- el patron de V50/V56. Idempotente (ON CONFLICT DO NOTHING).
-- ============================================================================

INSERT INTO permiso (recurso, operacion)
VALUES ('giro', 'actualizar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE p.recurso = 'giro' AND p.operacion = 'actualizar'
ON CONFLICT DO NOTHING;

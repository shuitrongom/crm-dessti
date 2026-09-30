-- ============================================================================
-- V85__inventario_avanzado_permiso_lote_actualizar_eliminar.sql
--
-- Mejora enterprise del modulo Inventario Avanzado (Req 60): gestion COMPLETA de
-- Lotes. Hasta ahora el catalogo de Lotes solo permitia crear/leer/listar
-- (permisos sembrados en V5). Se introducen los permisos atomicos dedicados:
--   * lote:actualizar  -> editar un Lote (p. ej. corregir su fecha de caducidad)
--   * lote:eliminar    -> dar de baja un Lote que NO tenga movimientos asociados
--
-- La eliminacion es segura por diseno: el ServicioInventarioAvanzado rechaza
-- (422) borrar un Lote referenciado por movimientos de almacen o capas de costo
-- (FK sin cascada en V26), preservando la integridad contable del inventario.
--
-- COMPATIBILIDAD: ambos permisos se asignan al mismo rol `almacen` (UUID ...008,
-- Req 27.5) que ya opera el inventario avanzado, para no romper el flujo actual.
-- El super_admin puede reasignarlos a un rol dedicado si desea segregar.
--
-- Patron de siembra identico a V75 (INSERT ... ON CONFLICT DO NOTHING sobre
-- `permiso` y asignacion selectiva a `rol_permiso`). Idempotente.
-- ============================================================================

-- 1) Permisos atomicos nuevos (idempotente).
INSERT INTO permiso (recurso, operacion)
VALUES ('lote', 'actualizar'),
       ('lote', 'eliminar')
ON CONFLICT DO NOTHING;

-- 2) Asignacion al rol `almacen` (UUID ...008, Req 27.5) para preservar el flujo
--    actual. La asignacion masiva por recurso de V5 ya se ejecuto y no recogeria
--    estos permisos nuevos, por lo que se asignan aqui explicitamente.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000008', p.id
FROM permiso p
WHERE p.recurso = 'lote' AND p.operacion IN ('actualizar', 'eliminar')
ON CONFLICT DO NOTHING;

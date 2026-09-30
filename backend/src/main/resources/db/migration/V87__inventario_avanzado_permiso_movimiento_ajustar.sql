-- ============================================================================
-- V87__inventario_avanzado_permiso_movimiento_ajustar.sql
--
-- Mejora enterprise del modulo Inventario Avanzado (Req 60): AJUSTE de inventario
-- por conteo fisico. Hasta ahora los movimientos de almacen se limitaban a
-- entradas, salidas y transferencias (permiso movimiento_inventario:crear, V5).
-- El ajuste por conteo fisico (fijar el saldo real de un Material en un Almacen
-- tras un inventario fisico) es una operacion SENSIBLE: puede subir o bajar el
-- saldo sin un documento de compra/venta, por lo que se gobierna con un permiso
-- atomico DEDICADO en lugar de reutilizar movimiento_inventario:crear.
--
--   * movimiento_inventario:ajustar -> registrar un ajuste de inventario que
--     concilia el saldo del sistema con la cantidad contada fisicamente. El motor
--     genera internamente una ENTRADA o SALIDA de tipo 'ajuste' por la diferencia,
--     conservando el costo promedio vigente (no altera la valuacion unitaria).
--
-- El tipo 'ajuste' ya esta admitido en el CHECK de movimiento_almacen.tipo (V26),
-- de modo que NO se requiere cambio de esquema, solo el permiso.
--
-- COMPATIBILIDAD: se asigna al rol `almacen` (UUID ...008, Req 27.5) que ya opera
-- el inventario avanzado. El super_admin puede segregarlo a un rol de auditoria/
-- inventario fisico si lo desea. Patron de siembra idempotente identico a V85.
-- ============================================================================

-- 1) Permiso atomico nuevo (idempotente).
INSERT INTO permiso (recurso, operacion)
VALUES ('movimiento_inventario', 'ajustar')
ON CONFLICT DO NOTHING;

-- 2) Asignacion al rol `almacen` (UUID ...008, Req 27.5).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000008', p.id
FROM permiso p
WHERE p.recurso = 'movimiento_inventario' AND p.operacion = 'ajustar'
ON CONFLICT DO NOTHING;

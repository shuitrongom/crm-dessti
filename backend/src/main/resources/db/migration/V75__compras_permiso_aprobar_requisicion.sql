-- ============================================================================
-- V75__compras_permiso_aprobar_requisicion.sql
--
-- Mejora enterprise del modulo Compras (Fase A): SEGREGACION DE FUNCIONES en la
-- aprobacion de Requisiciones_Compra. Hasta ahora todas las transiciones de
-- estado de la requisicion (enviar, aprobar, rechazar, cancelar) se gobernaban
-- con el unico permiso `requisicion_compra:cambiar_estado`. Se introduce el
-- permiso atomico dedicado `requisicion_compra:aprobar` para que la APROBACION y
-- el RECHAZO (la resolucion de una requisicion enviada) puedan reservarse a un
-- rol autorizador distinto de quien la crea/envia, siguiendo el control interno
-- de compras (best practice P2P 2026: separar solicitante y aprobador).
--
-- El gating por estado destino lo aplica el RequisicionCompraController via SpEL:
--   * destino 'aprobada' | 'rechazada'  -> requiere requisicion_compra:aprobar
--   * destino 'enviada'  | 'cancelada'  -> requiere requisicion_compra:cambiar_estado
--
-- COMPATIBILIDAD: para NO romper el flujo actual, el nuevo permiso se asigna al
-- mismo rol `almacen` (UUID ...008) que ya opera compras; asi quien hoy aprueba
-- sigue pudiendo hacerlo. El super_admin puede reasignar este permiso a un rol
-- aprobador dedicado cuando desee segregar formalmente las funciones.
--
-- Patron de siembra identico a V29 (INSERT ... ON CONFLICT DO NOTHING sobre
-- `permiso` y asignacion selectiva a `rol_permiso`). Idempotente.
-- ============================================================================

-- 1) Permiso atomico nuevo (idempotente).
INSERT INTO permiso (recurso, operacion)
VALUES ('requisicion_compra', 'aprobar')
ON CONFLICT DO NOTHING;

-- 2) Asignacion al rol `almacen` (UUID ...008, Req 27.5) para preservar el flujo
--    actual. La asignacion masiva por recurso de V5 ya se ejecuto y no recogeria
--    este permiso nuevo, por lo que se asigna aqui explicitamente.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000008', p.id
FROM permiso p
WHERE p.recurso = 'requisicion_compra' AND p.operacion = 'aprobar'
ON CONFLICT DO NOTHING;

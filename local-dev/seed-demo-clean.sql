-- ============================================================================
-- seed-demo-clean.sql  --  Borra TODO lo que sembro seed-demo.sql y NADA mas.
--
-- Seguridad: elimina exclusivamente las filas con created_by = 'seed-demo',
-- en orden inverso de dependencias (hijos antes que padres) para respetar las
-- FKs. Tus datos reales (created_by distinto) quedan intactos.
--
-- Uso (superusuario postgres O rol dessti_migrator con BYPASSRLS):
--   psql -h localhost -p 5432 -U dessti_migrator -d dessti_plataforma -f local-dev\seed-demo-clean.sql
--   (password de dessti_migrator: Pa55worD, ver local-dev\crear-base.sql)
-- ============================================================================

\set ON_ERROR_STOP on

DO $clean$
DECLARE
    v_actor TEXT := 'seed-demo';
    v_n     BIGINT := 0;
    v_tmp   BIGINT;
BEGIN
    -- Sin filtrar por tenant: como superusuario / BYPASSRLS se salta RLS. El
    -- filtro por created_by garantiza que solo se borran los datos demo.

    -- ---- 15) Redes sociales ----
    DELETE FROM mensaje_social       WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM conversacion         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM cuenta_canal_social  WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 14) Calidad ISO 9001 (queja antes que accion; accion antes que no_conf) ----
    DELETE FROM queja_cliente        WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM accion_correctiva    WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM no_conformidad       WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM riesgo               WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 13) Instalacion + avance multi-sitio (avance antes que sitio) ----
    DELETE FROM avance_sitio              WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM orden_trabajo_instalacion WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM sitio                WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM proyecto             WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 12) Mantenimiento ----
    DELETE FROM ticket_servicio           WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM contrato_mantenimiento    WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 11) RH / Nomina ----
    DELETE FROM recibo_nomina        WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM nomina               WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM incidencia           WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM contrato_laboral     WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM empleado             WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 10) Presupuestos ----
    DELETE FROM presupuesto          WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 9) Activos fijos ----
    DELETE FROM depreciacion         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM activo_fijo          WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 8) Tesoreria ----
    DELETE FROM movimiento_bancario       WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM conciliacion_bancaria     WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM estado_cuenta_bancario    WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM cuenta_bancaria           WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 7) Contabilidad / Finanzas ----
    DELETE FROM aplicacion_pago      WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM pago_cliente         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM cuenta_por_cobrar    WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM nota_credito         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM factura              WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 6) Compras ----
    DELETE FROM factura_proveedor         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM partida_recepcion         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM recepcion_mercancia       WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM partida_orden_compra      WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM orden_compra              WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM partida_requisicion       WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM requisicion_compra        WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM proveedor                 WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 5) Produccion ----
    DELETE FROM partida_orden_fabricacion WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM orden_fabricacion         WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 3) Inventario avanzado ----
    DELETE FROM movimiento_almacen        WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM capa_costo                WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM lote                      WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM existencia_almacen        WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM config_inventario_material WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM material                  WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM almacen                   WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 4) Comercial / CRM ----
    DELETE FROM partida_cotizacion   WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM cotizacion           WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM oportunidad          WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM contacto             WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM cliente              WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 2) Productos / Lista de precios ----
    DELETE FROM precio_producto      WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM lista_precios        WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM producto             WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    -- ---- 1) Estrategia ----
    DELETE FROM resultado_clave      WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM objetivo_estrategico WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;
    DELETE FROM esencia_empresa      WHERE created_by = v_actor; GET DIAGNOSTICS v_tmp = ROW_COUNT; v_n := v_n + v_tmp;

    RAISE NOTICE 'Limpieza demo COMPLETADA. Filas eliminadas: %', v_n;
END
$clean$;

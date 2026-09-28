-- ============================================================================
-- seed-demo.sql  --  DATOS DE PRUEBA MASIVOS Y COMPLETOS (demo). NO produccion.
--
-- Llena TODOS los modulos con VOLUMEN para ver el comportamiento del CRM/BI:
--   Estrategia, Comercial (clientes/contactos/oportunidades/cotizaciones),
--   Producto/Lista precios, Inventario avanzado (materiales/almacenes/lotes/Kardex),
--   Produccion, Compras (proveedores/requisiciones/ordenes/recepciones/facturas prov),
--   Contabilidad/Finanzas (facturas timbradas/CxC/pagos), Tesoreria (flujo mensual),
--   Activos fijos (+deprec), Presupuestos, RH/Nomina (empleados/contratos/incidencias/
--   nominas/recibos), Mantenimiento (contratos/tickets), Instalacion (proyectos/sitios/
--   OTI) + AVANCE MULTI-SITIO por sucursal, Calidad ISO 9001 (quejas/no conformidades/
--   acciones correctivas/riesgos), Redes sociales (cuentas/conversaciones/mensajes).
--
-- Todo lleva created_by = 'seed-demo'.
--
-- IDEMPOTENTE: al inicio BORRA todo lo demo anterior (created_by='seed-demo') del
-- tenant, en orden inverso de FKs, para poder RE-EJECUTARLO sin choques de unicidad.
-- No toca datos reales (created_by distinto).
--
-- ASCII puro (sin acentos) para evitar problemas WIN1252/UTF8 con psql en Windows.
--
-- Uso (rol dessti_migrator con BYPASSRLS, o superusuario postgres):
--   psql -h localhost -p 5432 -U dessti_migrator -d dessti_plataforma -f local-dev\seed-demo.sql
-- ============================================================================

\set ON_ERROR_STOP on

DO $seed$
DECLARE
    v_tenant       UUID;
    v_nombre       TEXT;
    v_hoy          DATE := CURRENT_DATE;
    v_actor        TEXT := 'seed-demo';
    v_usuario      UUID;
    v_cliente      UUID;
    v_cotizacion   UUID;
    v_factura      UUID;
    v_material     UUID;
    v_almacen      UUID;
    v_almacen2     UUID;
    v_proveedor    UUID;
    v_orden_compra UUID;
    v_of           UUID;
    v_cuenta       UUID;
    v_estado_cta   UUID;
    v_cuenta_social UUID;
    v_conv         UUID;
    v_objetivo     UUID;
    v_lista        UUID;
    v_producto     UUID;
    v_activo       UUID;
    v_empleado     UUID;
    v_nomina       UUID;
    v_proyecto     UUID;
    v_sitio        UUID;
    v_req          UUID;
    v_recepcion    UUID;
    v_partida_oc   UUID;
    v_nc           UUID;
    v_accion       UUID;
    i              INT;
    j              INT;
    v_etapa        TEXT;
    v_estado_cot   TEXT;
    v_estado_of    TEXT;
    v_estado_oc    TEXT;
    v_subtotal     NUMERIC(18,2);
    v_iva          NUMERIC(18,2);
    v_total        NUMERIC(18,2);
    v_canal        TEXT;
    v_costo        NUMERIC(18,2);
    v_residual     NUMERIC(18,2);
    v_deprec       NUMERIC(18,2);
    v_metodo       TEXT;
    v_perc         NUMERIC(18,2);
    v_ded          NUMERIC(18,2);
    v_fecha        DATE;
    v_fase         TEXT;
    materiales     UUID[];
    empleados      UUID[];
    canales_venta  UUID[];
    v_folio_seq    INT := 0;
    v_anio         INT := EXTRACT(YEAR FROM CURRENT_DATE)::int;
BEGIN
    -- ------------------------------------------------------------------
    -- 0) Detectar empresa (tenant) y fijar contexto RLS
    -- ------------------------------------------------------------------
    SELECT id, nombre INTO v_tenant, v_nombre
    FROM empresa WHERE estado = 'activa' ORDER BY created_at ASC LIMIT 1;

    IF v_tenant IS NULL THEN
        RAISE EXCEPTION 'No hay ninguna empresa activa. Crea una empresa en la app antes de sembrar.';
    END IF;

    PERFORM set_config('app.current_tenant', v_tenant::text, false);
    SELECT id INTO v_usuario FROM usuario WHERE tenant_id = v_tenant ORDER BY created_at ASC LIMIT 1;

    RAISE NOTICE 'Sembrando datos demo en la empresa % (tenant %)', v_nombre, v_tenant;

    -- ------------------------------------------------------------------
    -- 0.1) LIMPIEZA idempotente: borra lo demo anterior (orden inverso FKs)
    --      para poder re-ejecutar el seed sin choques de unicidad.
    --      Se hace por DELETE dinamico tolerante a tablas AUN NO EXISTENTES:
    --      si una tabla del bloque nuevo (p. ej. avance_sitio de V78) todavia
    --      no se ha migrado, se omite en vez de abortar todo el seed.
    -- ------------------------------------------------------------------
    DECLARE
        v_tablas TEXT[] := ARRAY[
            'mensaje_social','conversacion','cuenta_canal_social',
            'queja_cliente','accion_correctiva','no_conformidad','riesgo',
            'avance_sitio','orden_trabajo_instalacion','sitio','proyecto',
            'ticket_servicio','contrato_mantenimiento',
            'recibo_nomina','nomina','incidencia','contrato_laboral','empleado',
            'presupuesto','depreciacion','activo_fijo',
            'movimiento_bancario','conciliacion_bancaria','estado_cuenta_bancario','cuenta_bancaria',
            'aplicacion_pago','pago_cliente','cuenta_por_cobrar','nota_credito','factura',
            'cuenta_por_pagar','factura_proveedor','partida_recepcion','recepcion_mercancia',
            'partida_orden_compra','orden_compra','partida_requisicion','requisicion_compra','proveedor',
            'partida_orden_fabricacion','orden_fabricacion',
            'movimiento_almacen','capa_costo','lote','existencia_almacen',
            'config_inventario_material','material','almacen',
            'actividad_comercial',
            'partida_cotizacion','cotizacion','oportunidad','canal_venta','contacto','cliente',
            'precio_producto','lista_precios','producto',
            'resultado_clave','objetivo_estrategico','esencia_empresa'];
        v_tabla TEXT;
    BEGIN
        -- LIMPIEZA POR TENANT (no solo por created_by). Esta es una base de datos
        -- DEMO: todos los datos operativos del tenant se regeneran en la siembra.
        -- Borrar por tenant_id (en el orden hija -> padre del array) elimina tambien
        -- las filas huerfanas que hubieran quedado de corridas anteriores con otro
        -- created_by, que era lo que provocaba violaciones de FK (p. ej. pago_cliente
        -- o aplicacion_pago apuntando a cliente/CxC ya borrados). El aislamiento por
        -- tenant garantiza que NUNCA se tocan datos de otras empresas.
        FOREACH v_tabla IN ARRAY v_tablas LOOP
            -- to_regclass devuelve NULL si la tabla no existe: se omite sin error.
            IF to_regclass('public.' || v_tabla) IS NOT NULL THEN
                EXECUTE format('DELETE FROM %I WHERE tenant_id = $1', v_tabla) USING v_tenant;
            END IF;
        END LOOP;
    END;

    RAISE NOTICE 'Limpieza previa de datos demo COMPLETADA. Iniciando siembra masiva...';

    -- ==================================================================
    -- 1) ESTRATEGIA
    -- ==================================================================
    INSERT INTO esencia_empresa (tenant_id, mision, vision, valores, created_by)
    SELECT v_tenant,
           'Brindar soluciones integrales con calidad y servicio superior, generando valor '
           || 'medible para nuestros clientes.',
           'Ser la empresa lider del mercado nacional para 2030, reconocida por su innovacion '
           || 'y excelencia operativa.',
           'Integridad, Innovacion, Compromiso con el cliente, Trabajo en equipo, Excelencia.',
           v_actor
    WHERE NOT EXISTS (SELECT 1 FROM esencia_empresa WHERE tenant_id = v_tenant);

    DECLARE
        -- Objetivos estrategicos realistas (se ven bien en el home y en el modal).
        v_obj_nombres TEXT[] := ARRAY[
            'Incrementar las ventas anuales',
            'Reducir el tiempo de entrega de instalaciones',
            'Mejorar la satisfaccion del cliente',
            'Optimizar el costo de produccion',
            'Ampliar la cartera de clientes activos',
            'Elevar el margen de utilidad neta',
            'Reducir la rotacion de personal',
            'Digitalizar los procesos administrativos',
            'Mejorar el cumplimiento de SLA de soporte',
            'Aumentar la recompra de clientes existentes',
            'Reducir el inventario inmovilizado',
            'Fortalecer la presencia en redes sociales'];
        v_obj_metas TEXT[] := ARRAY[
            'Crecer 20% las ventas respecto al ano anterior',
            'Bajar el tiempo medio de instalacion a 15 dias',
            'Alcanzar 90% de satisfaccion en encuestas',
            'Reducir 12% el costo unitario de fabricacion',
            'Sumar 150 clientes activos nuevos',
            'Llegar a 18% de margen neto',
            'Mantener la rotacion por debajo del 8%',
            'Digitalizar el 100% de las requisiciones',
            'Cumplir el 95% de los SLA de resolucion',
            'Lograr 35% de recompra anual',
            'Reducir 25% el inventario sin movimiento',
            'Duplicar el alcance mensual en redes'];
        v_obj_resp TEXT[] := ARRAY[
            'Direccion Comercial','Gerencia de Operaciones','Atencion a Clientes',
            'Gerencia de Produccion','Direccion Comercial','Direccion General',
            'Recursos Humanos','Direccion de TI','Gerencia de Soporte',
            'Direccion Comercial','Gerencia de Inventarios','Marketing'];
        v_obj_rc TEXT[] := ARRAY[
            'Cerrar {N} nuevos contratos en el trimestre',
            'Reducir en {N} dias el promedio de entrega',
            'Subir {N} puntos el indice de satisfaccion'];
        v_idx INT;
    BEGIN
        FOR i IN 1..12 LOOP
            v_idx := 1 + ((i - 1) % array_length(v_obj_nombres, 1));
            v_objetivo := gen_random_uuid();
            INSERT INTO objetivo_estrategico
                (id, tenant_id, nombre, responsable, periodo_inicio, periodo_fin, meta, avance, created_by)
            VALUES (v_objetivo, v_tenant,
                    v_obj_nombres[v_idx], v_obj_resp[v_idx],
                    v_hoy - 90, v_hoy + 180, v_obj_metas[v_idx],
                    (i * 8 % 100)::numeric(5,2), v_actor);
            FOR j IN 1..3 LOOP
                INSERT INTO resultado_clave
                    (tenant_id, objetivo_estrategico_id, descripcion, valor_objetivo, valor_actual, peso, created_by)
                VALUES (v_tenant, v_objetivo,
                        replace(v_obj_rc[j], '{N}', (5 * j + i)::text),
                        (100 * (5 * j + i))::numeric(18,4),
                        (100 * (5 * j + i) * (i % 4) / 4.0)::numeric(18,4),
                        CASE j WHEN 1 THEN 40 WHEN 2 THEN 35 ELSE 25 END, v_actor);
            END LOOP;
        END LOOP;
    END;

    -- ==================================================================
    -- 2) PRODUCTOS + LISTA DE PRECIOS
    -- ==================================================================
    v_lista := gen_random_uuid();
    INSERT INTO lista_precios (id, tenant_id, nombre, vigencia_inicio, prioridad, activo, created_by)
    VALUES (v_lista, v_tenant, 'Lista general demo', v_hoy - 60, 1, TRUE, v_actor);

    FOR i IN 1..60 LOOP
        v_producto := gen_random_uuid();
        INSERT INTO producto (id, tenant_id, nombre, unidad, descripcion, activo, created_by)
        VALUES (v_producto, v_tenant, 'Producto Demo ' || i, 'pieza',
                'Descripcion del producto demo ' || i, TRUE, v_actor);
        INSERT INTO precio_producto (tenant_id, lista_precios_id, producto_id, precio, created_by)
        VALUES (v_tenant, v_lista, v_producto, (150 + (i * 137 % 12000))::numeric(18,2), v_actor);
    END LOOP;

    -- ------------------------------------------------------------------
    -- Listas de precios adicionales (Req 59.3): distinta prioridad, segmento y
    -- vigencia para que la demo muestre varias listas y la logica de vigencia
    -- (vigente / futura / vencida). A cada lista se le asignan precios de una
    -- muestra de productos del tenant (con un factor por segmento).
    -- ------------------------------------------------------------------
    DECLARE
        k               INT;
        v_lista_id      UUID;
        v_nombres       TEXT[]    := ARRAY['Lista mayoreo demo','Lista VIP demo','Lista temporada demo'];
        v_segmentos     TEXT[]    := ARRAY['mayoreo','vip','temporada'];
        v_prioridades   INT[]     := ARRAY[5, 10, 3];
        -- Factor de precio por lista (mayoreo mas barato, VIP mas caro).
        v_factores      NUMERIC[] := ARRAY[0.90, 1.15, 1.05];
        -- Vigencia: mayoreo abierta; VIP con fin a 90 dias; temporada ya vencida.
        v_inicios       DATE[]    := ARRAY[v_hoy - 30, v_hoy - 10, v_hoy - 120];
        v_fines         DATE[]    := ARRAY[NULL::date, (v_hoy + 90)::date, (v_hoy - 30)::date];
    BEGIN
        FOR k IN 1..array_length(v_nombres, 1) LOOP
            v_lista_id := gen_random_uuid();
            INSERT INTO lista_precios
                (id, tenant_id, nombre, segmento, prioridad, vigencia_inicio, vigencia_fin, activo, created_by)
            VALUES (v_lista_id, v_tenant, v_nombres[k], v_segmentos[k], v_prioridades[k],
                    v_inicios[k], v_fines[k], TRUE, v_actor);
            -- Asigna precio a 20 productos aleatorios del tenant en cada lista.
            INSERT INTO precio_producto (tenant_id, lista_precios_id, producto_id, precio, created_by)
            SELECT v_tenant, v_lista_id, p.id,
                   round(((300 + (random() * 9000)) * v_factores[k])::numeric, 2)::numeric(18,2), v_actor
            FROM producto p
            WHERE p.tenant_id = v_tenant AND p.created_by = v_actor
            ORDER BY random() LIMIT 20;
        END LOOP;
    END;

    -- ------------------------------------------------------------------
    -- Canales de venta demo (V15, Req 63): vias comerciales tipicas para
    -- clasificar oportunidades y cotizaciones. Se guardan sus ids para
    -- asignarlos despues de forma variada. Idempotente por nombre activo.
    -- ------------------------------------------------------------------
    canales_venta := ARRAY[]::UUID[];
    DECLARE
        k               INT;
        v_canal_id      UUID;
        v_canales_nom   TEXT[] := ARRAY['Directo','Referido','Redes sociales','Marketplace','Distribuidor'];
        v_canales_desc  TEXT[] := ARRAY[
            'Venta directa del equipo comercial.',
            'Cliente llegado por recomendacion de otro cliente.',
            'Prospectos captados por redes sociales.',
            'Ventas por plataformas de comercio electronico.',
            'Ventas a traves de distribuidores o socios.'];
    BEGIN
        FOR k IN 1..array_length(v_canales_nom, 1) LOOP
            v_canal_id := gen_random_uuid();
            INSERT INTO canal_venta (id, tenant_id, nombre, descripcion, activo, created_by)
            VALUES (v_canal_id, v_tenant, v_canales_nom[k], v_canales_desc[k], TRUE, v_actor);
            canales_venta := array_append(canales_venta, v_canal_id);
        END LOOP;
    END;

    -- ==================================================================
    -- 3) INVENTARIO AVANZADO
    -- ==================================================================
    v_almacen := gen_random_uuid();
    INSERT INTO almacen (id, tenant_id, nombre, tipo, activo, created_by)
    VALUES (v_almacen, v_tenant, 'Almacen Central Demo', 'bodega', TRUE, v_actor);
    v_almacen2 := gen_random_uuid();
    INSERT INTO almacen (id, tenant_id, nombre, tipo, activo, created_by)
    VALUES (v_almacen2, v_tenant, 'Sucursal Norte Demo', 'sucursal', TRUE, v_actor);

    materiales := ARRAY[]::UUID[];
    FOR i IN 1..80 LOOP
        v_material := gen_random_uuid();
        materiales := array_append(materiales, v_material);
        v_costo := (50 + i * 3)::numeric(18,4);
        INSERT INTO material
            (id, tenant_id, nombre, stock_minimo, existencias, unidad_medida, activo, created_by)
        VALUES (v_material, v_tenant, 'Material Demo ' || i,
                10, (i * 7 % 300)::numeric(18,3), 'pieza', TRUE, v_actor);

        INSERT INTO config_inventario_material
            (tenant_id, material_id, metodo_costeo, control_lote, consumo_promedio,
             tiempo_entrega_dias, stock_seguridad, created_by)
        VALUES (v_tenant, v_material, CASE WHEN i % 2 = 0 THEN 'peps' ELSE 'promedio' END,
                (i % 3 = 0), (i % 20)::numeric(18,3), (i % 15), 5::numeric(18,3), v_actor);

        INSERT INTO existencia_almacen
            (tenant_id, almacen_id, material_id, cantidad, costo_promedio, created_by)
        VALUES (v_tenant, v_almacen, v_material, (i * 7 % 300)::numeric(18,3), v_costo, v_actor);

        IF i % 3 = 0 THEN
            INSERT INTO lote (tenant_id, material_id, codigo, fecha_caducidad, created_by)
            VALUES (v_tenant, v_material, 'LOTE-' || i, v_hoy + (180 + i), v_actor);
        END IF;

        INSERT INTO movimiento_almacen
            (tenant_id, almacen_id, material_id, tipo, cantidad, costo_unitario, costo_total,
             saldo_cantidad, saldo_costo_total, motivo, created_by)
        VALUES
            (v_tenant, v_almacen, v_material, 'entrada', 100::numeric(18,3), v_costo,
             (100 * v_costo)::numeric(18,4), 100::numeric(18,3), (100 * v_costo)::numeric(18,4),
             'Compra inicial', v_actor),
            (v_tenant, v_almacen, v_material, 'salida', 20::numeric(18,3), v_costo,
             (20 * v_costo)::numeric(18,4), 80::numeric(18,3), (80 * v_costo)::numeric(18,4),
             'Consumo produccion', v_actor);
    END LOOP;

    -- ==================================================================
    -- 4) COMERCIAL / CRM
    -- ==================================================================
    FOR i IN 1..100 LOOP
        v_cliente := gen_random_uuid();
        -- Propietario/vendedor (V81): se asigna el usuario del tenant a ~2/3 de los
        -- clientes para ver la asignacion y dejar el resto "sin asignar".
        INSERT INTO cliente (id, tenant_id, nombre, rfc, email, telefono, activo,
                             propietario_usuario_id, created_by)
        VALUES (v_cliente, v_tenant, 'Cliente Demo ' || i,
                'DEMC' || lpad(i::text, 6, '0') || 'A' || to_char(i % 10, 'FM0'),
                'cliente' || i || '@demo.mx', '55' || lpad((10000000 + i)::text, 8, '0'),
                TRUE, CASE WHEN i % 3 <> 0 THEN v_usuario ELSE NULL END, v_actor);

        INSERT INTO contacto (tenant_id, cliente_id, nombre, activo, created_by)
        VALUES (v_tenant, v_cliente, 'Contacto de Cliente ' || i, TRUE, v_actor);

        FOR j IN 1..(1 + (i % 4)) LOOP
            v_etapa := (ARRAY['nuevo','calificado','propuesta','negociacion','ganado','perdido'])[1 + ((i + j) % 6)];
            -- Forecast (V81): probabilidad sugerida por etapa, fecha de cierre
            -- esperada en las etapas abiertas, y motivo de perdida en las perdidas.
            INSERT INTO oportunidad
                (tenant_id, cliente_id, titulo, valor_estimado, etapa,
                 responsable_usuario_id, canal_venta_id,
                 probabilidad, fecha_cierre_esperada, motivo_perdida, created_by)
            VALUES (v_tenant, v_cliente, 'Oportunidad ' || i || '-' || j,
                    (8000 + ((i * 37 + j * 911) % 300000))::numeric(18,2), v_etapa,
                    CASE WHEN (i + j) % 2 = 0 THEN v_usuario ELSE NULL END,
                    -- Canal de venta variado: ~4 de cada 5 llevan canal (el resto sin canal).
                    CASE WHEN (i + j) % 5 = 0 THEN NULL
                         ELSE canales_venta[1 + ((i + j) % array_length(canales_venta, 1))] END,
                    CASE v_etapa
                        WHEN 'nuevo' THEN 10 WHEN 'calificado' THEN 30
                        WHEN 'propuesta' THEN 50 WHEN 'negociacion' THEN 70
                        WHEN 'ganado' THEN 100 ELSE 0 END,
                    CASE WHEN v_etapa IN ('nuevo','calificado','propuesta','negociacion')
                         THEN v_hoy + (15 + ((i + j) % 60)) ELSE NULL END,
                    CASE WHEN v_etapa = 'perdido'
                         THEN (ARRAY['Precio muy alto','Eligio a la competencia',
                                     'Sin presupuesto este ano','No respondio el cliente'])[1 + ((i + j) % 4)]
                         ELSE NULL END,
                    v_actor);
        END LOOP;

        FOR j IN 1..(1 + (i % 3)) LOOP
            v_estado_cot := (ARRAY['borrador','enviada','aprobada','rechazada'])[1 + ((i + j) % 4)];
            -- Base neta de la partida (subtotal). El desglose fiscal CFDI (V80) se
            -- calcula a partir de ella: descuento por partida, IVA 16%, y en algunas
            -- cotizaciones un descuento global y retenciones.
            DECLARE
                v_base       NUMERIC(18,2) := (5000 + ((i * 53 + j * 313) % 200000))::numeric(18,2);
                v_desc_part  NUMERIC(18,2) := CASE WHEN (i + j) % 5 = 0 THEN round(v_base * 0.10, 2) ELSE 0 END;
                v_base_neta  NUMERIC(18,2);
                v_importe    NUMERIC(18,2);
                v_iva_part   NUMERIC(18,2);
                v_desc_glob  NUMERIC(18,2);
                v_ret_isr    NUMERIC(18,2);
                v_ret_iva    NUMERIC(18,2);
                v_cant       INT := 1 + (i % 5);
                v_precio     NUMERIC(18,2);
            BEGIN
                v_importe   := v_base;                       -- importe bruto de la partida
                v_base_neta := v_base - v_desc_part;         -- base neta = bruto - descuento
                v_iva_part  := round(v_base_neta * 0.16, 2); -- IVA 16% sobre la base neta
                v_precio    := round(v_importe / v_cant, 2);
                -- Descuento global y retenciones solo en algunas cotizaciones (variedad).
                v_desc_glob := CASE WHEN (i + j) % 7 = 0 THEN round(v_base_neta * 0.05, 2) ELSE 0 END;
                v_ret_isr   := CASE WHEN (i + j) % 6 = 0 THEN round(v_base_neta * 0.0125, 2) ELSE 0 END;
                v_ret_iva   := CASE WHEN (i + j) % 6 = 0 THEN round(v_iva_part * 0.6667, 2) ELSE 0 END;
                -- subtotal(cotizacion) = suma base neta; total = subtotal - descGlobal + IVA - retenciones.
                v_subtotal  := v_base_neta;
                v_total     := v_subtotal - v_desc_glob + v_iva_part - v_ret_isr - v_ret_iva;
                v_cotizacion := gen_random_uuid();
                -- Folio legible consecutivo por anio (COT-<anio>-<nnnn>), igual que
                -- el que asigna el backend al crear una cotizacion (V60), para que
                -- la demo muestre folios reales en lugar del id truncado.
                v_folio_seq := v_folio_seq + 1;
                -- Fecha de emision escalonada (hoy menos algunos dias, para variedad)
                -- y vigencia 30 dias despues, de modo que la demo muestre ambas fechas.
                INSERT INTO cotizacion
                    (id, tenant_id, cliente_id, estado, folio, subtotal, descuento_global, iva,
                     retencion_isr, retencion_iva, total, moneda, fecha_emision, valido_hasta,
                     canal_venta_id, created_by)
                VALUES (v_cotizacion, v_tenant, v_cliente, v_estado_cot,
                        'COT-' || v_anio || '-' || lpad(v_folio_seq::text, 4, '0'),
                        v_subtotal, v_desc_glob,
                        v_iva_part, v_ret_isr, v_ret_iva, v_total, 'MXN',
                        (CURRENT_DATE - ((i + j) % 20)),
                        (CURRENT_DATE - ((i + j) % 20) + 30),
                        CASE WHEN (i + j) % 4 = 0 THEN NULL
                             ELSE canales_venta[1 + ((i + j) % array_length(canales_venta, 1))] END,
                        v_actor);
                INSERT INTO partida_cotizacion
                    (tenant_id, cotizacion_id, descripcion, cantidad, precio_unitario,
                     descuento, tasa_iva, importe_base, iva, subtotal, created_by)
                VALUES (v_tenant, v_cotizacion, 'Partida cotizada ' || i || '-' || j,
                        v_cant, v_precio, v_desc_part, '16', v_importe, v_iva_part, v_base_neta, v_actor);
            END;
        END LOOP;

        -- ------------------------------------------------------------------
        -- Actividades de seguimiento (V79): entre 2 y 4 por cliente, mezclando
        -- tipos, estados y vencimientos (algunas pendientes vencidas, otras
        -- completadas, notas historicas) para ver el timeline y la agenda.
        -- ------------------------------------------------------------------
        FOR j IN 1..(2 + (i % 3)) LOOP
            DECLARE
                v_tipo   TEXT := (ARRAY['llamada','correo','reunion','tarea','nota'])[1 + ((i + j) % 5)];
                v_estado TEXT;
                v_prog   TIMESTAMPTZ;
                v_venc   TIMESTAMPTZ;
                v_compl  TIMESTAMPTZ;
            BEGIN
                -- Una nota nace completada; el resto alterna pendiente/completada/cancelada.
                IF v_tipo = 'nota' THEN
                    v_estado := 'completada';
                ELSE
                    v_estado := (ARRAY['pendiente','completada','pendiente','cancelada'])[1 + ((i + j) % 4)];
                END IF;
                -- Programada: la mitad en el pasado, la mitad en el futuro (para vencidas/proximas).
                v_prog := (now() + (((i + j) % 20) - 10 || ' days')::interval);
                v_venc := CASE WHEN v_tipo = 'tarea' THEN v_prog + interval '3 days' ELSE NULL END;
                v_compl := CASE WHEN v_estado = 'completada' THEN v_prog ELSE NULL END;
                INSERT INTO actividad_comercial
                    (tenant_id, cliente_id, tipo, estado, asunto, descripcion,
                     fecha_programada, vencimiento, completada_en, responsable_usuario_id, created_by)
                VALUES (v_tenant, v_cliente, v_tipo, v_estado,
                        (ARRAY['Llamada de seguimiento','Enviar propuesta por correo',
                               'Reunion de presentacion','Tarea: preparar cotizacion',
                               'Nota de la conversacion'])[1 + ((i + j) % 5)],
                        'Seguimiento comercial demo del cliente ' || i || ' (' || v_tipo || ').',
                        v_prog, v_venc, v_compl,
                        CASE WHEN (i + j) % 2 = 0 THEN v_usuario ELSE NULL END, v_actor);
            END;
        END LOOP;
    END LOOP;

    -- Deja el contador de folios de cotizacion al dia (V60) para que las
    -- cotizaciones creadas DESPUES desde la app continuen la numeracion sin
    -- chocar con los folios sembrados. UPSERT sobre (tenant_id, anio).
    IF v_folio_seq > 0 THEN
        INSERT INTO cotizacion_folio_seq (tenant_id, anio, ultimo)
        VALUES (v_tenant, v_anio, v_folio_seq)
        ON CONFLICT (tenant_id, anio)
        DO UPDATE SET ultimo = GREATEST(cotizacion_folio_seq.ultimo, EXCLUDED.ultimo);
    END IF;

    -- ==================================================================
    -- 5) PRODUCCION
    -- ==================================================================
    FOR i IN 1..70 LOOP
        SELECT id INTO v_cliente FROM cliente
        WHERE tenant_id = v_tenant AND created_by = v_actor ORDER BY random() LIMIT 1;
        v_estado_of := (ARRAY['pendiente','en_produccion','terminada','cancelada'])[1 + (i % 4)];
        v_of := gen_random_uuid();
        INSERT INTO orden_fabricacion (id, tenant_id, cliente_id, estado, created_by)
        VALUES (v_of, v_tenant, v_cliente, v_estado_of, v_actor);
        FOR j IN 1..3 LOOP
            INSERT INTO partida_orden_fabricacion
                (tenant_id, orden_fabricacion_id, material_id, cantidad, created_by)
            VALUES (v_tenant, v_of, materiales[1 + ((i + j) % array_length(materiales, 1))],
                    (5 + (i % 20))::numeric(18,4), v_actor);
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- 6) COMPRAS
    -- ==================================================================
    FOR i IN 1..40 LOOP
        v_proveedor := gen_random_uuid();
        INSERT INTO proveedor (id, tenant_id, nombre, rfc, email, telefono, activo, created_by)
        VALUES (v_proveedor, v_tenant, 'Proveedor Demo ' || i,
                'PROV' || lpad(i::text, 6, '0') || 'X',
                'proveedor' || i || '@demo.mx', NULL, TRUE, v_actor);

        v_req := gen_random_uuid();
        INSERT INTO requisicion_compra (id, tenant_id, estado, created_by)
        VALUES (v_req, v_tenant, (ARRAY['borrador','enviada','aprobada'])[1 + (i % 3)], v_actor);
        INSERT INTO partida_requisicion (tenant_id, requisicion_compra_id, material_id, cantidad, created_by)
        VALUES (v_tenant, v_req, materiales[1 + (i % array_length(materiales, 1))],
                (10 + (i % 40))::numeric(18,3), v_actor);

        FOR j IN 1..(1 + (i % 3)) LOOP
            v_estado_oc := (ARRAY['abierta','recibida_parcial','recibida_total','cerrada','cancelada'])[1 + ((i + j) % 5)];
            v_orden_compra := gen_random_uuid();
            INSERT INTO orden_compra (id, tenant_id, proveedor_id, estado, total, created_by)
            VALUES (v_orden_compra, v_tenant, v_proveedor, v_estado_oc,
                    (15000 + (i * 733 + j * 197) % 400000)::numeric(18,2), v_actor);

            v_material := materiales[1 + ((i + j) % array_length(materiales, 1))];
            v_partida_oc := gen_random_uuid();
            INSERT INTO partida_orden_compra
                (id, tenant_id, orden_compra_id, material_id, cantidad, precio_unitario, subtotal, created_by)
            VALUES (v_partida_oc, v_tenant, v_orden_compra, v_material,
                    (5 + (i % 30))::numeric(18,3), (200 + (i * 13 % 6000))::numeric(18,2),
                    ((5 + (i % 30)) * (200 + (i * 13 % 6000)))::numeric(18,2), v_actor);

            IF v_estado_oc IN ('recibida_parcial','recibida_total','cerrada') THEN
                v_recepcion := gen_random_uuid();
                INSERT INTO recepcion_mercancia (id, tenant_id, orden_compra_id, created_by)
                VALUES (v_recepcion, v_tenant, v_orden_compra, v_actor);
                INSERT INTO partida_recepcion
                    (tenant_id, recepcion_mercancia_id, partida_orden_compra_id, material_id,
                     cantidad_recibida, created_by)
                VALUES (v_tenant, v_recepcion, v_partida_oc, v_material,
                        (5 + (i % 30))::numeric(18,3), v_actor);
                DECLARE
                    v_fp_id     UUID := gen_random_uuid();
                    v_fp_estado TEXT := (ARRAY['registrada','conciliada','pagada'])[1 + ((i + j) % 3)];
                    v_fp_monto  NUMERIC(18,2) := (15000 + (i * 733 + j * 197) % 400000)::numeric(18,2);
                BEGIN
                    INSERT INTO factura_proveedor
                        (id, tenant_id, orden_compra_id, proveedor_id, folio_proveedor, monto, estado, created_by)
                    VALUES (v_fp_id, v_tenant, v_orden_compra, v_proveedor, 'FP-' || i || '-' || j,
                            v_fp_monto, v_fp_estado, v_actor);

                    -- Cuenta por pagar derivada de la factura de proveedor conciliada (Req 42.1).
                    -- Se crea una CxP por factura conciliada/pagada; para las conciliadas se deja
                    -- pendiente/parcial con fecha_vencimiento PASADA, de modo que alimenten los
                    -- indicadores de "CxP vencidas" (saldo y conteo) del tablero.
                    IF v_fp_estado IN ('conciliada','pagada') THEN
                        DECLARE
                            v_cxp_estado TEXT := CASE
                                WHEN v_fp_estado = 'pagada' THEN 'pagada'
                                WHEN (i + j) % 2 = 0 THEN 'parcial'
                                ELSE 'pendiente' END;
                            v_cxp_saldo  NUMERIC(18,2) := CASE
                                WHEN v_cxp_estado = 'pagada' THEN 0
                                WHEN v_cxp_estado = 'parcial' THEN round(v_fp_monto * 0.5, 2)
                                ELSE v_fp_monto END;
                            -- La mayoria vencidas (fecha pasada); algunas por vencer (futura).
                            v_cxp_venc   DATE := CASE
                                WHEN (i + j) % 4 = 0 THEN v_hoy + ((5 + (j % 20)))
                                ELSE v_hoy - ((5 + ((i + j) % 40))) END;
                        BEGIN
                            INSERT INTO cuenta_por_pagar
                                (tenant_id, factura_proveedor_id, proveedor_id, total, saldo,
                                 estado, fecha_vencimiento, created_by)
                            VALUES (v_tenant, v_fp_id, v_proveedor, v_fp_monto, v_cxp_saldo,
                                    v_cxp_estado, v_cxp_venc, v_actor);
                        END;
                    END IF;
                END;
            END IF;
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- 7) CONTABILIDAD / FINANZAS
    -- ==================================================================
    FOR i IN 1..150 LOOP
        SELECT c.id, c.cliente_id INTO v_cotizacion, v_cliente
        FROM cotizacion c
        WHERE c.tenant_id = v_tenant AND c.created_by = v_actor ORDER BY random() LIMIT 1;

        v_subtotal := (6000 + (i * 971 % 300000))::numeric(18,2);
        v_iva := round(v_subtotal * 0.16, 2);
        v_total := v_subtotal + v_iva;
        v_factura := gen_random_uuid();

        INSERT INTO factura
            (id, tenant_id, cliente_id, cotizacion_id,
             receptor_rfc, receptor_nombre, receptor_cp, receptor_regimen_fiscal, uso_cfdi,
             subtotal, iva, total, retenciones, estado, folio_fiscal, fecha_timbrado, created_by)
        VALUES (v_factura, v_tenant, v_cliente, v_cotizacion,
                'XAXX010101000', 'Cliente Demo Receptor ' || i, '01000', '601', 'G03',
                v_subtotal, v_iva, v_total, 0, 'timbrada',
                gen_random_uuid(), now() - ((i % 120) || ' days')::interval, v_actor);

        INSERT INTO cuenta_por_cobrar
            (tenant_id, factura_id, cliente_id, total, saldo, estado, fecha_vencimiento, created_by)
        VALUES (v_tenant, v_factura, v_cliente, v_total,
                CASE WHEN i % 3 = 0 THEN 0 ELSE v_total END,
                CASE WHEN i % 3 = 0 THEN 'pagada' ELSE 'pendiente' END,
                CASE WHEN i % 2 = 0 THEN v_hoy - (i % 50) ELSE v_hoy + (i % 50) END, v_actor);

        IF i % 3 = 0 THEN
            INSERT INTO pago_cliente (tenant_id, cliente_id, monto, created_by)
            VALUES (v_tenant, v_cliente, v_total, v_actor);
        END IF;
    END LOOP;

    -- ==================================================================
    -- 8) TESORERIA
    -- ==================================================================
    v_cuenta := gen_random_uuid();
    INSERT INTO cuenta_bancaria (id, tenant_id, nombre, banco, clabe, moneda, activa, created_by)
    VALUES (v_cuenta, v_tenant, 'Cuenta Operativa Demo', 'BBVA',
            '012180001234567890', 'MXN', TRUE, v_actor);

    FOR i IN 0..11 LOOP
        v_fecha := (date_trunc('month', v_hoy) - (i || ' months')::interval)::date + 5;
        v_estado_cta := gen_random_uuid();
        INSERT INTO estado_cuenta_bancario
            (id, tenant_id, cuenta_bancaria_id, periodo_inicio, periodo_fin,
             saldo_inicial, saldo_final, created_by)
        VALUES (v_estado_cta, v_tenant, v_cuenta,
                date_trunc('month', v_fecha)::date,
                (date_trunc('month', v_fecha) + interval '1 month - 1 day')::date,
                (100000 + i * 20000)::numeric(18,2), (150000 + i * 25000)::numeric(18,2), v_actor);
        FOR j IN 1..5 LOOP
            INSERT INTO movimiento_bancario
                (tenant_id, estado_cuenta_bancario_id, cuenta_bancaria_id, fecha, monto,
                 referencia, descripcion, estado_conciliacion, created_by)
            VALUES (v_tenant, v_estado_cta, v_cuenta, v_fecha + j,
                    (30000 + (i * 5000 + j * 3100) % 90000)::numeric(18,2),
                    'DEP-' || i || '-' || j, 'Cobranza cliente',
                    (ARRAY['pendiente','conciliado'])[1 + (j % 2)], v_actor);
        END LOOP;
        FOR j IN 1..4 LOOP
            INSERT INTO movimiento_bancario
                (tenant_id, estado_cuenta_bancario_id, cuenta_bancaria_id, fecha, monto,
                 referencia, descripcion, estado_conciliacion, created_by)
            VALUES (v_tenant, v_estado_cta, v_cuenta, v_fecha + j,
                    -((15000 + (i * 4000 + j * 2600) % 70000)::numeric(18,2)),
                    'RET-' || i || '-' || j, 'Pago proveedor/nomina', 'pendiente', v_actor);
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- 9) ACTIVOS FIJOS + depreciaciones
    -- ==================================================================
    FOR i IN 1..40 LOOP
        v_costo := (20000 + i * 9000)::numeric(18,2);
        v_residual := round(v_costo * 0.1, 2);
        v_deprec := LEAST(round(v_costo * (i % 5) * 0.05, 2), v_costo - v_residual);
        v_metodo := CASE WHEN i % 3 = 0 THEN 'saldos_decrecientes' ELSE 'linea_recta' END;
        v_activo := gen_random_uuid();
        INSERT INTO activo_fijo
            (id, tenant_id, nombre, costo, fecha_adquisicion, vida_util_meses,
             metodo_depreciacion, valor_residual, depreciacion_acumulada, estado, created_by)
        VALUES (v_activo, v_tenant, 'Activo Demo ' || i, v_costo, v_hoy - (i * 40),
                48, v_metodo, v_residual, v_deprec,
                CASE WHEN i % 8 = 0 THEN 'baja' ELSE 'activo' END, v_actor);

        FOR j IN 1..3 LOOP
            INSERT INTO depreciacion
                (tenant_id, activo_fijo_id, periodo, monto, depreciacion_acumulada_resultante, created_by)
            VALUES (v_tenant, v_activo,
                    to_char(v_hoy - ((j - 1) || ' months')::interval, 'YYYY-MM'),
                    round((v_costo - v_residual) / 48, 2),
                    LEAST(round((v_costo - v_residual) / 48 * j, 2), v_costo - v_residual), v_actor);
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- 10) PRESUPUESTOS
    -- ==================================================================
    FOR i IN 0..11 LOOP
        FOREACH v_canal IN ARRAY ARRAY['COMERCIAL','OPERACION','FINANZAS','RH'] LOOP
            INSERT INTO presupuesto
                (tenant_id, area, periodo, ingresos_estimados, egresos_estimados, created_by)
            VALUES (v_tenant, v_canal,
                    to_char(date_trunc('month', v_hoy) - (i || ' months')::interval, 'YYYY-MM'),
                    (200000 + i * 15000)::numeric(18,2), (150000 + i * 12000)::numeric(18,2), v_actor)
            ON CONFLICT (tenant_id, area, periodo) DO NOTHING;
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- 11) RH / NOMINA
    -- ==================================================================
    empleados := ARRAY[]::UUID[];
    FOR i IN 1..50 LOOP
        v_empleado := gen_random_uuid();
        empleados := array_append(empleados, v_empleado);
        INSERT INTO empleado
            (id, tenant_id, nombre, rfc, curp, nss, fecha_ingreso, activo, created_by)
        VALUES (v_empleado, v_tenant, 'Empleado Demo ' || i,
                'EMPD' || lpad(i::text, 6, '0') || 'X',
                'EMPD' || lpad(i::text, 6, '0') || 'HDFXYZ' || to_char(i % 100, 'FM00'),
                lpad((10000000000 + i)::text, 11, '0'),
                v_hoy - (365 + i * 20), TRUE, v_actor);

        INSERT INTO contrato_laboral
            (tenant_id, empleado_id, tipo, salario_diario, periodicidad, fecha_inicio, activo, created_by)
        VALUES (v_tenant, v_empleado,
                (ARRAY['indeterminado','determinado','obra','capacitacion'])[1 + (i % 4)],
                (400 + (i * 37 % 2000))::numeric(18,2),
                (ARRAY['semanal','quincenal','mensual'])[1 + (i % 3)],
                v_hoy - (365 + i * 20), TRUE, v_actor);

        INSERT INTO incidencia (tenant_id, empleado_id, periodo_nomina, tipo, cantidad, descripcion, created_by)
        VALUES (v_tenant, v_empleado, to_char(v_hoy, 'YYYY-MM'),
                (ARRAY['asistencia','falta','permiso','incapacidad','tiempo_extra'])[1 + (i % 5)],
                (i % 8)::numeric(18,2), 'Incidencia demo ' || i, v_actor);
    END LOOP;

    FOR i IN 0..5 LOOP
        v_nomina := gen_random_uuid();
        INSERT INTO nomina (id, tenant_id, periodo_nomina, estado, created_by)
        VALUES (v_nomina, v_tenant,
                to_char(date_trunc('month', v_hoy) - (i || ' months')::interval, 'YYYY-MM'),
                (ARRAY['pagada','pagada','timbrada','autorizada','calculada','borrador'])[1 + i], v_actor);

        FOR j IN 1..array_length(empleados, 1) LOOP
            v_perc := (12000 + (j * 311) % 25000)::numeric(18,2);
            v_ded := round(v_perc * 0.18, 2);
            INSERT INTO recibo_nomina
                (tenant_id, nomina_id, empleado_id, percepciones, deducciones, neto, estado, created_by)
            VALUES (v_tenant, v_nomina, empleados[j], v_perc, v_ded, v_perc - v_ded,
                    CASE WHEN i <= 2 THEN 'timbrado' ELSE 'calculado' END, v_actor);
        END LOOP;

        UPDATE nomina n SET
            total_percepciones = sub.p, total_deducciones = sub.d, total_neto = sub.n
        FROM (SELECT SUM(percepciones) p, SUM(deducciones) d, SUM(neto) n
              FROM recibo_nomina WHERE nomina_id = v_nomina) sub
        WHERE n.id = v_nomina;
    END LOOP;

    -- ==================================================================
    -- 12) MANTENIMIENTO
    -- ==================================================================
    FOR i IN 1..25 LOOP
        SELECT id INTO v_cliente FROM cliente
        WHERE tenant_id = v_tenant AND created_by = v_actor ORDER BY random() LIMIT 1;
        DECLARE v_contrato_m UUID := gen_random_uuid();
        BEGIN
            INSERT INTO contrato_mantenimiento
                (id, tenant_id, cliente_id, tipo, sla_respuesta_horas, sla_resolucion_horas, activo, created_by)
            VALUES (v_contrato_m, v_tenant, v_cliente,
                    (ARRAY['preventivo','correctivo'])[1 + (i % 2)], 4, 24, TRUE, v_actor);
            FOR j IN 1..4 LOOP
                DECLARE
                    v_estado_tk TEXT := (ARRAY['abierto','asignado','en_proceso','resuelto','cerrado'])[1 + ((i + j) % 5)];
                    v_resuelto  BOOLEAN := v_estado_tk IN ('resuelto','cerrado');
                    v_abierto_en TIMESTAMPTZ := now() - ((10 + ((i + j) % 20)) || ' days')::interval;
                    -- Alterna cumplimiento del SLA para que haya cumplidos e incumplidos.
                    v_sla_resp  BOOLEAN := ((i + j) % 4) <> 0;   -- ~75% cumple respuesta
                    v_sla_reso  BOOLEAN := ((i + j) % 3) <> 0;   -- ~66% cumple resolucion
                BEGIN
                    INSERT INTO ticket_servicio
                        (tenant_id, contrato_mantenimiento_id, cliente_id, origen, estado,
                         abierto_en, resuelto_en, sla_respuesta_cumplido, sla_resolucion_cumplido, created_by)
                    VALUES (v_tenant, v_contrato_m, v_cliente,
                            (ARRAY['manual','preventivo'])[1 + (j % 2)],
                            v_estado_tk,
                            v_abierto_en,
                            -- Solo los tickets resueltos/cerrados llevan resuelto_en y banderas SLA
                            -- (asi alimentan los indicadores de cumplimiento de SLA del tablero).
                            CASE WHEN v_resuelto THEN v_abierto_en + ((6 + (j * 3)) || ' hours')::interval ELSE NULL END,
                            CASE WHEN v_resuelto THEN v_sla_resp ELSE NULL END,
                            CASE WHEN v_resuelto THEN v_sla_reso ELSE NULL END,
                            v_actor);
                END;
            END LOOP;
        END;
    END LOOP;

    -- ==================================================================
    -- 13) INSTALACION / PROYECTOS MULTI-SITIO (varias sucursales por proyecto)
    --     + AVANCE MULTI-SITIO por sucursal (fase editable de giros genericos)
    -- ==================================================================
    FOR i IN 1..20 LOOP
        SELECT id INTO v_cliente FROM cliente
        WHERE tenant_id = v_tenant AND created_by = v_actor ORDER BY random() LIMIT 1;
        v_proyecto := gen_random_uuid();
        INSERT INTO proyecto (id, tenant_id, cliente_id, nombre, created_by)
        VALUES (v_proyecto, v_tenant, v_cliente, 'Proyecto Multi-sitio ' || i, v_actor);

        -- Entre 3 y 7 sucursales por proyecto, cada una con su avance.
        FOR j IN 1..(3 + (i % 5)) LOOP
            v_sitio := gen_random_uuid();
            INSERT INTO sitio (id, tenant_id, proyecto_id, nombre, direccion, created_by)
            VALUES (v_sitio, v_tenant, v_proyecto, 'Sucursal ' || i || '-' || j,
                    'Av. Principal ' || (100 + j) || ', Ciudad ' || i, v_actor);

            -- Avance multi-sitio: solo si la migracion V78 ya creo la tabla.
            IF to_regclass('public.avance_sitio') IS NOT NULL THEN
                v_fase := (ARRAY['pendiente','en_preparacion','en_instalacion','entregado'])[1 + ((i + j) % 4)];
                INSERT INTO avance_sitio (tenant_id, sitio_id, fase, nota, created_by)
                VALUES (v_tenant, v_sitio, v_fase,
                        'Avance de la sucursal ' || i || '-' || j, v_actor);
            END IF;

            -- OTI para algunas sucursales (referencia debil a orden de fabricacion).
            IF j % 2 = 0 THEN
                SELECT id INTO v_of FROM orden_fabricacion
                WHERE tenant_id = v_tenant AND created_by = v_actor ORDER BY random() LIMIT 1;
                IF v_of IS NOT NULL THEN
                    INSERT INTO orden_trabajo_instalacion
                        (tenant_id, orden_fabricacion_id, sitio_id, cuadrilla_id, cliente_id,
                         fecha_programada, estado, created_by)
                    VALUES (v_tenant, v_of, v_sitio, gen_random_uuid(), v_cliente,
                            v_hoy + (i % 30),
                            (ARRAY['programada','en_curso','completada','cancelada'])[1 + (j % 4)], v_actor);
                END IF;
            END IF;
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- 14) CALIDAD ISO 9001: quejas + no conformidades + acciones + riesgos
    -- ==================================================================
    -- Quejas de cliente
    FOR i IN 1..25 LOOP
        SELECT id INTO v_cliente FROM cliente
        WHERE tenant_id = v_tenant AND created_by = v_actor ORDER BY random() LIMIT 1;
        INSERT INTO queja_cliente
            (tenant_id, cliente_id, origen, descripcion, estado, created_by)
        VALUES (v_tenant, v_cliente,
                (ARRAY['portal','social','correo','telefono','otro'])[1 + (i % 5)],
                'Queja demo ' || i || ': el cliente reporta una incidencia de servicio.',
                (ARRAY['registrada','atendida'])[1 + (i % 2)], v_actor);
    END LOOP;

    -- Riesgos del SGC (nivel derivado; probabilidad x impacto)
    FOR i IN 1..20 LOOP
        INSERT INTO riesgo
            (tenant_id, descripcion, probabilidad, impacto, nivel_derivado, acciones, estado, created_by)
        VALUES (v_tenant, 'Riesgo demo ' || i || ': posible desviacion en el proceso.',
                (ARRAY['baja','media','alta'])[1 + (i % 3)],
                (ARRAY['bajo','medio','alto'])[1 + ((i + 1) % 3)],
                (ARRAY['bajo','medio','alto','critico'])[1 + (i % 4)],
                'Plan de mitigacion del riesgo ' || i,
                (ARRAY['identificado','en_tratamiento','mitigado','aceptado'])[1 + (i % 4)], v_actor);
    END LOOP;

    -- No conformidades + accion correctiva vinculada (requiere responsable = usuario)
    FOR i IN 1..15 LOOP
        v_nc := gen_random_uuid();
        INSERT INTO no_conformidad
            (id, tenant_id, origen, descripcion, proceso_afectado, estado, created_by)
        VALUES (v_nc, v_tenant,
                (ARRAY['queja','auditoria_interna','proceso','proveedor','otro'])[1 + (i % 5)],
                'No conformidad demo ' || i || ': hallazgo en el proceso.',
                'Proceso ' || (1 + (i % 6)),
                (ARRAY['abierta','en_tratamiento','cerrada'])[1 + (i % 3)], v_actor);

        -- La accion correctiva exige responsable_id (usuario). Solo si hay usuario.
        IF v_usuario IS NOT NULL THEN
            v_accion := gen_random_uuid();
            INSERT INTO accion_correctiva
                (id, tenant_id, no_conformidad_id, responsable_id, causa_raiz, acciones_planificadas,
                 eficacia_verificada, estado, created_by)
            VALUES (v_accion, v_tenant, v_nc, v_usuario,
                    'Causa raiz identificada para la NC ' || i,
                    'Acciones planificadas para corregir la NC ' || i,
                    (i % 3 = 0),
                    (ARRAY['abierta','en_analisis','en_ejecucion','verificacion'])[1 + (i % 4)], v_actor);
        END IF;
    END LOOP;

    -- ==================================================================
    -- 15) REDES SOCIALES
    -- ==================================================================
    FOR i IN 1..3 LOOP
        v_canal := (ARRAY['whatsapp','messenger','instagram'])[i];
        v_cuenta_social := gen_random_uuid();
        INSERT INTO cuenta_canal_social
            (id, tenant_id, canal, identificador_externo, nombre, credenciales_ref, activa, created_by)
        VALUES (v_cuenta_social, v_tenant, v_canal, 'ext-' || v_canal || '-demo',
                'Cuenta ' || v_canal || ' demo', 'secreto-ref-' || v_canal, TRUE, v_actor);
        FOR j IN 1..30 LOOP
            v_conv := gen_random_uuid();
            DECLARE
                -- Marca temporal del mensaje entrante (distribuida en los ultimos 60 dias).
                v_recibido TIMESTAMPTZ := now() - ((1 + ((i + j) % 60)) || ' days')::interval;
                -- La respuesta saliente llega minutos/horas despues (alimenta tiempo de respuesta).
                v_enviado  TIMESTAMPTZ := (now() - ((1 + ((i + j) % 60)) || ' days')::interval)
                                          + ((10 + (j * 7)) || ' minutes')::interval;
                -- Una de cada tres conversaciones se vincula a un cliente (cuenta como lead).
                v_cli_conv UUID;
            BEGIN
                SELECT id INTO v_cliente FROM cliente
                WHERE tenant_id = v_tenant AND created_by = v_actor ORDER BY random() LIMIT 1;
                v_cli_conv := CASE WHEN j % 3 = 0 THEN v_cliente ELSE NULL END;

                INSERT INTO conversacion
                    (id, tenant_id, cuenta_canal_social_id, canal, remitente_externo, cliente_id, estado, created_by)
                VALUES (v_conv, v_tenant, v_cuenta_social, v_canal, 'contacto-' || i || '-' || j,
                        v_cli_conv,
                        (ARRAY['abierta','asignada','cerrada'])[1 + (j % 3)], v_actor);

                -- Mensaje entrante (recibido_en) + respuesta saliente (enviado_en): ambos con marca
                -- temporal para que alimenten mensajes_recibidos/enviados, alcance y tiempo de respuesta.
                INSERT INTO mensaje_social
                    (tenant_id, conversacion_id, direccion, tipo, contenido, recibido_en, enviado_en, created_by)
                VALUES
                    (v_tenant, v_conv, 'entrante', 'texto', 'Hola, me interesa su producto.', v_recibido, NULL, v_actor),
                    (v_tenant, v_conv, 'saliente', 'texto', 'Con gusto le comparto informacion.', NULL, v_enviado, v_actor);
            END;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Seed demo MASIVO COMPLETO en tenant %.', v_tenant;
END
$seed$;

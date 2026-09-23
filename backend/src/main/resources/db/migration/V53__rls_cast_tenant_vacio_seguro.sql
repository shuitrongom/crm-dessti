-- ============================================================================
-- V53__rls_cast_tenant_vacio_seguro.sql
--
-- CAST DE TENANT SEGURO ANTE CADENA VACIA en TODAS las politicas RLS (Req 23, 27).
--
-- ----------------------------------------------------------------------------
-- MOTIVACION (segundo defecto real de RLS corregido, reproducido en produccion)
-- ----------------------------------------------------------------------------
-- `TenantSessionInitializer` fija el tenant con
--     SELECT set_config('app.current_tenant', <uuid>, true)
-- El tercer parametro `true` hace que la variable sea LOCAL A LA TRANSACCION.
-- Cuando esa transaccion termina, PostgreSQL revierte la GUC personalizada
-- `app.current_tenant` a su valor de reset DENTRO de esa sesion fisica del pool.
-- Ese valor de reset NO es NULL: una vez que una GUC personalizada ha sido
-- tocada en una sesion, su valor de reset es la CADENA VACIA ''. El pool de
-- conexiones reutiliza esa misma conexion fisica para la siguiente peticion.
--
-- La siguiente peticion que toca una tabla protegida por RLS ANTES de aplicar
-- un nuevo tenant (p. ej. el LOGIN, que resuelve al usuario por
-- identificador_acceso antes de que exista tenant en contexto) evalua politicas
-- del tipo:
--     tenant_id = current_setting('app.current_tenant', true)::uuid
-- Con la GUC = '', el cast ''::uuid lanza
--     ERROR: invalid input syntax for type uuid: ""  (SQLState 22P02)
-- que se manifiesta como HTTP 500.
--
-- Reproducido en psql como el rol de aplicacion:
--     SET app.current_tenant = '';
--     SELECT ... FROM usuario WHERE identificador_acceso='...';
--       -> ERROR: invalid input syntax for type uuid: ""
--
-- ----------------------------------------------------------------------------
-- POR QUE EL GUARD `= ''` DE V48 NO BASTA
-- ----------------------------------------------------------------------------
-- Las politicas de lectura de V48 (usuario_lectura, rol_lectura) agregaron un
-- termino `OR current_setting(...) = ''`. Eso NO resuelve el problema: dentro de
-- un OR, PostgreSQL puede evaluar igualmente la rama del cast `::uuid`, y un
-- error de cast EN TIEMPO DE EJECUCION aborta toda la expresion en lugar de
-- cortocircuitar a verdadero. El guard es insuficiente porque el problema es el
-- CAST mismo, no la comparacion. Ademas, las 87 politicas `tenant_isolation` de
-- negocio nunca tuvieron guard alguno.
--
-- ----------------------------------------------------------------------------
-- LA CORRECCION (aprobada): CAST SEGURO ANTE CADENA VACIA con NULLIF
-- ----------------------------------------------------------------------------
-- Se redefine CADA politica RLS con alcance de tenant para que el cast sea
-- seguro ante cadena vacia usando:
--     NULLIF(current_setting('app.current_tenant', true), '')::uuid
-- `NULLIF(x, '')` devuelve NULL cuando la GUC esta ausente o vacia, por lo que
-- el cast se convierte en NULL::uuid (sin error), y `tenant_id = NULL` es NULL
-- (la fila no es visible ni insertable): el comportamiento seguro y correcto.
-- Esto ELIMINA por completo la clase de fallo de la cadena vacia y PRESERVA
-- EXACTAMENTE la semantica de aislamiento: un tenant concreto sigue coincidiendo
-- solo con SUS propias filas. Los NOMBRES y COMANDOS de las politicas se
-- conservan identicos; solo cambia el cast para hacerlo seguro.
--
-- No se re-habilita ROW LEVEL SECURITY (ya esta habilitada); solo se redefinen
-- politicas. `empresa` no se toca (intencionalmente sin RLS).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) Tablas de negocio: politica unica `tenant_isolation` (FOR ALL).
--    Se redefinen las 87 tablas con cast seguro ante cadena vacia.
-- ----------------------------------------------------------------------------

DROP POLICY IF EXISTS tenant_isolation ON accion_correctiva;
CREATE POLICY tenant_isolation ON accion_correctiva
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON activo_fijo;
CREATE POLICY tenant_isolation ON activo_fijo
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON alerta_auditoria;
CREATE POLICY tenant_isolation ON alerta_auditoria
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON almacen;
CREATE POLICY tenant_isolation ON almacen
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON aplicacion_pago;
CREATE POLICY tenant_isolation ON aplicacion_pago
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON asignacion_puesto;
CREATE POLICY tenant_isolation ON asignacion_puesto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cambio_sgc;
CREATE POLICY tenant_isolation ON cambio_sgc
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON campana_publicitaria;
CREATE POLICY tenant_isolation ON campana_publicitaria
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON canal_venta;
CREATE POLICY tenant_isolation ON canal_venta
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON capa_costo;
CREATE POLICY tenant_isolation ON capa_costo
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cliente;
CREATE POLICY tenant_isolation ON cliente
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON conciliacion_bancaria;
CREATE POLICY tenant_isolation ON conciliacion_bancaria
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON config_inventario_material;
CREATE POLICY tenant_isolation ON config_inventario_material
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON consentimiento_canal;
CREATE POLICY tenant_isolation ON consentimiento_canal
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON contacto;
CREATE POLICY tenant_isolation ON contacto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON contexto_organizacion;
CREATE POLICY tenant_isolation ON contexto_organizacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON contrato_laboral;
CREATE POLICY tenant_isolation ON contrato_laboral
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON contrato_mantenimiento;
CREATE POLICY tenant_isolation ON contrato_mantenimiento
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON conversacion;
CREATE POLICY tenant_isolation ON conversacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cotizacion;
CREATE POLICY tenant_isolation ON cotizacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cuenta_bancaria;
CREATE POLICY tenant_isolation ON cuenta_bancaria
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cuenta_canal_social;
CREATE POLICY tenant_isolation ON cuenta_canal_social
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cuenta_contable;
CREATE POLICY tenant_isolation ON cuenta_contable
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cuenta_por_cobrar;
CREATE POLICY tenant_isolation ON cuenta_por_cobrar
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON cuenta_por_pagar;
CREATE POLICY tenant_isolation ON cuenta_por_pagar
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON depreciacion;
CREATE POLICY tenant_isolation ON depreciacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON empleado;
CREATE POLICY tenant_isolation ON empleado
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON esencia_empresa;
CREATE POLICY tenant_isolation ON esencia_empresa
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON estado_cuenta_bancario;
CREATE POLICY tenant_isolation ON estado_cuenta_bancario
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON evaluacion_desempeno;
CREATE POLICY tenant_isolation ON evaluacion_desempeno
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON evidencia_instalacion;
CREATE POLICY tenant_isolation ON evidencia_instalacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON existencia_almacen;
CREATE POLICY tenant_isolation ON existencia_almacen
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON factura;
CREATE POLICY tenant_isolation ON factura
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON factura_proveedor;
CREATE POLICY tenant_isolation ON factura_proveedor
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON historial_avance_objetivo;
CREATE POLICY tenant_isolation ON historial_avance_objetivo
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON incidencia;
CREATE POLICY tenant_isolation ON incidencia
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON intento_envio_notificacion;
CREATE POLICY tenant_isolation ON intento_envio_notificacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON intento_publicacion;
CREATE POLICY tenant_isolation ON intento_publicacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON levantamiento_foto;
CREATE POLICY tenant_isolation ON levantamiento_foto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON levantamiento_sitio;
CREATE POLICY tenant_isolation ON levantamiento_sitio
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON lista_precios;
CREATE POLICY tenant_isolation ON lista_precios
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON lote;
CREATE POLICY tenant_isolation ON lote
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON material;
CREATE POLICY tenant_isolation ON material
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON mensaje_social;
CREATE POLICY tenant_isolation ON mensaje_social
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON movimiento_almacen;
CREATE POLICY tenant_isolation ON movimiento_almacen
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON movimiento_bancario;
CREATE POLICY tenant_isolation ON movimiento_bancario
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON movimiento_inventario;
CREATE POLICY tenant_isolation ON movimiento_inventario
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON movimiento_poliza;
CREATE POLICY tenant_isolation ON movimiento_poliza
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON no_conformidad;
CREATE POLICY tenant_isolation ON no_conformidad
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON nomina;
CREATE POLICY tenant_isolation ON nomina
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON nota_credito;
CREATE POLICY tenant_isolation ON nota_credito
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON notificacion;
CREATE POLICY tenant_isolation ON notificacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON objetivo_estrategico;
CREATE POLICY tenant_isolation ON objetivo_estrategico
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON oportunidad;
CREATE POLICY tenant_isolation ON oportunidad
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON oportunidad_calidad;
CREATE POLICY tenant_isolation ON oportunidad_calidad
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON orden_compra;
CREATE POLICY tenant_isolation ON orden_compra
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON orden_fabricacion;
CREATE POLICY tenant_isolation ON orden_fabricacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON orden_trabajo_instalacion;
CREATE POLICY tenant_isolation ON orden_trabajo_instalacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON pago_cliente;
CREATE POLICY tenant_isolation ON pago_cliente
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON partida_cotizacion;
CREATE POLICY tenant_isolation ON partida_cotizacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON partida_orden_compra;
CREATE POLICY tenant_isolation ON partida_orden_compra
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON partida_recepcion;
CREATE POLICY tenant_isolation ON partida_recepcion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON partida_requisicion;
CREATE POLICY tenant_isolation ON partida_requisicion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON pendiente_instalacion;
CREATE POLICY tenant_isolation ON pendiente_instalacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON permiso_instalacion;
CREATE POLICY tenant_isolation ON permiso_instalacion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON plantilla_mensaje;
CREATE POLICY tenant_isolation ON plantilla_mensaje
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON poliza_contable;
CREATE POLICY tenant_isolation ON poliza_contable
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON precio_producto;
CREATE POLICY tenant_isolation ON precio_producto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON presupuesto;
CREATE POLICY tenant_isolation ON presupuesto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON producto;
CREATE POLICY tenant_isolation ON producto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON programacion_pago;
CREATE POLICY tenant_isolation ON programacion_pago
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON proveedor;
CREATE POLICY tenant_isolation ON proveedor
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON proyecto;
CREATE POLICY tenant_isolation ON proyecto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON prueba_diseno;
CREATE POLICY tenant_isolation ON prueba_diseno
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON publicacion_social;
CREATE POLICY tenant_isolation ON publicacion_social
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON puesto;
CREATE POLICY tenant_isolation ON puesto
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON queja_cliente;
CREATE POLICY tenant_isolation ON queja_cliente
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON recepcion_mercancia;
CREATE POLICY tenant_isolation ON recepcion_mercancia
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON recibo_nomina;
CREATE POLICY tenant_isolation ON recibo_nomina
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON requisicion_compra;
CREATE POLICY tenant_isolation ON requisicion_compra
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON resultado_clave;
CREATE POLICY tenant_isolation ON resultado_clave
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON riesgo;
CREATE POLICY tenant_isolation ON riesgo
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON sitio;
CREATE POLICY tenant_isolation ON sitio
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON suscripcion;
CREATE POLICY tenant_isolation ON suscripcion
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON tablero_personalizado;
CREATE POLICY tenant_isolation ON tablero_personalizado
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON ticket_servicio;
CREATE POLICY tenant_isolation ON ticket_servicio
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation ON widget_tablero;
CREATE POLICY tenant_isolation ON widget_tablero
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ----------------------------------------------------------------------------
-- 2) rol: politicas por comando de V48, ahora con cast seguro ante cadena vacia.
--    NULLIF(...,'') colapsa el caso vacio en el termino IS NULL, por lo que el
--    termino explicito `= ''` ya no es necesario en la lectura.
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS rol_lectura ON rol;
CREATE POLICY rol_lectura ON rol
    FOR SELECT
    USING (
        tenant_id IS NULL
        OR NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

DROP POLICY IF EXISTS rol_insercion ON rol;
CREATE POLICY rol_insercion ON rol
    FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS rol_actualizacion ON rol;
CREATE POLICY rol_actualizacion ON rol
    FOR UPDATE
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS rol_borrado ON rol;
CREATE POLICY rol_borrado ON rol
    FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ----------------------------------------------------------------------------
-- 3) usuario: politicas por comando de V48, ahora con cast seguro ante cadena
--    vacia. usuario_login_mutacion sigue permitiendo la mutacion de la ventana
--    de login (contadores de intentos fallidos) cuando NO hay tenant fijado;
--    NULLIF(...,'') IS NULL captura tanto la variable ausente como la cadena
--    vacia (el estado real tras el reset transaccional en el pool).
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS usuario_lectura ON usuario;
CREATE POLICY usuario_lectura ON usuario
    FOR SELECT
    USING (
        tenant_id IS NULL
        OR NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

DROP POLICY IF EXISTS usuario_insercion ON usuario;
CREATE POLICY usuario_insercion ON usuario
    FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS usuario_actualizacion ON usuario;
CREATE POLICY usuario_actualizacion ON usuario
    FOR UPDATE
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS usuario_borrado ON usuario;
CREATE POLICY usuario_borrado ON usuario
    FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS usuario_login_mutacion ON usuario;
CREATE POLICY usuario_login_mutacion ON usuario
    FOR UPDATE
    USING      (NULLIF(current_setting('app.current_tenant', true), '') IS NULL)
    WITH CHECK (NULLIF(current_setting('app.current_tenant', true), '') IS NULL);

-- ----------------------------------------------------------------------------
-- Comentarios de politica (redaccion actualizada: cast seguro con NULLIF).
-- ----------------------------------------------------------------------------
COMMENT ON POLICY rol_lectura ON rol IS
    'RLS consciente de plataforma (Req 23/27): lectura de roles predefinidos (tenant NULL), del tenant actual o en la ventana de autenticacion. Cast seguro ante cadena vacia con NULLIF (V53): la GUC vacia tras el reset transaccional del pool colapsa a NULL sin error 22P02. Escritura tenant-scoped en politicas separadas.';
COMMENT ON POLICY usuario_lectura ON usuario IS
    'RLS consciente de plataforma (Req 23): lectura del super_admin (tenant NULL), del tenant actual o en la ventana de autenticacion (resolucion por identificador global). Cast seguro ante cadena vacia con NULLIF (V53): la GUC vacia tras el reset transaccional del pool colapsa a NULL sin error 22P02. Escritura tenant-scoped en politicas separadas.';

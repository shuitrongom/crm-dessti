-- ============================================================================
-- V40__presupuestos_presupuesto.sql
--
-- Modulo `presupuestos` (Bloque 38, Tarea 38.1, Req 62, 12, 23, 49). Es la
-- PRIMERA tabla del modulo presupuestos; establece la raiz `presupuesto`
-- replicando EXACTAMENTE el patron reutilizable de toda tabla tenant-scoped
-- (V11..V38):
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17.
--
-- PROPOSITO (Req 62): Presupuesto & control de costos. Cada Presupuesto fija,
-- por `area` y `periodo`, los montos ESTIMADOS de ingresos y/o egresos. La
-- variacion frente al ejercicio REAL (derivado de operaciones: facturacion,
-- compras, nomina) se calcula como una AGREGACION DE SOLO LECTURA que NO
-- modifica los origenes (Req 62.2, 62.8): el importe real lo aporta el puerto
-- de solo lectura `RealEjercidoPort` del modulo (ver decisiones abajo). Esta
-- tabla persiste UNICAMENTE lo estimado; el real NO se almacena aqui.
--
-- REQUISITOS CUBIERTOS
--   - Req 62.1 (crear Presupuesto por area y periodo con montos estimados de
--     ingresos y/o egresos -> persistir): columnas area, periodo,
--     ingresos_estimados, egresos_estimados; UNIQUE (tenant_id, area, periodo).
--   - Req 62.2 / 62.8 (comparar contra el ejercicio real derivado de operaciones
--     como agregacion de SOLO LECTURA, sin modificar origen): NO hay columnas de
--     "real" aqui; el real se obtiene bajo demanda via RealEjercidoPort y la
--     variacion se calcula en memoria (dominio puro CalculoVariacionPresupuesto).
--   - Req 62.3 (variacion que supera un umbral configurable -> destacar la
--     desviacion): el umbral es configuracion de aplicacion
--     (crm.presupuestos.umbral-desviacion), NO una columna; se aplica al reportar.
--   - Req 62.4 (listado paginado 20/100, filtro por area y periodo): indices de
--     apoyo (tenant_id, area) y (tenant_id, periodo).
--   - Req 62.5 (auditoria al crear/modificar): la registra la aplicacion via
--     AuditoriaPort; columnas created_by/updated_by de apoyo.
--   - Req 62.6 / 62.7 (Property 36: variacion = real - presupuestado, en importe
--     ABSOLUTO y en PORCENTAJE, favorable/desfavorable): calculo puro de dominio;
--     no requiere columnas.
--   - Req 23 (multi-tenant): tenant_id + RLS.
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. `area` COMO VARCHAR(40) LIBRE CON CHECK NO-VACIO: el dominio de areas
--      esperado son las areas funcionales del CRM (por ejemplo: 'comercial',
--      'produccion', 'compras', 'nomina', 'instalacion', 'mantenimiento',
--      'marketing', 'administracion'). Se modela como texto libre (no enum de BD)
--      para no acoplar el presupuesto a un catalogo rigido y permitir areas
--      especificas del tenant; el CHECK garantiza que no sea vacio tras recortar
--      espacios. Etiquetas ASCII minusculas por estabilidad de codificacion,
--      coherente con V5 (nombres de rol) y V17 (etiquetas de estado).
--   2. `periodo` COMO VARCHAR(7): admite el formato 'AAAA-MM' (mes contable) o un
--      codigo de periodo equivalente (por ejemplo 'AAAA-Q1'). Se valida no-vacio;
--      la semantica del formato la impone la aplicacion. Se mantiene corto y
--      textual para ordenar/filtrar de forma natural (Req 62.4).
--   3. UNIQUE (tenant_id, area, periodo): un unico Presupuesto por combinacion de
--      area y periodo dentro del tenant (Req 62.1). Un alta duplicada viola este
--      indice; la aplicacion la traduce a 409 (ConflictoUnicidadException) tras
--      pre-verificar con existsByAreaAndPeriodo.
--   4. MONTOS NUMERIC(18,2) NOT NULL DEFAULT 0 CON CHECK >= 0: coherentes con la
--      escala monetaria (2, HALF_UP) del resto del CRM (cotizaciones, activos
--      fijos). Se admite 0 para un Presupuesto que solo estima ingresos O solo
--      egresos (Req 62.1 "ingresos y/o egresos"): el lado no presupuestado queda
--      en 0. No se admiten montos negativos (un presupuesto es una estimacion no
--      negativa).
--   5. EL "REAL" NO SE PERSISTE (Req 62.2, 62.8): el ejercicio real es un dato
--      derivado y volatil de otros modulos; almacenarlo aqui duplicaria la verdad
--      y arriesgaria inconsistencias. En su lugar, la aplicacion consume
--      RealEjercidoPort (puerto de SOLO LECTURA) que agrega ingresos/egresos
--      reales por area y periodo sin tocar los origenes. Un adaptador por defecto
--      devuelve 0 (placeholder documentado) para que el modulo compile y funcione;
--      adaptadores concretos (facturacion/compras/nomina) pueden implementarlo mas
--      adelante sin tocar el motor de presupuesto.
--   6. EL UMBRAL DE DESVIACION NO ES COLUMNA (Req 62.3): es configuracion de
--      aplicacion (crm.presupuestos.umbral-desviacion, por defecto 0.10 = 10%),
--      aplicada al construir el reporte de variacion. Asi puede ajustarse sin
--      migracion.
--   7. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V38.
--   8. PERMISOS: V5 ya sembro presupuesto:{crear,leer,listar,actualizar} y los
--      asigno a los roles admin_empresa (a0000000-...-000000000002) y gerente
--      (a0000000-...-000000000003, Req 27.14). Por tanto V40 NO necesita sembrar
--      permisos adicionales.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- presupuesto
--   Presupuesto por area y periodo con montos ESTIMADOS de ingresos y/o egresos
--   (Req 62.1). tenant-scoped (Req 23). El ejercicio real y el umbral NO se
--   almacenan aqui (DECISIONES 5 y 6).
-- ----------------------------------------------------------------------------
CREATE TABLE presupuesto (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    -- Area funcional del presupuesto; texto libre no vacio (DECISION 1).
    area                VARCHAR(40)    NOT NULL,
    -- Periodo 'AAAA-MM' o codigo de periodo equivalente; no vacio (DECISION 2).
    periodo             VARCHAR(7)     NOT NULL,
    -- Montos ESTIMADOS no negativos, escala 2 (DECISION 4). 0 = lado no presupuestado.
    ingresos_estimados  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    egresos_estimados   NUMERIC(18,2)  NOT NULL DEFAULT 0,
    -- Columna heredada de TenantScopedEntity: concurrencia optimista (Req 49).
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_presupuesto PRIMARY KEY (id),
    CONSTRAINT fk_presupuesto_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Area no vacia tras recortar espacios (DECISION 1).
    CONSTRAINT ck_presupuesto_area_no_vacia CHECK (length(btrim(area)) >= 1),
    -- Periodo no vacio tras recortar espacios (DECISION 2).
    CONSTRAINT ck_presupuesto_periodo_no_vacio CHECK (length(btrim(periodo)) >= 1),
    -- Montos estimados no negativos (DECISION 4).
    CONSTRAINT ck_presupuesto_ingresos_no_neg CHECK (ingresos_estimados >= 0),
    CONSTRAINT ck_presupuesto_egresos_no_neg CHECK (egresos_estimados >= 0),
    -- Un unico Presupuesto por area y periodo dentro del tenant (Req 62.1, DECISION 3).
    CONSTRAINT uq_presupuesto_area_periodo UNIQUE (tenant_id, area, periodo)
);

CREATE INDEX ix_presupuesto_tenant_id ON presupuesto (tenant_id);

-- Apoyo a los filtros del listado (Req 62.4): por area y por periodo, siempre
-- acotados al tenant.
CREATE INDEX ix_presupuesto_tenant_area    ON presupuesto (tenant_id, area);
CREATE INDEX ix_presupuesto_tenant_periodo ON presupuesto (tenant_id, periodo);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE presupuesto ENABLE ROW LEVEL SECURITY;
ALTER TABLE presupuesto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON presupuesto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

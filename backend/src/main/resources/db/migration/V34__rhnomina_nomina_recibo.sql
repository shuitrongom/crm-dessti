-- ============================================================================
-- V34__rhnomina_nomina_recibo.sql
--
-- Modulo de calculo de Nomina y CFDI de nomina (rhnomina.nomina): tablas
-- `nomina` (proceso de nomina de un Periodo_Nomina y su maquina de estados) y
-- `recibo_nomina` (Recibo_Nomina por Empleado, con importes calculados y su
-- Timbrado como CFDI de nomina). Bloque 35, Req 41, 35, 23, 49. Se construye
-- SOBRE el bloque 34 (V32: empleado / contrato_laboral / incidencia) y replica
-- EXACTAMENTE el patron reutilizable tenant-scoped establecido por V11..V32:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id (y por relaciones/filtros frecuentes),
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron de V14/V17/V30/V32 (ENABLE + FORCE + tenant_isolation).
--
-- ----------------------------------------------------------------------------
-- Requisitos cubiertos (Req 41 -- calculo de Nomina y CFDI de nomina)
-- ----------------------------------------------------------------------------
--   - 41.1 Al procesar la Nomina de un Periodo_Nomina, por cada Empleado se
--          determinan percepciones (salario y, cuando aplique, tiempo extra,
--          aguinaldo, PTU), deducciones (ISR, IMSS, Infonavit) y el subsidio al
--          empleo cuando corresponda, y el neto a pagar. Los importes AGREGADOS
--          por Empleado se guardan en recibo_nomina (percepciones, deducciones,
--          subsidio, neto); los totales del proceso, en nomina.
--   - 41.2 ISR conforme a las tarifas vigentes y cuotas IMSS/Infonavit conforme a
--          normativa: el CALCULO es una funcion pura del dominio (CalculoNomina /
--          TablasFiscalesNomina; Property 19). La BD solo almacena importes ya
--          calculados con CHECK de no negatividad.
--   - 41.3 Si faltan datos fiscales del Empleado (RFC/CURP/NSS o Contrato_Laboral
--          vigente) -> la capa de aplicacion RECHAZA el calculo (HTTP 422)
--          nombrando el dato faltante y NO persiste ningun Recibo_Nomina de esa
--          Nomina (calculo atomico). No hay CHECK para esto: es regla de negocio.
--   - 41.4 Al autorizar la Nomina y timbrarla, por cada Recibo_Nomina se solicita
--          el Timbrado como CFDI de nomina al PAC (Req 35): folio_fiscal UUID,
--          sello_sat TEXT y fecha_timbrado TIMESTAMPTZ.
--   - 41.5 Estados de la Nomina y SOLO estas transiciones:
--          borrador -> calculada -> autorizada -> timbrada -> pagada.
--          El Recibo_Nomina: calculado -> timbrado -> cancelado.
--   - 41.6 Transicion invalida -> se rechaza conservando el estado (HTTP 409); la
--          maquina de estados pura del dominio lo impone (EstadoNomina /
--          EstadoReciboNomina).
--   - 41.7 Recibo_Nomina timbrado + Folio_Fiscal INMUTABLES (historico): la
--          inmutabilidad tras 'timbrado' la impone el DOMINIO (los mutadores
--          lanzan si estado != 'calculado'); la BD conserva folio_fiscal/sello_sat
--          como historico. UNIQUE parcial (tenant_id, folio_fiscal) WHERE NOT NULL.
--   - 41.8 Auditoria en los cambios de estado de la Nomina y en el Timbrado del
--          Recibo_Nomina: la aplica la capa de aplicacion (ServicioNomina).
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla (ambas).
--   - Req 49 (concurrencia optimista): columna version en ambas tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. IDENTIDAD ARITMETICA DE LA NOMINA (Req 41.1, 41.2; Property 19):
--        neto = round(percepciones - deducciones + subsidio, 2)   (half-up, escala 2)
--      y neto >= 0 para entradas validas. El calculo es una funcion PURA del
--      dominio (CalculoNomina). La BD solo almacena los importes ya calculados con
--      CHECK de no negatividad (percepciones/deducciones/subsidio/neto >= 0).
--   2. TABLAS FISCALES VERSIONADAS (Req 41.2): las tarifas de ISR (Art. 96 LISR),
--      la cuota obrera del IMSS y el descuento de Infonavit, y el subsidio al
--      empleo, viven como CONSTANTES DOCUMENTADAS en el dominio
--      (TablasFiscalesNomina), con la nota "vigente 2024/2026, revisar anualmente".
--      Se pueden actualizar sin tocar el motor de calculo. La cuota IMSS aqui es una
--      representacion SIMPLIFICADA (una tasa obrera unica sobre el salario base);
--      el IMSS completo tiene multiples ramos y se refinaria sin cambiar el esquema.
--   3. IMPORTES AGREGADOS EN recibo_nomina (no una tabla de desglose por linea):
--      se almacenan percepciones/deducciones/subsidio/neto por Empleado. El
--      desglose fino (salario, tiempo extra, ISR, IMSS, ...) viaja en el resultado
--      del calculo y en el DTO; NO se materializa como tabla para mantener el
--      esquema tratable en este bloque (decision documentada, ampliable despues).
--   4. PERIODO_NOMINA (Req 41.1): se referencia por CODIGO en formato 'AAAA-MM'
--      (VARCHAR(7)), COHERENTE con incidencia.periodo_nomina de V32. No se crea una
--      tabla materializada de periodos: el vinculo se mantiene por codigo, igual
--      que en V32.
--   5. ESTADOS por CHECK (dominio cerrado y estable) ademas de la maquina de
--      estados pura del dominio (Req 41.5, 41.6):
--        nomina.estado        IN ('borrador','calculada','autorizada','timbrada','pagada')
--        recibo_nomina.estado IN ('calculado','timbrado','cancelado')
--   6. FOLIO_FISCAL UNICO PARCIAL (Req 41.4, 41.7): igual que factura (V30). Un
--      Folio_Fiscal (UUID del SAT) es unico por tenant, pero el recibo 'calculado'
--      aun no lo tiene; indice UNICO PARCIAL sobre (tenant_id, folio_fiscal) WHERE
--      folio_fiscal IS NOT NULL, de modo que multiples recibos sin folio coexisten.
--   7. INMUTABILIDAD (Req 41.7): se garantiza en el DOMINIO (los mutadores del
--      Recibo_Nomina lanzan si el estado != 'calculado', y la maquina de estados
--      solo admite las transiciones definidas). La BD conserva folio_fiscal/
--      sello_sat/fecha_timbrado como historico; no se agrega trigger de
--      inmutabilidad para no divergir del patron del resto de migraciones (la
--      fuente de verdad es el dominio).
--   8. PERMISOS (Req 3): V5 sembro nomina:{crear,leer,cambiar_estado} y los asigno
--      al rol `rh` (a0000000-0000-0000-0000-00000000000c) pero NO 'nomina:listar'
--      (listado GET /nominas) ni 'recibo_nomina:leer' (GET /nominas/{id}/recibos).
--      Se agregan aqui y se enlazan al MISMO rol `rh`, con el estilo de V9/V30/V32
--      (INSERT del permiso con ON CONFLICT DO NOTHING y enlace rol_permiso por
--      subconsulta). Los permisos ya sembrados NO se re-siembran.
--   9. sello_sat TEXT: el sello digital del SAT es una cadena Base64 larga; se
--      modela TEXT (sin limite fijo). folio_fiscal UUID coincide con el tipo del
--      Folio_Fiscal del SAT (patron de V30).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- nomina
--   Proceso de nomina de un Periodo_Nomina de la Empresa (Req 41.1). tenant-scoped
--   (Req 23). Estado inicial 'borrador' (Req 41.5); estado final 'pagada'. Los
--   totales agregan la suma de los Recibo_Nomina del proceso.
-- ----------------------------------------------------------------------------
CREATE TABLE nomina (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID           NOT NULL,
    periodo_nomina       VARCHAR(7)     NOT NULL,
    estado               VARCHAR(12)    NOT NULL DEFAULT 'borrador',
    total_percepciones   NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_deducciones    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_neto           NUMERIC(18, 2) NOT NULL DEFAULT 0,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_nomina PRIMARY KEY (id),
    CONSTRAINT fk_nomina_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Estados permitidos (Req 41.5). Etiquetas ASCII minusculas.
    CONSTRAINT ck_nomina_estado CHECK (
        estado IN ('borrador', 'calculada', 'autorizada', 'timbrada', 'pagada')),
    -- Totales no negativos (Req 41.1; identidad neto = percepciones - deducciones + subsidio >= 0).
    CONSTRAINT ck_nomina_total_percepciones_no_negativo CHECK (total_percepciones >= 0),
    CONSTRAINT ck_nomina_total_deducciones_no_negativo  CHECK (total_deducciones  >= 0),
    CONSTRAINT ck_nomina_total_neto_no_negativo         CHECK (total_neto         >= 0)
);

CREATE INDEX ix_nomina_tenant_id       ON nomina (tenant_id);
CREATE INDEX ix_nomina_tenant_periodo  ON nomina (tenant_id, periodo_nomina);
CREATE INDEX ix_nomina_tenant_estado   ON nomina (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- recibo_nomina
--   Recibo de nomina por Empleado dentro de una Nomina (Req 41.1, 41.4).
--   tenant-scoped (Req 23). Almacena los importes AGREGADOS ya calculados
--   (percepciones, deducciones, subsidio, neto) y el resultado del Timbrado como
--   CFDI de nomina (folio_fiscal, sello_sat, fecha_timbrado). Estado inicial
--   'calculado' (Req 41.5); una vez 'timbrado' es historico inmutable (Req 41.7).
-- ----------------------------------------------------------------------------
CREATE TABLE recibo_nomina (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    nomina_id       UUID           NOT NULL,
    empleado_id     UUID           NOT NULL,
    percepciones    NUMERIC(18, 2) NOT NULL,
    deducciones     NUMERIC(18, 2) NOT NULL,
    subsidio        NUMERIC(18, 2) NOT NULL DEFAULT 0,
    neto            NUMERIC(18, 2) NOT NULL,
    estado          VARCHAR(12)    NOT NULL DEFAULT 'calculado',
    folio_fiscal    UUID,
    sello_sat       TEXT,
    fecha_timbrado  TIMESTAMPTZ,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_recibo_nomina PRIMARY KEY (id),
    CONSTRAINT fk_recibo_nomina_empresa  FOREIGN KEY (tenant_id)   REFERENCES empresa (id),
    CONSTRAINT fk_recibo_nomina_nomina   FOREIGN KEY (nomina_id)   REFERENCES nomina (id),
    CONSTRAINT fk_recibo_nomina_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id),
    -- Estados permitidos (Req 41.5). Etiquetas ASCII minusculas.
    CONSTRAINT ck_recibo_nomina_estado CHECK (
        estado IN ('calculado', 'timbrado', 'cancelado')),
    -- Importes no negativos (Req 41.1; identidad neto >= 0).
    CONSTRAINT ck_recibo_nomina_percepciones_no_negativo CHECK (percepciones >= 0),
    CONSTRAINT ck_recibo_nomina_deducciones_no_negativo  CHECK (deducciones  >= 0),
    CONSTRAINT ck_recibo_nomina_subsidio_no_negativo     CHECK (subsidio     >= 0),
    CONSTRAINT ck_recibo_nomina_neto_no_negativo         CHECK (neto         >= 0)
);

CREATE INDEX ix_recibo_nomina_tenant_id       ON recibo_nomina (tenant_id);
CREATE INDEX ix_recibo_nomina_tenant_nomina   ON recibo_nomina (tenant_id, nomina_id);
CREATE INDEX ix_recibo_nomina_tenant_empleado ON recibo_nomina (tenant_id, empleado_id);

-- Folio_Fiscal unico por tenant, solo cuando existe (Req 41.4, 41.7). Los recibos
-- 'calculado' (folio NULL) no participan del indice unico parcial.
CREATE UNIQUE INDEX uq_recibo_nomina_tenant_folio_fiscal
    ON recibo_nomina (tenant_id, folio_fiscal)
    WHERE folio_fiscal IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V14/V17/V30/V32.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE nomina ENABLE ROW LEVEL SECURITY;
ALTER TABLE nomina FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON nomina
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE recibo_nomina ENABLE ROW LEVEL SECURITY;
ALTER TABLE recibo_nomina FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON recibo_nomina
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS ADICIONALES (Req 3, 27.12) -- DECISION 8
--   V5 sembro nomina:{crear,leer,cambiar_estado} y los asigno al rol `rh`. El
--   listado (GET /nominas) exige nomina:listar y la consulta de recibos
--   (GET /nominas/{id}/recibos) exige recibo_nomina:leer, que V5 no incluia. Se
--   agregan aqui y se enlazan al MISMO rol predefinido `rh` (UUID fijo de V5), con
--   el estilo de V9/V30/V32 (ON CONFLICT DO NOTHING por la clave natural
--   (recurso, operacion)). Los permisos ya sembrados NO se re-siembran.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('nomina',        'listar'),
    ('recibo_nomina', 'leer')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000c', p.id
FROM permiso p
WHERE (p.recurso = 'nomina' AND p.operacion = 'listar')
   OR (p.recurso = 'recibo_nomina' AND p.operacion = 'leer')
ON CONFLICT DO NOTHING;

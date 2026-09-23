-- ============================================================================
-- V35__tesoreria_cuenta_bancaria_conciliacion.sql
--
-- Modulo `tesoreria` / Cuentas bancarias, estados de cuenta, movimientos
-- bancarios y conciliacion bancaria (Tareas 31.1, 31.2; Req 43, 12, 23, 49).
--
-- Crea CUATRO tablas tenant-scoped, replicando EXACTAMENTE el patron reutilizable
-- de V11..V33 para toda tabla tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando V33/V14/V17 (ENABLE + FORCE + politica USING/WITH CHECK).
--
-- Requisitos cubiertos:
--   - Req 43.1 (alta de Cuenta_Bancaria): tabla cuenta_bancaria con nombre, banco,
--     CLABE opcional, moneda y bandera activa.
--   - Req 43.2 (importar Estado_Cuenta_Bancario con Movimiento_Bancario): tablas
--     estado_cuenta_bancario (periodo + saldos) y movimiento_bancario (monto CON
--     SIGNO). La importacion la ejecuta la aplicacion via ImportacionBancariaPort.
--   - Req 43.3 (emparejar cada Movimiento_Bancario con Poliza_Contable o Pago
--     cuando coinciden monto, fecha dentro de tolerancia y referencia): columnas
--     poliza_contable_id / pago_id en movimiento_bancario que registran la partida
--     emparejada; el emparejamiento PURO (monto + fecha±tolerancia + referencia)
--     vive en el dominio (ReglasConciliacion). La tolerancia de fecha es
--     configurable (crm.tesoreria.conciliacion.tolerancia-dias, por defecto 3).
--   - Req 43.4 (movimientos sin coincidencia -> excepciones para revision manual):
--     movimiento_bancario.estado_conciliacion con CHECK IN ('pendiente',
--     'conciliado','excepcion').
--   - Req 43.5 (diferencia saldo bancario vs contable; COMPLETA solo con diferencia
--     cero una vez explicadas las partidas -Property 18-): tabla
--     conciliacion_bancaria con saldo_bancario, saldo_contable, diferencia y estado
--     IN ('en_proceso','completa'). La decision COMPLETA <=> diferencia cero y sin
--     excepciones es una funcion PURA del dominio (ReglasConciliacion.esCompleta).
--   - Req 43.6 (listado paginado 20/100 filtrable por Cuenta_Bancaria, periodo y
--     estado de conciliacion): indices (tenant_id, cuenta_bancaria_id) y
--     (tenant_id, estado_conciliacion) en movimiento_bancario;
--     (tenant_id, cuenta_bancaria_id) en conciliacion_bancaria.
--   - Req 43.7 (auditoria al importar o conciliar): la ejecuta la aplicacion via
--     AuditoriaPort (no BD).
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla (las cuatro).
--   - Req 49 (concurrencia optimista): columna version en las cuatro tablas.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. MONTO CON SIGNO (movimiento_bancario.monto NUMERIC(18,2) NOT NULL): positivo
--      para un deposito y negativo para un retiro. El emparejamiento por monto
--      (Req 43.3) compara el VALOR ABSOLUTO del monto contra la magnitud de la
--      partida contable candidata (ReglasConciliacion.montoCoincide). El dominio
--      exige monto distinto de cero.
--   2. EMPAREJAMIENTO POR MONTO + FECHA±TOLERANCIA + REFERENCIA (Req 43.3): funcion
--      PURA del dominio (ReglasConciliacion.emparejar) que se ejecuta sobre los
--      candidatos que aporta el PolizaConciliablePort (implementado por un adaptador
--      de solo lectura en contabilidad sobre poliza_contable). La tolerancia de
--      fecha en dias es configurable (por defecto 3).
--   3. EXCEPCIONES (Req 43.4): un movimiento sin coincidencia queda
--      estado_conciliacion='excepcion' para revision manual; no enlaza poliza ni
--      pago.
--   4. DIFERENCIA CERO PARA COMPLETA (Req 43.5; Property 18): conciliacion_bancaria
--      registra saldo_bancario (variacion neta del periodo = saldo_final -
--      saldo_inicial), saldo_contable (suma de los montos CON SIGNO de los
--      movimientos explicados) y diferencia = saldo_bancario - saldo_contable con
--      CHECK ck_conciliacion_diferencia (diferencia = saldo_bancario -
--      saldo_contable). El estado 'completa' solo lo fija el dominio cuando la
--      diferencia es cero y no quedan movimientos en excepcion; la BD no puede
--      conocer las excepciones, por lo que NO se restringe estado por CHECK contra
--      diferencia (seria incompleto): la invariante COMPLETA <=> diferencia cero y
--      sin excepciones es responsabilidad PURA del dominio (Property 18).
--   5. AGREGADO estado_cuenta_bancario -> movimiento_bancario: la FK de
--      movimiento_bancario a estado_cuenta_bancario es ON DELETE CASCADE por
--      consistencia estructural del agregado; la aplicacion no borra estados de
--      cuenta (historico). movimiento_bancario tambien guarda cuenta_bancaria_id
--      (desnormalizado) para el filtro directo del listado (Req 43.6).
--   6. CLABE OPCIONAL: cuenta_bancaria.clabe VARCHAR(18) NULL; el dominio valida 18
--      digitos cuando se provee. moneda VARCHAR(3) NOT NULL DEFAULT 'MXN'.
--   7. PERMISOS (Req 3, 27.11) -- DECISION 7: V5 ya sembro
--      cuenta_bancaria:{crear,leer,listar} y conciliacion_bancaria:{crear,leer},
--      todos asignados al rol `contabilidad` (a0000000-0000-0000-0000-00000000000b).
--      Este bloque necesita ADEMAS:
--        * movimiento_bancario:leer      (listado GET de movimientos del Req 43.6),
--        * conciliacion_bancaria:listar  (coherencia del listado del Req 43.6; el
--          controller usa conciliacion_bancaria:leer para el GET de conciliaciones,
--          pero se siembra 'listar' para completar el conjunto de operaciones del
--          recurso, alineado con el resto de recursos listables).
--      Se siembran con ON CONFLICT DO NOTHING (clave natural recurso, operacion) y se
--      ENLAZAN explicitamente al rol `contabilidad`, porque la asignacion por recurso
--      de V5 ya se ejecuto y no recogeria operaciones nuevas.
--   8. NUMERO DE MIGRACION V35: pre-asignado a este bloque. V36 lo usa otro modulo en
--      paralelo; aqui NO se usa V36.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- cuenta_bancaria
--   Cuenta bancaria de la Empresa (Req 43.1). tenant-scoped (Req 23).
-- ----------------------------------------------------------------------------
CREATE TABLE cuenta_bancaria (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    banco       VARCHAR(120) NOT NULL,
    clabe       VARCHAR(18),
    moneda      VARCHAR(3)   NOT NULL DEFAULT 'MXN',
    activa      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_cuenta_bancaria PRIMARY KEY (id),
    CONSTRAINT fk_cuenta_bancaria_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre y banco no vacios (Req 43.1).
    CONSTRAINT ck_cuenta_bancaria_nombre CHECK (length(btrim(nombre)) >= 1),
    CONSTRAINT ck_cuenta_bancaria_banco CHECK (length(btrim(banco)) >= 1),
    -- CLABE de 18 digitos cuando se provee (decision 6).
    CONSTRAINT ck_cuenta_bancaria_clabe CHECK (clabe IS NULL OR clabe ~ '^[0-9]{18}$'),
    -- Moneda ISO 4217 de 3 letras.
    CONSTRAINT ck_cuenta_bancaria_moneda CHECK (length(btrim(moneda)) = 3)
);

CREATE INDEX ix_cuenta_bancaria_tenant_id ON cuenta_bancaria (tenant_id);

-- Apoyo al listado/busqueda por nombre, acotado al tenant (Req 43.1).
CREATE INDEX ix_cuenta_bancaria_tenant_nombre ON cuenta_bancaria (tenant_id, nombre);

-- ----------------------------------------------------------------------------
-- estado_cuenta_bancario
--   Estado de cuenta importado de una Cuenta_Bancaria para un periodo (Req 43.2).
--   tenant-scoped (Req 23). saldo_final es el saldo bancario de la conciliacion.
-- ----------------------------------------------------------------------------
CREATE TABLE estado_cuenta_bancario (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID           NOT NULL,
    cuenta_bancaria_id   UUID           NOT NULL,
    periodo_inicio       DATE           NOT NULL,
    periodo_fin          DATE           NOT NULL,
    saldo_inicial        NUMERIC(18, 2) NOT NULL,
    saldo_final          NUMERIC(18, 2) NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    CONSTRAINT pk_estado_cuenta_bancario PRIMARY KEY (id),
    CONSTRAINT fk_estado_cuenta_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_estado_cuenta_cuenta FOREIGN KEY (cuenta_bancaria_id)
        REFERENCES cuenta_bancaria (id),
    -- El fin del periodo no puede ser anterior al inicio (Req 43.2).
    CONSTRAINT ck_estado_cuenta_periodo CHECK (periodo_fin >= periodo_inicio)
);

CREATE INDEX ix_estado_cuenta_bancario_tenant_id ON estado_cuenta_bancario (tenant_id);

-- Apoyo al desglose y filtro por Cuenta_Bancaria (Req 43.6), acotado al tenant.
CREATE INDEX ix_estado_cuenta_bancario_tenant_cuenta
    ON estado_cuenta_bancario (tenant_id, cuenta_bancaria_id);

-- ----------------------------------------------------------------------------
-- movimiento_bancario
--   Linea de un Estado_Cuenta_Bancario: deposito (monto > 0) o retiro (monto < 0)
--   (Req 43.2, 43.3). tenant-scoped (Req 23). estado_conciliacion acotado por CHECK.
-- ----------------------------------------------------------------------------
CREATE TABLE movimiento_bancario (
    id                           UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                    UUID           NOT NULL,
    estado_cuenta_bancario_id    UUID           NOT NULL,
    cuenta_bancaria_id           UUID           NOT NULL,
    fecha                        DATE           NOT NULL,
    monto                        NUMERIC(18, 2) NOT NULL,
    referencia                   VARCHAR(120),
    descripcion                  VARCHAR(300),
    estado_conciliacion          VARCHAR(12)    NOT NULL DEFAULT 'pendiente',
    poliza_contable_id           UUID,
    pago_id                      UUID,
    version                      BIGINT         NOT NULL DEFAULT 0,
    created_at                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                   VARCHAR(255),
    updated_by                   VARCHAR(255),
    CONSTRAINT pk_movimiento_bancario PRIMARY KEY (id),
    CONSTRAINT fk_movimiento_bancario_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_movimiento_bancario_estado FOREIGN KEY (estado_cuenta_bancario_id)
        REFERENCES estado_cuenta_bancario (id) ON DELETE CASCADE,
    CONSTRAINT fk_movimiento_bancario_cuenta FOREIGN KEY (cuenta_bancaria_id)
        REFERENCES cuenta_bancaria (id),
    -- Monto distinto de cero (deposito o retiro; decision 1).
    CONSTRAINT ck_movimiento_bancario_monto CHECK (monto <> 0),
    -- Estado de conciliacion acotado (maquina de estados en el dominio; Req 43.3, 43.4).
    CONSTRAINT ck_movimiento_bancario_estado CHECK (
        estado_conciliacion IN ('pendiente', 'conciliado', 'excepcion'))
);

CREATE INDEX ix_movimiento_bancario_tenant_id ON movimiento_bancario (tenant_id);

-- Apoyo a los filtros del listado por Cuenta_Bancaria y por estado (Req 43.6),
-- siempre acotados al tenant.
CREATE INDEX ix_movimiento_bancario_tenant_cuenta
    ON movimiento_bancario (tenant_id, cuenta_bancaria_id);
CREATE INDEX ix_movimiento_bancario_tenant_estado
    ON movimiento_bancario (tenant_id, estado_conciliacion);

-- ----------------------------------------------------------------------------
-- conciliacion_bancaria
--   Resultado de conciliar un Estado_Cuenta_Bancario contra la contabilidad
--   (Req 43.5). tenant-scoped (Req 23). diferencia = saldo_bancario - saldo_contable
--   (CHECK). estado 'completa' solo lo fija el dominio con diferencia cero sin
--   excepciones (Property 18).
-- ----------------------------------------------------------------------------
CREATE TABLE conciliacion_bancaria (
    id                          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID           NOT NULL,
    cuenta_bancaria_id          UUID           NOT NULL,
    estado_cuenta_bancario_id   UUID           NOT NULL,
    saldo_bancario              NUMERIC(18, 2) NOT NULL,
    saldo_contable              NUMERIC(18, 2) NOT NULL,
    diferencia                  NUMERIC(18, 2) NOT NULL,
    estado                      VARCHAR(12)    NOT NULL DEFAULT 'en_proceso',
    fecha                       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                     BIGINT         NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    CONSTRAINT pk_conciliacion_bancaria PRIMARY KEY (id),
    CONSTRAINT fk_conciliacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_conciliacion_cuenta FOREIGN KEY (cuenta_bancaria_id)
        REFERENCES cuenta_bancaria (id),
    CONSTRAINT fk_conciliacion_estado_cuenta FOREIGN KEY (estado_cuenta_bancario_id)
        REFERENCES estado_cuenta_bancario (id),
    -- Estado acotado (maquina de estados en el dominio; Req 43.5).
    CONSTRAINT ck_conciliacion_estado CHECK (estado IN ('en_proceso', 'completa')),
    -- La diferencia es exactamente saldo_bancario - saldo_contable (decision 4).
    CONSTRAINT ck_conciliacion_diferencia CHECK (diferencia = saldo_bancario - saldo_contable)
);

CREATE INDEX ix_conciliacion_bancaria_tenant_id ON conciliacion_bancaria (tenant_id);

-- Apoyo a los filtros del listado por Cuenta_Bancaria y por estado (Req 43.6),
-- acotados al tenant.
CREATE INDEX ix_conciliacion_bancaria_tenant_cuenta
    ON conciliacion_bancaria (tenant_id, cuenta_bancaria_id);
CREATE INDEX ix_conciliacion_bancaria_tenant_estado
    ON conciliacion_bancaria (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V33/V14/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE cuenta_bancaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta_bancaria FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cuenta_bancaria
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE estado_cuenta_bancario ENABLE ROW LEVEL SECURITY;
ALTER TABLE estado_cuenta_bancario FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON estado_cuenta_bancario
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE movimiento_bancario ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_bancario FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON movimiento_bancario
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE conciliacion_bancaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE conciliacion_bancaria FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON conciliacion_bancaria
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS COMPLEMENTARIOS (Req 3, 27.11) -- DECISION 7
--   V5 sembro cuenta_bancaria:{crear,leer,listar} y conciliacion_bancaria:{crear,
--   leer}, asignados al rol `contabilidad` (a0000000-0000-0000-0000-00000000000b).
--   Este bloque necesita ADEMAS:
--     * movimiento_bancario:leer      (listado GET de movimientos del Req 43.6),
--     * conciliacion_bancaria:listar  (completa el conjunto de operaciones del
--       recurso, alineado con el resto de recursos listables).
--   Se siembran (ON CONFLICT DO NOTHING por la clave natural recurso, operacion) y se
--   enlazan al MISMO rol predefinido `contabilidad`, con el estilo de V33/V31.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('movimiento_bancario',    'leer'),
    ('conciliacion_bancaria',  'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE (p.recurso = 'movimiento_bancario'   AND p.operacion = 'leer')
   OR (p.recurso = 'conciliacion_bancaria' AND p.operacion = 'listar')
ON CONFLICT DO NOTHING;

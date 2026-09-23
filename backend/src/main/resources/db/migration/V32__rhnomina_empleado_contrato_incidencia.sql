-- ============================================================================
-- V32__rhnomina_empleado_contrato_incidencia.sql
--
-- Modulo base de Recursos Humanos / Nomina (rhnomina): tablas `empleado`,
-- `contrato_laboral` e `incidencia` (Tarea 34.1, Req 40, 23). Replica el PATRON
-- REUTILIZABLE tenant-scoped establecido por V11 (cliente/contacto):
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id (y por relaciones frecuentes),
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron de V11/V17 (ENABLE + FORCE + tenant_isolation).
--
-- Requisitos cubiertos (Req 40 -- gestion base de RH):
--   - 40.1 Alta de Empleado con datos obligatorios (nombre, RFC, CURP, NSS del
--          IMSS, fecha de ingreso) junto con su Contrato_Laboral (tipo, salario
--          diario y periodicidad); ambos se persisten atomicamente con id unico.
--   - 40.2 Alta con obligatorios faltantes o formato invalido de RFC/CURP/NSS ->
--          se rechaza en el dominio (HTTP 422) sin persistir; el formato se valida
--          en la capa de aplicacion (ValidacionesEmpleado), no por CHECK, para no
--          duplicar la logica y respetar el codigo de error esperado. Las columnas
--          solo acotan longitudes (VARCHAR) y presencia (NOT NULL).
--   - 40.3 Registro de Incidencia vinculada al Empleado y a un Periodo_Nomina,
--          con tipo en {asistencia, falta, permiso, incapacidad, tiempo_extra}.
--   - 40.4 Baja de Empleado activo -> borrado logico (activo=FALSE) conservando
--          el historico de Contrato_Laboral e Incidencia (no se eliminan filas).
--   - 40.5 Listado paginado 20/100 de Empleados (lo aplica la capa web).
--   - 40.6 Filtro por nombre o por estado sin distinguir mayusculas.
--   - 40.7 Auditoria al crear/modificar/baja (la aplica la capa de aplicacion).
--   - Req 23 (multi-tenant): tenant_id + RLS por tabla; unicidad de negocio POR
--            TENANT (RFC del Empleado unico dentro de la Empresa, no global).
--   - Req 49 (concurrencia optimista): columna version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien puede proveer su propio UUID (lo hace la fabrica).
--   2. timestamptz para las marcas temporales (UTC), coherente con V1/V11.
--   3. BORRADO LOGICO del Empleado (Req 40.4): columna activo BOOLEAN NOT NULL
--      DEFAULT TRUE; la baja conserva el historico (no se elimina la fila) y por
--      tanto los Contrato_Laboral e Incidencia asociados permanecen intactos.
--   4. UNICIDAD DE RFC DEL EMPLEADO POR TENANT ENTRE ACTIVOS (Req 23.6): INDICE
--      UNICO PARCIAL sobre (tenant_id, rfc) WHERE activo = TRUE, igual que el
--      cliente en V11; un Empleado dado de baja logica libera su RFC. El RFC se
--      almacena normalizado a mayusculas por la entidad.
--   5. VALIDACION DE FORMATO RFC/CURP/NSS (Req 40.2) en el dominio (HTTP 422),
--      no por CHECK: RFC persona fisica (13), CURP (18), NSS del IMSS (11 digitos).
--      Las columnas solo fijan longitudes maximas (VARCHAR) y NOT NULL.
--   6. PERIODO_NOMINA (Req 40.3): se almacena como codigo de periodo en formato
--      'AAAA-MM' (por ejemplo '2026-01') en VARCHAR(7); referencia logica al
--      Periodo_Nomina que el modulo de calculo de nomina materializara en el
--      bloque 35. No se declara aqui una FK a una tabla de periodos porque esa
--      tabla pertenece a un modulo posterior; el vinculo se mantiene por codigo.
--   7. CONTRATO_LABORAL / INCIDENCIA -> EMPLEADO: FK empleado_id NOT NULL. El
--      tipo/periodicidad se acotan por CHECK (dominio cerrado y estable) ademas de
--      validarse en el dominio; el salario_diario se exige > 0 por CHECK.
--   8. PERMISO empleado:eliminar (Req 40.4, 3.1): V5 sembro empleado:{crear,leer,
--      listar,actualizar} pero NO 'eliminar', necesario para la baja logica. Se
--      agrega al catalogo y se enlaza al rol predefinido `rh` (UUID fijo de V5),
--      replicando exactamente lo que V11 hizo para cliente:eliminar.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- empleado
--   Persona fisica contratada por la Empresa (Req 40.1). tenant-scoped (Req 23).
--   Datos obligatorios: nombre, RFC, CURP, NSS del IMSS y fecha de ingreso. El
--   formato de RFC/CURP/NSS se valida en la capa de aplicacion (Req 40.2).
-- ----------------------------------------------------------------------------
CREATE TABLE empleado (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    nombre         VARCHAR(200) NOT NULL,
    rfc            VARCHAR(13)  NOT NULL,
    curp           VARCHAR(18)  NOT NULL,
    nss            VARCHAR(11)  NOT NULL,
    fecha_ingreso  DATE         NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_empleado PRIMARY KEY (id),
    CONSTRAINT fk_empleado_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT ck_empleado_nombre_no_vacio CHECK (length(btrim(nombre)) >= 1)
);

CREATE INDEX ix_empleado_tenant_nombre ON empleado (tenant_id, nombre);

-- Unicidad del RFC del Empleado POR TENANT unicamente entre activos (Req 23.6):
-- permite reutilizar el RFC de un Empleado dado de baja logica.
CREATE UNIQUE INDEX uq_empleado_rfc_activo_por_tenant
    ON empleado (tenant_id, rfc)
    WHERE activo = TRUE;

COMMENT ON INDEX uq_empleado_rfc_activo_por_tenant IS
    'Unicidad del RFC del Empleado POR TENANT entre activos (Req 23.6). El RFC se '
    'almacena normalizado a mayusculas por la entidad; la aplicacion comprueba '
    'existsByRfcAndActivoTrue y traduce la violacion a HTTP 409 ante carreras '
    'concurrentes. Un Empleado inactivo (baja logica) libera su RFC.';

-- ----------------------------------------------------------------------------
-- contrato_laboral
--   Contrato de trabajo asociado a un Empleado (Req 40.1). tenant-scoped
--   (Req 23). El alta del Empleado crea atomicamente su primer Contrato_Laboral.
--   Incluye activo/version por consistencia con el patron y para el historico.
-- ----------------------------------------------------------------------------
CREATE TABLE contrato_laboral (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    empleado_id     UUID          NOT NULL,
    tipo            VARCHAR(20)   NOT NULL,
    salario_diario  NUMERIC(18,2) NOT NULL,
    periodicidad    VARCHAR(12)   NOT NULL,
    fecha_inicio    DATE          NOT NULL,
    fecha_fin       DATE,
    activo          BOOLEAN       NOT NULL DEFAULT TRUE,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_contrato_laboral PRIMARY KEY (id),
    CONSTRAINT fk_contrato_laboral_empresa  FOREIGN KEY (tenant_id)   REFERENCES empresa (id),
    CONSTRAINT fk_contrato_laboral_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id),
    CONSTRAINT ck_contrato_laboral_tipo
        CHECK (tipo IN ('indeterminado', 'determinado', 'obra', 'capacitacion')),
    CONSTRAINT ck_contrato_laboral_periodicidad
        CHECK (periodicidad IN ('semanal', 'quincenal', 'mensual')),
    CONSTRAINT ck_contrato_laboral_salario_positivo CHECK (salario_diario > 0)
);

CREATE INDEX ix_contrato_laboral_tenant_empleado ON contrato_laboral (tenant_id, empleado_id);

-- ----------------------------------------------------------------------------
-- incidencia
--   Incidencia de un Empleado en un Periodo_Nomina (Req 40.3). tenant-scoped
--   (Req 23). Se conserva como historico (Req 40.4). El periodo_nomina se guarda
--   como codigo 'AAAA-MM'; el tipo se acota al dominio cerrado por CHECK.
-- ----------------------------------------------------------------------------
CREATE TABLE incidencia (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    empleado_id     UUID          NOT NULL,
    periodo_nomina  VARCHAR(7)    NOT NULL,
    tipo            VARCHAR(14)   NOT NULL,
    cantidad        NUMERIC(18,2),
    descripcion     VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_incidencia PRIMARY KEY (id),
    CONSTRAINT fk_incidencia_empresa  FOREIGN KEY (tenant_id)   REFERENCES empresa (id),
    CONSTRAINT fk_incidencia_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id),
    CONSTRAINT ck_incidencia_tipo
        CHECK (tipo IN ('asistencia', 'falta', 'permiso', 'incapacidad', 'tiempo_extra'))
);

CREATE INDEX ix_incidencia_tenant_empleado ON incidencia (tenant_id, empleado_id);
CREATE INDEX ix_incidencia_tenant_periodo  ON incidencia (tenant_id, periodo_nomina);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V11/V17.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE empleado ENABLE ROW LEVEL SECURITY;
ALTER TABLE empleado FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON empleado
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE contrato_laboral ENABLE ROW LEVEL SECURITY;
ALTER TABLE contrato_laboral FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON contrato_laboral
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE incidencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE incidencia FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON incidencia
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso atomico faltante de borrado logico (Req 40.4, 3.1). V5 sembro
-- empleado:{crear,leer,listar,actualizar} pero NO la operacion 'eliminar',
-- necesaria para la baja logica del Empleado. Se agrega al catalogo y se enlaza
-- al rol predefinido `rh` (UUID fijo de V5), replicando lo que V11 hizo para
-- cliente:eliminar. El guardado REST con @PreAuthorize se aplica en el controlador.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('empleado', 'eliminar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000c', p.id
FROM permiso p
WHERE p.recurso = 'empleado' AND p.operacion = 'eliminar'
ON CONFLICT DO NOTHING;

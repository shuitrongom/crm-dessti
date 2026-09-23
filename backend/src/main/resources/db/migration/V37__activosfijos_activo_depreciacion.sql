-- ============================================================================
-- V37__activosfijos_activo_depreciacion.sql
--
-- Modulo `activos fijos` (Tarea 32.1, Req 44, 38, 23, 49). Establece las dos
-- raices de agregado del modulo:
--   * activo_fijo  : bien depreciable de la Empresa (costo, fecha de adquisicion,
--                    vida util, metodo de depreciacion, valor residual y
--                    depreciacion acumulada); estado activo/baja (baja logica).
--   * depreciacion : registro de la depreciacion de un periodo por Activo_Fijo,
--                    con el monto aplicado, la depreciacion acumulada resultante y
--                    el enlace opcional a la Poliza_Contable generada (Req 38.2).
--
-- Replica EXACTAMENTE el patron reutilizable de V11..V36 para toda tabla
-- tenant-scoped:
--   * tenant_id UUID NOT NULL (Req 23.1) + FK -> empresa,
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id,
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron documentado en V2__rls_multi_tenant.sql y V17/V33.
--
-- Requisitos cubiertos:
--   - Req 44.1 (alta con datos obligatorios): activo_fijo con costo, fecha de
--     adquisicion, vida util (meses) y metodo de depreciacion NOT NULL, id UUID PK.
--   - Req 44.2 (rechazo de faltantes/invalidos): CHECKs de dominio (costo > 0,
--     vida_util_meses > 0, valor_residual en [0, costo], metodo/estado acotados).
--     La validacion primaria la aplica el dominio (422); estos CHECK son la
--     segunda capa de defensa.
--   - Req 44.3 (depreciacion del periodo + Poliza_Contable, Req 38.2): tabla
--     depreciacion con poliza_contable_id (FK opcional -> poliza_contable) y
--     UNIQUE (tenant_id, activo_fijo_id, periodo) (una depreciacion por activo y
--     periodo).
--   - Req 44.4 (baja o venta conservando historico): columna estado con CHECK IN
--     ('activo','baja'); la baja es logica (no se borra el Activo_Fijo ni su
--     historico de depreciaciones).
--   - Req 44.5 (listado paginado 20/100 con filtro por estado): indice
--     (tenant_id, estado) de apoyo al filtro.
--   - Req 44.6 (auditoria al alta, depreciar y baja): la registra la aplicacion
--     via AuditoriaPort (no BD).
--   - Req 23 (multi-tenant): tenant_id + RLS. Req 49 (concurrencia): version.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. ESTADO activo/baja (NO booleano): a diferencia de `cliente.activo`
--      (booleano de borrado logico), aqui se modela un estado explicito
--      VARCHAR(12) con CHECK IN ('activo','baja') porque el requisito habla de
--      "dar de baja" (Req 44.4) como una transicion de negocio auditable con
--      etiqueta propia. Es una maquina de estados minima (activo -> baja, final);
--      el dominio la aplica. El filtro del listado (Req 44.5) es por esta columna.
--   2. VIDA UTIL EN MESES (vida_util_meses): la depreciacion se corre por periodo
--      mensual 'AAAA-MM' (Req 44.3), por lo que la vida util se expresa en meses
--      para que la depreciacion de linea recta sea (costo - valor_residual) /
--      vida_util_meses por periodo.
--   3. METODO acotado a ('linea_recta','saldos_decrecientes') (etiquetas ASCII en
--      minusculas, coherentes con la convencion de V17/V33). El calculo de cada
--      metodo vive en el dominio (funcion pura), no en la BD.
--   4. INVARIANTE DE ACUMULACION: depreciacion_acumulada NUNCA excede
--      (costo - valor_residual). El dominio acota (clamp) el monto del periodo a
--      lo sumo a la base depreciable restante. En la BD se refuerza con el CHECK
--      ck_activo_fijo_acumulada_no_excede_base.
--   5. UNA DEPRECIACION POR ACTIVO Y PERIODO: UNIQUE
--      (tenant_id, activo_fijo_id, periodo) impide correr dos veces el mismo
--      periodo para el mismo Activo_Fijo (Req 44.3). La aplicacion pre-verifica y
--      captura la violacion del indice como segunda capa de defensa (409/422).
--   6. POLIZA_CONTABLE_ID OPCIONAL: la depreciacion del periodo genera la
--      Poliza_Contable correspondiente (Req 44.3/38.2) invocando al puerto de
--      contabilidad. Si el catalogo contable del tenant aun no define las cuentas
--      estandar de depreciacion (ver adaptador), la depreciacion se registra sin
--      poliza (poliza_contable_id NULL) y la aplicacion lo audita; por eso la
--      columna es NULL. La FK no cascada preserva la inmutabilidad contable.
--   7. PERMISOS (Req 3): V5 ya sembro activo_fijo:{crear,leer,listar} y
--      depreciacion:{crear,leer}, asignados al rol `contabilidad`
--      (a0000000-0000-0000-0000-00000000000b). Este bloque necesita ADEMAS
--      activo_fijo:cambiar_estado para el endpoint de baja (Req 44.4). Se siembra
--      (ON CONFLICT DO NOTHING) y se enlaza al mismo rol, con el estilo de V33.
--   8. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID (fabrica de dominio), coherente
--      con V11..V36.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- activo_fijo
--   Bien depreciable de la Empresa (Req 44.1). tenant-scoped (Req 23). estado
--   inicial 'activo'; 'baja' es final (Req 44.4). La depreciacion acumulada nunca
--   excede la base depreciable (costo - valor_residual) (DECISION 4).
-- ----------------------------------------------------------------------------
CREATE TABLE activo_fijo (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    nombre                  VARCHAR(200)   NOT NULL,
    costo                   NUMERIC(18, 2) NOT NULL,
    fecha_adquisicion       DATE           NOT NULL,
    vida_util_meses         INTEGER        NOT NULL,
    metodo_depreciacion     VARCHAR(20)    NOT NULL,
    valor_residual          NUMERIC(18, 2) NOT NULL DEFAULT 0,
    depreciacion_acumulada  NUMERIC(18, 2) NOT NULL DEFAULT 0,
    estado                  VARCHAR(12)    NOT NULL DEFAULT 'activo',
    -- Columnas heredadas de TenantScopedEntity.
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT pk_activo_fijo PRIMARY KEY (id),
    CONSTRAINT fk_activo_fijo_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Nombre no vacio (Req 44.1).
    CONSTRAINT ck_activo_fijo_nombre CHECK (length(btrim(nombre)) >= 1),
    -- Costo estrictamente positivo (Req 44.1, 44.2).
    CONSTRAINT ck_activo_fijo_costo_positivo CHECK (costo > 0),
    -- Vida util estrictamente positiva en meses (Req 44.1, 44.2).
    CONSTRAINT ck_activo_fijo_vida_util_positiva CHECK (vida_util_meses > 0),
    -- Metodo de depreciacion acotado (DECISION 3).
    CONSTRAINT ck_activo_fijo_metodo CHECK (
        metodo_depreciacion IN ('linea_recta', 'saldos_decrecientes')),
    -- Valor residual no negativo y no mayor que el costo (Req 44.2).
    CONSTRAINT ck_activo_fijo_valor_residual_no_negativo CHECK (valor_residual >= 0),
    CONSTRAINT ck_activo_fijo_valor_residual_menor_costo CHECK (valor_residual <= costo),
    -- Depreciacion acumulada no negativa y acotada a la base depreciable (DECISION 4).
    CONSTRAINT ck_activo_fijo_acumulada_no_negativa CHECK (depreciacion_acumulada >= 0),
    CONSTRAINT ck_activo_fijo_acumulada_no_excede_base CHECK (
        depreciacion_acumulada <= costo - valor_residual),
    -- Estado acotado (DECISION 1).
    CONSTRAINT ck_activo_fijo_estado CHECK (estado IN ('activo', 'baja'))
);

CREATE INDEX ix_activo_fijo_tenant_id ON activo_fijo (tenant_id);

-- Apoyo al filtro del listado por estado (Req 44.5), acotado al tenant.
CREATE INDEX ix_activo_fijo_tenant_estado ON activo_fijo (tenant_id, estado);

-- ----------------------------------------------------------------------------
-- depreciacion
--   Registro de la depreciacion de un periodo por Activo_Fijo (Req 44.3).
--   tenant-scoped (Req 23). Una depreciacion por (activo_fijo, periodo) (DECISION
--   5). poliza_contable_id opcional enlaza la Poliza_Contable generada (DECISION
--   6).
-- ----------------------------------------------------------------------------
CREATE TABLE depreciacion (
    id                                UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                         UUID           NOT NULL,
    activo_fijo_id                    UUID           NOT NULL,
    -- Periodo mensual 'AAAA-MM' (DECISION 2).
    periodo                           VARCHAR(7)     NOT NULL,
    monto                             NUMERIC(18, 2) NOT NULL,
    depreciacion_acumulada_resultante NUMERIC(18, 2) NOT NULL,
    poliza_contable_id                UUID,
    registrada_en                     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Columnas heredadas de TenantScopedEntity.
    version                           BIGINT         NOT NULL DEFAULT 0,
    created_at                        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                        VARCHAR(255),
    updated_by                        VARCHAR(255),
    CONSTRAINT pk_depreciacion PRIMARY KEY (id),
    CONSTRAINT fk_depreciacion_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_depreciacion_activo_fijo FOREIGN KEY (activo_fijo_id)
        REFERENCES activo_fijo (id),
    -- Enlace opcional a la Poliza_Contable generada; sin cascada (historico
    -- contable inmutable) (DECISION 6).
    CONSTRAINT fk_depreciacion_poliza FOREIGN KEY (poliza_contable_id)
        REFERENCES poliza_contable (id),
    -- Formato del periodo 'AAAA-MM'.
    CONSTRAINT ck_depreciacion_periodo_formato CHECK (periodo ~ '^[0-9]{4}-[0-9]{2}$'),
    -- Montos no negativos (Req 44.3).
    CONSTRAINT ck_depreciacion_monto_no_negativo CHECK (monto >= 0),
    CONSTRAINT ck_depreciacion_acumulada_no_negativa CHECK (
        depreciacion_acumulada_resultante >= 0),
    -- Una depreciacion por Activo_Fijo y periodo dentro del tenant (DECISION 5).
    CONSTRAINT uq_depreciacion_activo_periodo UNIQUE (tenant_id, activo_fijo_id, periodo)
);

CREATE INDEX ix_depreciacion_tenant_id ON depreciacion (tenant_id);

-- Apoyo a la consulta del historico de depreciaciones por Activo_Fijo, acotado al
-- tenant.
CREATE INDEX ix_depreciacion_tenant_activo ON depreciacion (tenant_id, activo_fijo_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V33/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE activo_fijo ENABLE ROW LEVEL SECURITY;
ALTER TABLE activo_fijo FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON activo_fijo
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE depreciacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE depreciacion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON depreciacion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- PERMISOS COMPLEMENTARIOS (Req 3) -- DECISION 7
--   V5 sembro activo_fijo:{crear,leer,listar} y depreciacion:{crear,leer},
--   asignados al rol `contabilidad` (a0000000-0000-0000-0000-00000000000b).
--   Este bloque necesita ADEMAS activo_fijo:cambiar_estado para el endpoint de
--   baja (Req 44.4). Se siembra (ON CONFLICT DO NOTHING por la clave natural
--   recurso, operacion) y se enlaza al MISMO rol predefinido `contabilidad`, con
--   el estilo de V33/V31. El resto de permisos NO se re-siembran.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('activo_fijo', 'cambiar_estado')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE p.recurso = 'activo_fijo' AND p.operacion = 'cambiar_estado'
ON CONFLICT DO NOTHING;

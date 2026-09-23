-- ============================================================================
-- V23__monetizacion_factura_renta.sql
--
-- Monetizacion de modulos (PARTE 2 de 2): FACTURA DE RENTA mensual por Empresa.
-- Suma los modulos HABILITADOS de la Empresa valuados con su PRECIO APLICABLE
-- (precio especial negociado de la Empresa > precio de lista del modulo, PARTE 1)
-- en la MONEDA DE FACTURACION de la Empresa. Depende de V22 (catalogo, monedas,
-- precios) y de suscripcion.modulos_habilitados (V21) para saber que modulos
-- estan habilitados.
--
-- ----------------------------------------------------------------------------
-- MONEDA DE FACTURACION DE LA EMPRESA
-- ----------------------------------------------------------------------------
--   Se agrega `moneda_facturacion` a `suscripcion` (la relacion Empresa<->Plan),
--   FK -> moneda(codigo). Es la moneda en la que se factura la renta de esa
--   Empresa. NULLABLE: si es NULL, la factura no puede emitirse hasta fijarla
--   (la aplicacion lo valida). Un ADD COLUMN nullable no altera la RLS de
--   suscripcion (V2).
--
-- ----------------------------------------------------------------------------
-- ALCANCE / RLS
-- ----------------------------------------------------------------------------
--   `factura_renta` y `factura_renta_linea` son de NIVEL PLATAFORMA
--   (facturacion del CRM a sus Empresas cliente, administrada por super_admin),
--   NO datos de negocio del tenant. Igual que las tablas de precios (V22) y por
--   la misma razon (el super_admin opera sin app.current_tenant fijado), NO se
--   les aplica RLS; el aislamiento se garantiza por RBAC (permisos de plataforma
--   reservados a super_admin). Guardan `tenant_id` para saber a que Empresa se
--   factura, pero NO son tenant-scoped por RLS. NO confundir con la Factura CFDI
--   de negocio (Req 34, otro modulo).
--
-- CONVENCIONES: PK UUID gen_random_uuid(); NUMERIC(18,2) para importes; version;
--   timestamptz UTC; claves de modulo/moneda coherentes con V21/V22.
-- ============================================================================

-- Moneda de facturacion de la Empresa (sobre su Suscripcion).
ALTER TABLE suscripcion
    ADD COLUMN IF NOT EXISTS moneda_facturacion VARCHAR(3);

ALTER TABLE suscripcion
    ADD CONSTRAINT fk_suscripcion_moneda
    FOREIGN KEY (moneda_facturacion) REFERENCES moneda (codigo);

COMMENT ON COLUMN suscripcion.moneda_facturacion IS
    'Moneda (ISO 4217) en la que se factura la renta de modulos de esta Empresa '
    '(monetizacion, V23). NULL = sin moneda de facturacion fijada; la factura de '
    'renta no puede emitirse hasta definirla.';

-- ----------------------------------------------------------------------------
-- factura_renta
--   Cabecera de la factura de renta mensual de una Empresa. Tabla de plataforma
--   (sin RLS). `periodo` = mes facturado (primer dia del mes, DATE). Estado
--   simple: 'emitida' (unica por (tenant, periodo, moneda) para evitar
--   duplicados del mismo periodo).
-- ----------------------------------------------------------------------------
CREATE TABLE factura_renta (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    periodo        DATE          NOT NULL,
    moneda_codigo  VARCHAR(3)    NOT NULL,
    total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    estado         VARCHAR(20)   NOT NULL DEFAULT 'emitida',
    emitida_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_factura_renta PRIMARY KEY (id),
    CONSTRAINT fk_factura_renta_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_factura_renta_moneda  FOREIGN KEY (moneda_codigo) REFERENCES moneda (codigo),
    CONSTRAINT ck_factura_renta_total  CHECK (total >= 0 AND total <= 999999999999.99),
    CONSTRAINT ck_factura_renta_estado CHECK (estado IN ('emitida', 'cancelada')),
    -- Una factura por Empresa, periodo y moneda (evita duplicar el mismo mes).
    CONSTRAINT uq_factura_renta_periodo UNIQUE (tenant_id, periodo, moneda_codigo)
);

CREATE INDEX ix_factura_renta_tenant ON factura_renta (tenant_id);

-- ----------------------------------------------------------------------------
-- factura_renta_linea
--   Una linea por modulo habilitado facturado, con su clave, nombre visible y el
--   precio aplicado (especial o de lista) en la moneda de la factura.
-- ----------------------------------------------------------------------------
CREATE TABLE factura_renta_linea (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    factura_renta_id   UUID          NOT NULL,
    modulo_clave       VARCHAR(60)   NOT NULL,
    modulo_nombre      VARCHAR(120)  NOT NULL,
    precio_aplicado    NUMERIC(18,2) NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    CONSTRAINT pk_factura_renta_linea PRIMARY KEY (id),
    CONSTRAINT fk_factura_linea_factura FOREIGN KEY (factura_renta_id)
        REFERENCES factura_renta (id) ON DELETE CASCADE,
    CONSTRAINT ck_factura_linea_precio CHECK (precio_aplicado >= 0 AND precio_aplicado <= 999999999.99),
    -- Un modulo no se repite dentro de la misma factura.
    CONSTRAINT uq_factura_linea_modulo UNIQUE (factura_renta_id, modulo_clave)
);

CREATE INDEX ix_factura_renta_linea_factura ON factura_renta_linea (factura_renta_id);

-- ----------------------------------------------------------------------------
-- Permisos de plataforma para la factura de renta (super_admin, Req 24.3).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('factura_renta', 'crear'),
    ('factura_renta', 'leer'),
    ('factura_renta', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE p.recurso = 'factura_renta'
ON CONFLICT DO NOTHING;
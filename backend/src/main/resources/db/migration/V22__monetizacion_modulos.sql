-- ============================================================================
-- V22__monetizacion_modulos.sql
--
-- Monetizacion de modulos (PARTE 1 de 2): catalogo de modulos facturables,
-- monedas soportadas y el PRECIO de cada modulo POR MONEDA, mas un precio
-- ESPECIAL negociado por Empresa (Req 24.3, ambito super_admin). La PARTE 2
-- (otra migracion/tarea posterior) construira la FACTURA de renta mensual por
-- Empresa que suma los modulos habilitados; esta migracion NO crea la factura,
-- pero modela las entidades para que la parte 2 las consuma sin friccion.
--
-- ----------------------------------------------------------------------------
-- DECISION DE MONEDA (confirmada por el usuario)
-- ----------------------------------------------------------------------------
--   "Precio por modulo en cada moneda": cada modulo tiene un precio EXPLICITO
--   en cada moneda. NO hay conversion automatica ni tipos de cambio. Cada
--   Empresa se factura en SU moneda usando el precio explicito definido para
--   esa moneda. Por eso `precio_modulo` guarda (modulo, moneda, precio) y NO
--   existe tabla de tipos de cambio.
--
-- ----------------------------------------------------------------------------
-- ALCANCE / RLS (IMPORTANTE)
-- ----------------------------------------------------------------------------
--   Estas tablas son de NIVEL PLATAFORMA, administradas por el super_admin
--   (tenant_id NULL en su contexto). Siguen el mismo patron que `plan` y
--   `empresa` (V1): son @Entity planas con su propia columna `version` y marcas
--   de auditoria, SIN heredar de TenantScopedEntity y SIN Row-Level Security.
--
--   `empresa_modulo_precio` referencia a un tenant (columna `tenant_id`) porque
--   guarda el precio negociado de UNA Empresa concreta, pero es administrada
--   POR EL super_admin a nivel plataforma. Aqui se toma una DECISION explicita:
--   NO se aplica RLS a NINGUNA de estas tablas de precios. Motivo: el
--   super_admin opera con `app.current_tenant` SIN fijar (contexto de
--   plataforma, tenant NULL); una politica RLS tipo `tenant_isolation`
--   (como la de `suscripcion`) fallaria en modo "fail-closed" y ocultaria/
--   bloquearia TODAS las filas para el super_admin, que es justamente quien
--   debe gestionar los precios de TODAS las Empresas. Igual que `plan` y
--   `empresa` (que tampoco tienen RLS), el aislamiento se garantiza por RBAC en
--   el controlador (permisos de plataforma reservados a super_admin), no por
--   filtro de tenant (Req 24.3, 27.7).
--
-- ----------------------------------------------------------------------------
-- CONVENCIONES
-- ----------------------------------------------------------------------------
--   * PK UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1);
--     la aplicacion tambien puede proveer su propio UUID (fabrica de dominio).
--   * MONEDA: NUMERIC(18,2) para el precio (convencion del proyecto, BigDecimal
--     escala 2 en Java). Rango del CHECK: [0.00, 999,999,999.99]. A diferencia
--     de `precio_producto` (V12, minimo 0.01) aqui se permite 0.00 porque un
--     modulo puede ser GRATUITO. Se rechazan negativos y valores sobre el tope,
--     reflejando MonetizacionValidaciones (422 en la aplicacion, defensa en BD).
--   * Codigo de moneda ISO 4217 (3 letras, en mayusculas): MXN, USD, EUR...
--   * clave de modulo: en minusculas/recortada, COHERENTE con la normalizacion
--     de `plan.modulos_habilitados` / `suscripcion.modulos_habilitados`, para
--     que la factura de la parte 2 pueda casar los modulos habilitados de una
--     Empresa con las entradas del catalogo.
--   * timestamptz (UTC) para las marcas de auditoria.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- moneda
--   Catalogo de monedas soportadas (ISO 4217). PK natural: codigo de 3 letras
--   en mayusculas. `activo` permite retirar una moneda sin borrar su historial.
--   Tabla de plataforma (sin tenant, sin RLS).
-- ----------------------------------------------------------------------------
CREATE TABLE moneda (
    codigo      VARCHAR(3)  NOT NULL,
    nombre      VARCHAR(60) NOT NULL,
    activo      BOOLEAN     NOT NULL DEFAULT TRUE,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_moneda PRIMARY KEY (codigo),
    -- ISO 4217: exactamente 3 letras mayusculas (defensa en BD; el dominio lo
    -- valida y normaliza a mayusculas antes de persistir).
    CONSTRAINT ck_moneda_codigo_iso CHECK (codigo ~ '^[A-Z]{3}$')
);

-- Semilla de monedas comunes (el super_admin puede agregar mas).
INSERT INTO moneda (codigo, nombre) VALUES
    ('MXN', 'Peso mexicano'),
    ('USD', 'Dolar estadounidense'),
    ('EUR', 'Euro')
ON CONFLICT (codigo) DO NOTHING;

-- ----------------------------------------------------------------------------
-- catalogo_modulo
--   Catalogo de modulos facturables. `clave` es la clave canonica del modulo
--   (minusculas), la MISMA que aparece en plan.modulos_habilitados /
--   suscripcion.modulos_habilitados, para que la factura de la parte 2 empareje
--   modulos habilitados con precios. Tabla de plataforma (sin tenant, sin RLS).
-- ----------------------------------------------------------------------------
CREATE TABLE catalogo_modulo (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    clave        VARCHAR(60)   NOT NULL,
    nombre       VARCHAR(120)  NOT NULL,
    descripcion  VARCHAR(500),
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    CONSTRAINT pk_catalogo_modulo PRIMARY KEY (id),
    CONSTRAINT uq_catalogo_modulo_clave UNIQUE (clave)
);

-- Semilla del catalogo con las claves canonicas de modulo que el sistema ya
-- usa (derivadas del mapa de modulos de design.md). Claves en minusculas ASCII,
-- coherentes con la normalizacion de modulos_habilitados (minusculas/trim). El
-- super_admin puede agregar/editar mas modulos despues.
INSERT INTO catalogo_modulo (clave, nombre, descripcion) VALUES
    ('comercial',          'Comercial (CRM)',             'Clientes, contactos, oportunidades, cotizaciones, catalogo de productos y canal de venta.'),
    ('redes-sociales',     'Redes sociales',              'Bandeja unificada, publicaciones, campanas y analitica social.'),
    ('operacion',          'Operacion y produccion',      'Ordenes de fabricacion, levantamiento, permisos, proyectos e inventario.'),
    ('inventario-avanzado','Inventario avanzado',         'Almacenes, Kardex, lotes, costeo (promedio/PEPS) e inventario perpetuo.'),
    ('mantenimiento',      'Mantenimiento',               'Contratos de mantenimiento, tickets de servicio y SLA.'),
    ('compras',            'Compras',                     'Proveedores, requisiciones, ordenes de compra, recepciones y 3 vias.'),
    ('facturacion',        'Facturacion (CFDI)',          'Facturas CFDI, timbrado/cancelacion, complementos de pago y notas de credito.'),
    ('contabilidad',       'Contabilidad y finanzas',     'Catalogo de cuentas, polizas, CxC, CxP y estados financieros.'),
    ('tesoreria',          'Tesoreria',                   'Cuentas bancarias, estados de cuenta y conciliacion bancaria.'),
    ('activos-fijos',      'Activos fijos',               'Activos fijos y depreciacion.'),
    ('rh-nomina',          'RH y nomina',                 'Empleados, contratos laborales, incidencias, nomina y organizacion de personal.'),
    ('portal-cliente',     'Portal del cliente',          'Acceso restringido de clientes.'),
    ('estrategia',         'Estrategia',                  'Mision/vision/valores y objetivos estrategicos con resultados clave.'),
    ('presupuestos',       'Presupuestos',                'Presupuestos por area y periodo con variacion real vs estimado.'),
    ('reportes-bi',        'Reportes y BI',               'Tableros, reportes y business intelligence.')
ON CONFLICT (clave) DO NOTHING;

-- ----------------------------------------------------------------------------
-- precio_modulo
--   Precio EXPLICITO de un modulo del catalogo en una moneda (sin conversion).
--   Un precio por (modulo, moneda). Tabla de plataforma (sin tenant, sin RLS).
--   Es el precio de LISTA del modulo; la factura de la parte 2 lo usa como
--   precio base cuando la Empresa no tiene un precio negociado propio.
-- ----------------------------------------------------------------------------
CREATE TABLE precio_modulo (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    catalogo_modulo_id UUID          NOT NULL,
    moneda_codigo      VARCHAR(3)    NOT NULL,
    precio             NUMERIC(18, 2) NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    CONSTRAINT pk_precio_modulo PRIMARY KEY (id),
    CONSTRAINT fk_precio_modulo_catalogo FOREIGN KEY (catalogo_modulo_id) REFERENCES catalogo_modulo (id),
    CONSTRAINT fk_precio_modulo_moneda   FOREIGN KEY (moneda_codigo) REFERENCES moneda (codigo),
    -- Rango de precio: se permite 0.00 (modulo gratuito). Refleja EXACTAMENTE
    -- MonetizacionValidaciones (422 en la aplicacion).
    CONSTRAINT ck_precio_modulo_rango CHECK (precio >= 0 AND precio <= 999999999.99),
    -- Un unico precio por modulo y moneda.
    CONSTRAINT uq_precio_modulo_modulo_moneda UNIQUE (catalogo_modulo_id, moneda_codigo)
);

CREATE INDEX ix_precio_modulo_catalogo ON precio_modulo (catalogo_modulo_id);

-- ----------------------------------------------------------------------------
-- empresa_modulo_precio
--   Precio ESPECIAL negociado (override/descuento) de una Empresa para un
--   modulo en una moneda. Aunque referencia un tenant (`tenant_id`), es una
--   tabla administrada por el super_admin a nivel plataforma: SIN RLS (ver nota
--   de cabecera). Un unico precio especial por (empresa, modulo, moneda).
--   La factura de la parte 2 prefiere este precio sobre el de `precio_modulo`.
-- ----------------------------------------------------------------------------
CREATE TABLE empresa_modulo_precio (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL,
    catalogo_modulo_id UUID          NOT NULL,
    moneda_codigo      VARCHAR(3)    NOT NULL,
    precio             NUMERIC(18, 2) NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    CONSTRAINT pk_empresa_modulo_precio PRIMARY KEY (id),
    CONSTRAINT fk_emp_mod_precio_empresa  FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_emp_mod_precio_catalogo FOREIGN KEY (catalogo_modulo_id) REFERENCES catalogo_modulo (id),
    CONSTRAINT fk_emp_mod_precio_moneda   FOREIGN KEY (moneda_codigo) REFERENCES moneda (codigo),
    CONSTRAINT ck_emp_mod_precio_rango CHECK (precio >= 0 AND precio <= 999999999.99),
    -- Un unico precio especial por Empresa, modulo y moneda.
    CONSTRAINT uq_emp_mod_precio UNIQUE (tenant_id, catalogo_modulo_id, moneda_codigo)
);

CREATE INDEX ix_empresa_modulo_precio_tenant ON empresa_modulo_precio (tenant_id);

-- ----------------------------------------------------------------------------
-- Permisos de plataforma para la monetizacion (Req 3.1, 24.3, 27.7). Se anaden
-- al catalogo de permisos atomicos y se enlazan UNICAMENTE al rol super_admin
-- (UUID a0000000-0000-0000-0000-000000000001, definido en V5). Mirror del
-- patron de siembra de V12. Recursos: modulo_catalogo, precio_modulo, moneda.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('modulo_catalogo', 'crear'),
    ('modulo_catalogo', 'leer'),
    ('modulo_catalogo', 'listar'),
    ('modulo_catalogo', 'actualizar'),
    ('precio_modulo',   'crear'),
    ('precio_modulo',   'leer'),
    ('precio_modulo',   'listar'),
    ('precio_modulo',   'actualizar'),
    ('moneda',          'crear'),
    ('moneda',          'leer'),
    ('moneda',          'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- Enlace a super_admin (SOLO recursos de plataforma, Req 27.7).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE p.recurso IN ('modulo_catalogo', 'precio_modulo', 'moneda')
ON CONFLICT DO NOTHING;

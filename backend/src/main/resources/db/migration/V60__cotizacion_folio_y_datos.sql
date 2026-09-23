-- ============================================================================
-- V60__cotizacion_folio_y_datos.sql
--
-- FOLIO legible y DATOS DESCRIPTIVOS de la Cotizacion (comercial-crm, Req 6).
--
-- La migracion V14 creo la tabla `cotizacion` (tenant-scoped, con RLS) con la
-- identidad basica: cliente_id, oportunidad_id, estado, subtotal, total,
-- canal_venta_id, version y marcas de auditoria. Esta migracion agrega:
--   * folio legible por humanos (p. ej. COT-2026-0001), unico por tenant;
--   * datos descriptivos y de vigencia de la Cotizacion (fecha de emision,
--     validez, condiciones, notas, moneda);
--   * marca temporal del ultimo envio por correo (enviada_en);
--   * una tabla auxiliar `cotizacion_folio_seq` para asignar folios secuenciales
--     por (tenant, anio) de forma segura ante concurrencia.
--
-- Todas las columnas nuevas de `cotizacion` son NULLABLE (no destructivo): ya
-- pueden existir filas en `cotizacion`, y agregarlas NOT NULL romperia la
-- migracion. La NO-nulidad del folio en filas NUEVAS la garantiza el dominio /
-- la aplicacion al crear (siempre se asigna un folio); las filas historicas
-- pueden quedar con folio NULL (entorno dev).
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. FOLIO NULLABLE + INDICE UNICO PARCIAL. El folio se modela VARCHAR(30)
--      NULLABLE para no romper con filas existentes. La unicidad se garantiza
--      con un indice unico PARCIAL por tenant sobre (tenant_id, folio) WHERE
--      folio IS NOT NULL: dos Cotizaciones del mismo tenant no pueden compartir
--      folio, pero se permiten multiples filas historicas con folio NULL. El
--      dominio asigna SIEMPRE un folio en las Cotizaciones nuevas.
--
--   2. CONTADOR DE FOLIO POR (TENANT, ANIO) CON UPSERT ATOMICO. Para asignar
--      folios secuenciales COT-<anio>-<nnnn> de forma correcta ante concurrencia
--      se usa una tabla auxiliar `cotizacion_folio_seq` (tenant_id, anio,
--      ultimo). La aplicacion hace un UPSERT
--         INSERT ... VALUES (tenant, anio, 1)
--         ON CONFLICT (tenant_id, anio) DO UPDATE SET ultimo = ultimo + 1
--         RETURNING ultimo;
--      que incrementa y devuelve el siguiente numero en una sola sentencia
--      atomica, sin condiciones de carrera (a diferencia de contar filas). El
--      indice unico parcial del folio actua ademas como red de seguridad.
--
--   3. `cotizacion_folio_seq` SIN RLS, keyed por (tenant_id, anio). Es un mero
--      contador tecnico de folios, no un dato de negocio consultable: no expone
--      informacion sensible entre tenants (solo un entero por anio) y necesita
--      un UPSERT atomico que la RLS complicaria. Se deja como tabla de
--      plataforma con PK compuesta (tenant_id, anio). La aplicacion siempre
--      opera sobre su propio (tenant_id, anio), derivado del contexto.
--
--   4. MONEDA. moneda VARCHAR(3) por Cotizacion (ISO 4217), por defecto 'MXN'.
--      Se mantiene simple: sin conversion FX; es la divisa en la que se expresa
--      la Cotizacion y su PDF.
--
-- ALCANCE / SEGURIDAD:
--   * NO se toca la Row-Level Security de `cotizacion` (Capa 2, V14): estas
--     columnas no participan del aislamiento por tenant (Req 23). El tenant_id y
--     sus politicas quedan intactos.
--   * NO se tocan columnas ni indices existentes: esta migracion solo SUMA.
--
-- Requisitos cubiertos:
--   - Req 6   : Cotizacion mas completa (folio legible, datos descriptivos).
--   - Migracion versionada y NO destructiva (solo agrega columnas/objetos).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Columnas nuevas en `cotizacion` (todas NULLABLE, no destructivo).
-- IF NOT EXISTS para tolerar re-aplicaciones manuales sin dejar estado parcial.
-- ----------------------------------------------------------------------------
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS folio          VARCHAR(30);
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS fecha_emision  DATE;
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS valido_hasta   DATE;
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS condiciones    VARCHAR(2000);
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS notas          VARCHAR(2000);
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS moneda         VARCHAR(3) DEFAULT 'MXN';
ALTER TABLE cotizacion ADD COLUMN IF NOT EXISTS enviada_en     TIMESTAMPTZ;

-- Indice UNICO PARCIAL del folio por tenant (Decision 1): garantiza folios
-- unicos dentro del tenant, permitiendo multiples filas historicas con folio
-- NULL. Es la red de seguridad del UPSERT del contador.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cotizacion_tenant_folio
    ON cotizacion (tenant_id, folio)
    WHERE folio IS NOT NULL;

-- CHECK LENIENTE de la moneda: 3 letras mayusculas (ISO 4217) o NULL (opcional).
-- El dominio ya normaliza a 'MXN' por defecto; esta restriccion es una defensa
-- de datos a nivel de base sin volver obligatoria la columna.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cotizacion_moneda_formato'
    ) THEN
        ALTER TABLE cotizacion
            ADD CONSTRAINT ck_cotizacion_moneda_formato
            CHECK (moneda IS NULL OR moneda ~ '^[A-Z]{3}$');
    END IF;
END $$;

COMMENT ON COLUMN cotizacion.folio IS
    'Folio legible por humanos (p. ej. COT-2026-0001); unico por tenant (uq_cotizacion_tenant_folio). Lo asigna el dominio al crear.';
COMMENT ON COLUMN cotizacion.fecha_emision IS
    'Fecha de emision de la Cotizacion (por defecto la fecha de creacion); opcional a nivel de base.';
COMMENT ON COLUMN cotizacion.valido_hasta IS
    'Fecha de vigencia/expiracion de la Cotizacion; opcional. Si esta presente, el dominio exige valido_hasta >= fecha_emision.';
COMMENT ON COLUMN cotizacion.condiciones IS
    'Terminos y condiciones de la Cotizacion; texto libre opcional (max. 2000).';
COMMENT ON COLUMN cotizacion.notas IS
    'Notas libres de la Cotizacion; texto opcional (max. 2000).';
COMMENT ON COLUMN cotizacion.moneda IS
    'Moneda de la Cotizacion (ISO 4217); por defecto MXN. Sin conversion FX.';
COMMENT ON COLUMN cotizacion.enviada_en IS
    'Marca temporal (UTC) del ultimo envio por correo de la Cotizacion; NULL si nunca se envio.';

-- ----------------------------------------------------------------------------
-- cotizacion_folio_seq
--   Contador tecnico de folios por (tenant, anio) (Decision 2 y 3). Tabla de
--   plataforma SIN RLS, con PK compuesta. La aplicacion incrementa `ultimo` con
--   un UPSERT atomico y usa el valor devuelto como consecutivo del folio.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cotizacion_folio_seq (
    tenant_id  UUID     NOT NULL,
    anio       INTEGER  NOT NULL,
    ultimo     INTEGER  NOT NULL DEFAULT 0,
    CONSTRAINT pk_cotizacion_folio_seq PRIMARY KEY (tenant_id, anio),
    CONSTRAINT fk_cotizacion_folio_seq_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT ck_cotizacion_folio_seq_ultimo_no_negativo CHECK (ultimo >= 0)
);

COMMENT ON TABLE cotizacion_folio_seq IS
    'Contador de folios de Cotizacion por (tenant, anio). La aplicacion lo incrementa con UPSERT atomico para asignar el consecutivo COT-<anio>-<nnnn> sin condiciones de carrera.';

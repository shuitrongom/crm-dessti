-- ============================================================================
-- V51__empresa_giro.sql
--
-- Enlace de la Empresa (Tenant) a su Giro (vertical de negocio) de la
-- PLATAFORMA MULTIGIRO (Tarea 4.1, Req 2.3, 11.2, 11.3, 11.4). La migracion
-- V1 creo la tabla `empresa` (unidad tenant, su PK ES el tenant_id) y la V50
-- creo el Catalogo_Giros (`giro`) sembrando el Giro `anuncios-luminosos`
-- (activo). Esta migracion anade a `empresa` la columna `giro_id` que la asocia
-- a UN Giro del catalogo, de modo obligatorio (NOT NULL).
--
-- El Giro es un ATRIBUTO de la Empresa, NUNCA un nuevo eje de aislamiento
-- (Req 8.1): el aislamiento sigue rigiendose por `tenant_id` + RLS de las
-- tablas de negocio. `empresa` es dato de plataforma y NO lleva RLS (coherente
-- con V1/V2/V48), por lo que aqui no se tocan politicas RLS.
--
-- ----------------------------------------------------------------------------
-- ESTRATEGIA: NO DESTRUCTIVA, TRANSACCIONAL Y FAIL-FAST (Req 11.3, 11.4)
-- ----------------------------------------------------------------------------
-- Flyway envuelve cada migracion en UNA sola transaccion en PostgreSQL: o se
-- aplican TODOS los pasos o NINGUNO (no deja estado parcial). La secuencia es
-- la clasica "add-nullable -> backfill -> set-not-null", que permite poblar la
-- nueva columna en Empresas preexistentes ANTES de imponer la obligatoriedad:
--
--   1) Anadir `giro_id UUID` NULLABLE (no destructivo: no altera datos ni
--      columnas existentes; solo agrega una columna vacia).
--   2) Anadir la FK `fk_empresa_giro` hacia `giro(id)` (integridad referencial;
--      se declara antes del backfill para que este solo pueda escribir ids de
--      Giros existentes).
--   3) BACKFILL (Req 11.2): asignar el Giro `anuncios-luminosos` a toda Empresa
--      sin Giro, enlazando por la CLAVE NATURAL `clave` (no por UUID), coherente
--      con la siembra de V50.
--   4) SET NOT NULL (Req 2.3): tras poblar, imponer la obligatoriedad del Giro.
--
-- FAIL-FAST (Req 11.4): si el Catalogo_Giros no contiene `anuncios-luminosos`
-- (p. ej. V50 no aplicada o siembra alterada), el subquery del backfill devuelve
-- NULL, las Empresas quedan con `giro_id` NULL y el paso 4 (SET NOT NULL) FALLA;
-- Flyway revierte la transaccion completa y la BD permanece exactamente como
-- estaba (sin columna `giro_id`, sin estado parcial). Es el comportamiento
-- deseado: detener el despliegue ante un catalogo inconsistente.
--
-- ----------------------------------------------------------------------------
-- CONVENCIONES REPLICADAS DE V1
-- ----------------------------------------------------------------------------
--   * Nombre de la FK: `fk_<tabla>_<referencia>` -> `fk_empresa_giro`
--     (patron de V1: `fk_suscripcion_plan`, `fk_rol_empresa`, ...).
--   * Indice de la columna FK: `ix_<tabla>_<columna>` -> `ix_empresa_giro_id`
--     (patron de V1: `ix_suscripcion_plan_id`, `ix_usuario_tenant_id`, ...).
--     Apoya el conteo de Empresas por Giro (Req 1.5) y las verificaciones de
--     "giro en uso" al desactivarlo (ServicioGiros, Tarea 2.5).
--
-- Requisitos cubiertos:
--   - Req 2.3  : la Empresa referencia un Giro de forma OBLIGATORIA (NOT NULL).
--   - Req 11.2 : backfill de Empresas preexistentes al Giro `anuncios-luminosos`.
--   - Req 11.3 : migracion versionada y NO destructiva (no borra ni altera datos
--                existentes; solo agrega columna, FK e indice).
--   - Req 11.4 : transaccional y fail-fast; ante inconsistencia no deja estado
--                parcial (la transaccion de Flyway revierte).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) COLUMNA giro_id (NULLABLE primero)
--   No destructivo: agrega una columna vacia para poder poblarla antes de
--   imponer NOT NULL. IF NOT EXISTS para tolerar re-aplicaciones manuales.
-- ----------------------------------------------------------------------------
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS giro_id UUID;

-- ----------------------------------------------------------------------------
-- 2) INTEGRIDAD REFERENCIAL: FK empresa.giro_id -> giro.id
--   Convencion de nombre de V1 (`fk_<tabla>_<referencia>`). Sin ON DELETE:
--   un Giro en uso no debe poder eliminarse (comportamiento RESTRICT por
--   defecto), coherente con la regla de "no desactivar/borrar Giro en uso".
-- ----------------------------------------------------------------------------
ALTER TABLE empresa
    ADD CONSTRAINT fk_empresa_giro FOREIGN KEY (giro_id) REFERENCES giro (id);

-- ----------------------------------------------------------------------------
-- 3) BACKFILL (Req 11.2)
--   Asigna el Giro `anuncios-luminosos` a toda Empresa sin Giro, por la clave
--   natural `clave` (no por UUID). Si el Giro no existe, el subquery devuelve
--   NULL y el paso 4 fallara (fail-fast, Req 11.4).
-- ----------------------------------------------------------------------------
UPDATE empresa
   SET giro_id = (SELECT id FROM giro WHERE clave = 'anuncios-luminosos')
 WHERE giro_id IS NULL;

-- ----------------------------------------------------------------------------
-- 4) OBLIGATORIEDAD DEL GIRO (Req 2.3)
--   Tras el backfill, imponer NOT NULL. Si alguna Empresa quedo sin Giro
--   (catalogo inconsistente), este paso falla y Flyway revierte todo (Req 11.4).
-- ----------------------------------------------------------------------------
ALTER TABLE empresa ALTER COLUMN giro_id SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 5) INDICE DE LA COLUMNA FK (convencion de V1)
--   Apoya el conteo de Empresas por Giro (Req 1.5) y la comprobacion de "Giro
--   en uso" al desactivarlo. IF NOT EXISTS para re-aplicaciones manuales.
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_empresa_giro_id ON empresa (giro_id);

COMMENT ON COLUMN empresa.giro_id IS
    'Giro (vertical de negocio) al que pertenece la Empresa. FK obligatoria a giro(id) (Req 2.3). Atributo de la Empresa, no eje de aislamiento (Req 8.1). Empresas preexistentes se backfillearon a anuncios-luminosos (Req 11.2).';
-- ============================================================================
-- V64__contratacion_plan_vs_suscripcion.sql
--
-- CONTRATACION: PLAN vs PAQUETE_SUSCRIPCION (spec plan-vs-suscripcion-contratacion).
--
-- Introduce en el modelo de datos la decision de producto central: cada Empresa
-- se contrata mediante EXACTAMENTE UN instrumento comercial vigente que es O un
-- Plan (largo plazo, duracion > 1 anio, sin prueba) O un Paquete_Suscripcion
-- (corto plazo, duracion <= 1 anio, con opcion de prueba); nunca ambos a la vez
-- (invariante XOR, Req 1).
--
-- Esta migracion cubre exclusivamente el andamiaje de datos (SOLO SQL; el
-- codigo Java del dominio/servicios llega en tareas posteriores):
--   1) `plan.duracion_dias` (duracion del contrato de largo plazo, > 365).
--   2) NUEVO catalogo de plataforma `paquete_suscripcion` (espejo de `plan`,
--      SIN RLS, con atributos de prueba).
--   3) Columnas de instrumento y facturacion en `suscripcion` (el "Contrato"):
--      `tipo_instrumento`, `paquete_suscripcion_id` (FK), `facturacion_activada`,
--      `inicio_facturacion`; `plan_id` pasa a NULLABLE.
--   4) Ampliacion del CHECK de estado a los cinco valores del ciclo de vida.
--   5) Clasificacion de los Contratos existentes (D4/D4-bis) ANTES del XOR.
--   6) CHECK XOR de instrumento (DESPUES de la clasificacion, para no violar las
--      filas legado).
--   7) Saneo del override de modulos inconsistente (Req 11.3): recorte a la
--      interseccion con los modulos del Plan referenciado.
--
-- ----------------------------------------------------------------------------
-- DECISIONES Y CONVENCIONES
-- ----------------------------------------------------------------------------
--   1. NO DESTRUCTIVA E IDEMPOTENTE. Solo se AGREGAN columnas/tablas/restricciones
--      y se AMPLIA un CHECK. Se usan `CREATE TABLE IF NOT EXISTS`,
--      `ADD COLUMN IF NOT EXISTS` y `DROP CONSTRAINT IF EXISTS` antes de cada
--      `ADD CONSTRAINT` para tolerar re-aplicaciones manuales sin dejar estado
--      parcial (Flyway envuelve la migracion en una sola transaccion). Los
--      UPDATE de backfill/saneo son idempotentes por su clausula WHERE.
--
--   2. `paquete_suscripcion` ES DATO DE PLATAFORMA, SIN RLS. Coherente con
--      `plan` y `giro` (V1/V50): es un catalogo compartido por todas las
--      Empresas, administrado por el super_admin. Intencionalmente NO se
--      habilita Row-Level Security ni se define politica `tenant_isolation`
--      (habilitarla romperia la siembra/consultas de plataforma que operan sin
--      `app.current_tenant`). El control de acceso lo aporta el RBAC de
--      plataforma.
--
--   3. `suscripcion` (el Contrato) TIENE RLS (`tenant_isolation`, V2/V53). Esta
--      migracion NO toca su RLS: solo agrega columnas/restricciones. El
--      aislamiento por tenant permanece intacto.
--
--   4. NOMBRE DE CONSTRAINT CONSERVADO EN EL CHECK DE ESTADO. `ck_suscripcion_estado`
--      se recrea con su MISMO nombre (DROP + ADD), ampliando el conjunto de
--      valores admitidos de {activa,suspendida,cancelada} a
--      {activa,en_prueba,suspendida,cancelada,vencida} (Req 5.1, 12.2). Ninguna
--      fila existente viola el nuevo CHECK.
--
--   5. CLASIFICACION DE CONTRATOS LEGADO (D4/D4-bis). Los Contratos existentes
--      referencian un `plan_id` REAL. Para respetar la invariante XOR se
--      clasifican como `tipo_instrumento = 'plan'` (lo veraz respecto al dato
--      actual). Por eso `tipo_instrumento` se agrega con DEFAULT 'plan' y se
--      refuerza con un UPDATE explicito ANTES de anadir el CHECK XOR; de lo
--      contrario las filas legado (plan_id NOT NULL, paquete NULL) lo violarian.
--
--   6. DURACION EN EL CATALOGO (D6). La duracion del contrato vive en el
--      catalogo: `plan.duracion_dias > 365`, `paquete_suscripcion.duracion_dias
--      IN (1..365)`. El Contrato deriva su `vigencia_fin` del catalogo al
--      asignarse (logica de dominio, tareas posteriores).
--
-- Requisitos cubiertos: 1.1, 1.2, 2.2, 3.3, 5.1, 11.3, 12.1, 12.2, 12.3.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) plan.duracion_dias  (Req 2.2, 12.2)
--   Duracion del contrato de largo plazo, en dias. El dominio exige > 365.
--   Se agrega con DEFAULT 730 (2 anios) para BACKFILLEAR de forma segura las
--   filas de Plan legado (un valor > 365 valido). Tras el backfill se QUITA el
--   default para forzar que las inserciones futuras aporten el valor explicito
--   desde el dominio.
-- ----------------------------------------------------------------------------
ALTER TABLE plan ADD COLUMN IF NOT EXISTS duracion_dias INTEGER NOT NULL DEFAULT 730;
ALTER TABLE plan ALTER COLUMN duracion_dias DROP DEFAULT;

-- CHECK de duracion del Plan (> 365 = mayor a un anio). Idempotente por el
-- DROP previo. El default 730 garantiza que las filas legado ya lo cumplen.
ALTER TABLE plan DROP CONSTRAINT IF EXISTS ck_plan_duracion_dias;
ALTER TABLE plan ADD CONSTRAINT ck_plan_duracion_dias CHECK (duracion_dias > 365);

COMMENT ON COLUMN plan.duracion_dias IS
    'Duracion del contrato de Plan en dias. El dominio exige > 365 (compromiso mayor a un anio; Req 2.2/2.3). Filas legado backfilleadas con 730 (2 anios) al aplicar la migracion.';

-- ----------------------------------------------------------------------------
-- 2) TABLA paquete_suscripcion  (Req 3.1, 3.2, 12.2)
--   NUEVO catalogo de plataforma, espejo de `plan`, SIN RLS (DECISION 2).
--   Contratos de corto plazo (duracion <= 365) con opcion de periodo de prueba.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paquete_suscripcion (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- Nombre comercial unico del paquete (identificador visible; no se exponen UUID).
    nombre                VARCHAR(120) NOT NULL,
    max_usuarios          INTEGER      NOT NULL,
    -- Giro (vertical) y moneda de cotizacion: NULLABLE (coherente con `plan`,
    -- V55); el dominio los exige para paquetes nuevos/actualizados.
    giro_id               UUID,
    moneda_codigo         VARCHAR(3),
    -- Precio por modulo {clave_modulo: precio} y lista autoritativa de claves,
    -- mismo patron que `plan` (V55).
    precios_modulos       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    modulos_habilitados   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- Duracion del contrato de corto plazo, en dias: 1..365 (<= 1 anio; Req 3.3/3.4).
    duracion_dias         INTEGER      NOT NULL,
    -- Atributos de periodo de prueba (Req 3.2/3.5).
    admite_prueba         BOOLEAN      NOT NULL DEFAULT FALSE,
    duracion_prueba_meses INTEGER,
    -- Concurrencia optimista y auditoria (patron de plataforma V1/V50).
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT pk_paquete_suscripcion PRIMARY KEY (id),
    CONSTRAINT uq_paquete_suscripcion_nombre UNIQUE (nombre),
    CONSTRAINT fk_paquete_giro   FOREIGN KEY (giro_id)       REFERENCES giro (id),
    CONSTRAINT fk_paquete_moneda FOREIGN KEY (moneda_codigo) REFERENCES moneda (codigo),
    CONSTRAINT ck_paquete_max_usuarios CHECK (max_usuarios >= 0),
    -- Duracion de corto plazo: estrictamente positiva y <= 1 anio (Req 3.3).
    CONSTRAINT ck_paquete_duracion_dias CHECK (duracion_dias > 0 AND duracion_dias <= 365),
    -- Si admite prueba, la duracion de la prueba debe estar definida y ser > 0
    -- (Req 3.5). Si no admite prueba, la columna es indiferente.
    CONSTRAINT ck_paquete_prueba CHECK (
        (admite_prueba = FALSE)
        OR (duracion_prueba_meses IS NOT NULL AND duracion_prueba_meses > 0)
    )
);

-- NOTA (DECISION 2): intencionalmente NO se ejecuta
--   ALTER TABLE paquete_suscripcion ENABLE/FORCE ROW LEVEL SECURITY;
-- ni se crea politica `tenant_isolation`. `paquete_suscripcion` es dato de
-- plataforma, coherente con `plan` y `giro` (V1/V50), que tampoco llevan RLS.

COMMENT ON TABLE paquete_suscripcion IS
    'Catalogo de Paquetes de Suscripcion (contratos de corto plazo, duracion <= 365 dias, con opcion de prueba). Dato de plataforma SIN RLS, coherente con plan/giro. Es una plantilla comercial, no una asignacion a una Empresa.';

-- ----------------------------------------------------------------------------
-- 3) COLUMNAS DE INSTRUMENTO Y FACTURACION EN suscripcion (Contrato)
--   (Req 1.1, 5.x, 8.x, 12.2). `suscripcion` TIENE RLS: aqui NO se toca (DECISION 3).
--   `tipo_instrumento` con DEFAULT 'plan': clasifica las filas legado como Plan
--   (referencian un plan_id real), coherente con el UPDATE del paso 5 y el XOR
--   del paso 6 (DECISION 5).
-- ----------------------------------------------------------------------------
ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS tipo_instrumento       VARCHAR(20) NOT NULL DEFAULT 'plan';
ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS paquete_suscripcion_id UUID;
ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS facturacion_activada   BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS inicio_facturacion     DATE;

-- El instrumento puede ser un Plan O un Paquete: `plan_id` deja de ser
-- obligatorio (la exclusividad la gobierna el CHECK XOR del paso 6).
ALTER TABLE suscripcion ALTER COLUMN plan_id DROP NOT NULL;

-- FK al nuevo catalogo de paquetes. Idempotente por el DROP previo.
ALTER TABLE suscripcion DROP CONSTRAINT IF EXISTS fk_suscripcion_paquete;
ALTER TABLE suscripcion ADD CONSTRAINT fk_suscripcion_paquete
    FOREIGN KEY (paquete_suscripcion_id) REFERENCES paquete_suscripcion (id);

CREATE INDEX IF NOT EXISTS ix_suscripcion_paquete_id ON suscripcion (paquete_suscripcion_id);

COMMENT ON COLUMN suscripcion.tipo_instrumento IS
    'Discriminador del instrumento del Contrato: ''plan'' o ''suscripcion''. Filas legado clasificadas como ''plan'' (referencian un plan_id real; D4/D4-bis).';
COMMENT ON COLUMN suscripcion.paquete_suscripcion_id IS
    'FK al Paquete_Suscripcion del Contrato (paquete_suscripcion.id). NULLABLE. Exactamente uno de plan_id / paquete_suscripcion_id no nulo, segun tipo_instrumento (invariante XOR, ck_suscripcion_instrumento).';
COMMENT ON COLUMN suscripcion.facturacion_activada IS
    'Indica que una prueba (EN_PRUEBA) fue convertida a suscripcion de pago por el super_admin (Req 8).';
COMMENT ON COLUMN suscripcion.inicio_facturacion IS
    'Fecha de inicio de cobro tras activar la facturacion de una prueba (Req 8.2). NULL mientras no se active.';

-- ----------------------------------------------------------------------------
-- 4) CHECK DE ESTADO AMPLIADO  (Req 5.1, 12.2)
--   DROP + ADD con el MISMO nombre (DECISION 4). Amplia el ciclo de vida a los
--   cinco valores. Ninguna fila existente ('activa'/'suspendida'/'cancelada')
--   viola el nuevo CHECK.
-- ----------------------------------------------------------------------------
ALTER TABLE suscripcion DROP CONSTRAINT IF EXISTS ck_suscripcion_estado;
ALTER TABLE suscripcion ADD CONSTRAINT ck_suscripcion_estado CHECK (
    estado IN ('activa', 'en_prueba', 'suspendida', 'cancelada', 'vencida'));

-- ----------------------------------------------------------------------------
-- 5) CLASIFICACION DE CONTRATOS EXISTENTES (D4/D4-bis)  (Req 12.1, 12.3)
--   ANTES del CHECK XOR: los Contratos legado referencian un plan_id real y se
--   clasifican como 'plan'. El DEFAULT 'plan' ya lo cubre; el UPDATE explicito
--   se incluye por claridad/idempotencia y por robustez ante re-aplicaciones.
-- ----------------------------------------------------------------------------
UPDATE suscripcion
   SET tipo_instrumento = 'plan'
 WHERE plan_id IS NOT NULL
   AND paquete_suscripcion_id IS NULL;

-- ----------------------------------------------------------------------------
-- 6) CHECK XOR DE INSTRUMENTO  (Req 1.1, 1.2)
--   DESPUES de la clasificacion (paso 5) para no violar las filas legado.
--   Exactamente uno de plan_id / paquete_suscripcion_id no nulo, segun el tipo.
--   Idempotente por el DROP previo.
-- ----------------------------------------------------------------------------
ALTER TABLE suscripcion DROP CONSTRAINT IF EXISTS ck_suscripcion_instrumento;
ALTER TABLE suscripcion ADD CONSTRAINT ck_suscripcion_instrumento CHECK (
    (tipo_instrumento = 'plan'       AND plan_id IS NOT NULL               AND paquete_suscripcion_id IS NULL)
    OR
    (tipo_instrumento = 'suscripcion' AND paquete_suscripcion_id IS NOT NULL AND plan_id IS NULL)
);

-- ----------------------------------------------------------------------------
-- 7) SANEO DEL OVERRIDE DE MODULOS INCONSISTENTE  (Req 11.3, D4)
--   La Empresa demo ("Demo Factura") y cualquier otra pueden tener un
--   Override_Modulos con modulos AJENOS a los del Plan referenciado (dato
--   inconsistente). Se recorta el override a la INTERSECCION con
--   plan.modulos_habilitados, SOLO cuando el override contiene modulos ajenos.
--   No cambia el comportamiento del gating; es idempotente (tras el recorte la
--   condicion EXISTS del WHERE ya no se cumple).
-- ----------------------------------------------------------------------------
UPDATE suscripcion s
   SET modulos_habilitados = (
       SELECT COALESCE(jsonb_agg(m), '[]'::jsonb)
         FROM jsonb_array_elements_text(s.modulos_habilitados) AS m
        WHERE m IN (SELECT jsonb_array_elements_text(p.modulos_habilitados)
                      FROM plan p
                     WHERE p.id = s.plan_id)
   )
 WHERE s.modulos_habilitados IS NOT NULL
   AND s.plan_id IS NOT NULL
   AND EXISTS (
       SELECT 1
         FROM jsonb_array_elements_text(s.modulos_habilitados) AS m
        WHERE m NOT IN (SELECT jsonb_array_elements_text(p.modulos_habilitados)
                          FROM plan p
                         WHERE p.id = s.plan_id)
   );

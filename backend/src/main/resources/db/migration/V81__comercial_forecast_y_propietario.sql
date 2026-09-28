-- ============================================================================
-- V81__comercial_forecast_y_propietario.sql
--
-- Bloque C del CRM enterprise: forecast de ventas y responsables.
--   (1) Oportunidad: probabilidad de cierre (0..100), fecha de cierre esperada y
--       motivo de perdida, para pronosticar el pipeline (valor ponderado) y dejar
--       constancia de por que se pierde una Oportunidad.
--   (2) Cliente: propietario/vendedor asignado (propietario_usuario_id -> usuario)
--       para accountability comercial.
--
-- Patron NO destructivo (replica V59/V60/V80): solo ADD COLUMN nullable o con
-- DEFAULT que preservan las filas existentes. No se tocan RLS ni indices previos.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. PROBABILIDAD (0..100): entero porcentual. Tiene un DEFAULT sugerido por
--      etapa que el dominio fija al crear/cambiar etapa (nuevo=10, calificado=30,
--      propuesta=50, negociacion=70, ganado=100, perdido=0), pero es EDITABLE por
--      el usuario para afinar el pronostico. Se persiste (no se deriva en cada
--      lectura) para que el forecast sea estable y reproducible. Para filas
--      historicas se rellena segun su etapa actual (UPDATE de backfill abajo).
--   2. FECHA DE CIERRE ESPERADA: DATE nullable. Fecha en la que se espera ganar
--      la Oportunidad; alimenta el forecast por periodo. Opcional.
--   3. MOTIVO DE PERDIDA: VARCHAR(500) nullable. El dominio lo exige (422) al
--      pasar la etapa a 'perdido'; en otras etapas permanece nulo. La BD no lo
--      vuelve obligatorio (seria imposible para filas ya perdidas sin motivo);
--      la regla vive en el dominio.
--   4. PROPIETARIO DEL CLIENTE: propietario_usuario_id UUID nullable con FK ->
--      usuario(id). Sin ON DELETE CASCADE (los usuarios se desactivan, no se
--      borran). La coherencia de tenant (que el usuario pertenezca al mismo
--      tenant que el cliente) la fuerza la aplicacion via findByIdAndTenantId,
--      porque usuario.tenant_id es nullable (super_admin) y no hay RLS que lo
--      garantice a nivel de BD.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- oportunidad: forecast (probabilidad, fecha de cierre esperada, motivo de perdida)
-- ----------------------------------------------------------------------------
ALTER TABLE oportunidad
    ADD COLUMN IF NOT EXISTS probabilidad            INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fecha_cierre_esperada   DATE,
    ADD COLUMN IF NOT EXISTS motivo_perdida          VARCHAR(500);

-- La probabilidad es un porcentaje entero en [0, 100].
ALTER TABLE oportunidad
    ADD CONSTRAINT ck_oportunidad_probabilidad CHECK (probabilidad >= 0 AND probabilidad <= 100);

-- Backfill de la probabilidad de las Oportunidades historicas segun su etapa
-- actual, para que el forecast tenga sentido de inmediato (el DEFAULT 0 solo
-- aplicaria a las nuevas sin etapa, lo cual no ocurre). Coherente con la
-- sugerencia por etapa del dominio (EtapaOportunidad.probabilidadSugerida()).
UPDATE oportunidad SET probabilidad = CASE etapa
        WHEN 'nuevo'       THEN 10
        WHEN 'calificado'  THEN 30
        WHEN 'propuesta'   THEN 50
        WHEN 'negociacion' THEN 70
        WHEN 'ganado'      THEN 100
        WHEN 'perdido'     THEN 0
        ELSE probabilidad
    END
WHERE probabilidad = 0;

-- Apoyo al forecast por fecha de cierre esperada (pipeline por periodo), acotado
-- al tenant.
CREATE INDEX ix_oportunidad_tenant_cierre
    ON oportunidad (tenant_id, fecha_cierre_esperada);

-- ----------------------------------------------------------------------------
-- cliente: propietario/vendedor asignado
-- ----------------------------------------------------------------------------
ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS propietario_usuario_id UUID;

ALTER TABLE cliente
    ADD CONSTRAINT fk_cliente_propietario_usuario
    FOREIGN KEY (propietario_usuario_id) REFERENCES usuario (id);

-- Apoyo a la consulta de la cartera por propietario (mis clientes), acotada al tenant.
CREATE INDEX ix_cliente_tenant_propietario
    ON cliente (tenant_id, propietario_usuario_id);

COMMENT ON COLUMN oportunidad.probabilidad IS
    'Probabilidad de cierre en porcentaje entero [0..100]; sugerida por etapa y editable (V81, forecast).';
COMMENT ON COLUMN oportunidad.fecha_cierre_esperada IS
    'Fecha esperada de cierre (ganar) de la Oportunidad; opcional; alimenta el forecast por periodo (V81).';
COMMENT ON COLUMN oportunidad.motivo_perdida IS
    'Motivo por el que se perdio la Oportunidad; obligatorio en el dominio al pasar a etapa perdido (V81).';
COMMENT ON COLUMN cliente.propietario_usuario_id IS
    'Usuario propietario/vendedor asignado al Cliente; opcional; FK a usuario(id) (V81).';

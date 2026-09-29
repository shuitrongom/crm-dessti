-- ============================================================================
-- V84 — Evidencias documentales del avance de Sitio con flujo de aprobacion
--        (Req 3.2, deber-ser enterprise)
-- ----------------------------------------------------------------------------
-- Contexto: el avance de un Sitio multi-sitio (avance_sitio, V78) llevaba una
-- unica referencia de texto `evidencia_url`. Eso no modela como opera una
-- empresa real: en campo se toman FOTOS/ACTAS que deben (1) subirse como ARCHIVO
-- real, (2) quedar guardadas y visibles para el admin/encargado, y (3) pasar por
-- una AUTORIZACION (aprobar/rechazar) antes de que una fase se de por entregada.
--
-- Esta migracion crea la entidad de evidencia de PRIMERA CLASE: muchas evidencias
-- por avance, cada una con su archivo (referenciado por una clave opaca del
-- almacen de objetos; el binario NO vive en la BD), sus metadatos, su estado de
-- aprobacion (pendiente/aprobada/rechazada) y el rastro de quien la subio y quien
-- la decidio. La guarda de negocio "no entregar sin evidencia aprobada" la aplica
-- el servicio (ServicioProyectos) apoyandose en esta tabla.
--
-- Decisiones:
--   1. Tabla hija de avance_sitio (FK avance_sitio_id). tenant-scoped con RLS
--      identica al resto (ENABLE+FORCE+tenant_isolation), como V78.
--   2. El archivo se guarda FUERA de la BD (filesystem/objeto); aqui solo su
--      clave (clave_almacen), nombre original, MIME y tamano. Asi la BD no se
--      infla ni degrada con binarios.
--   3. La fase que la evidencia respalda se congela al subirla (columna `fase`),
--      para que la evidencia siga ligada a la etapa que documento aunque el Sitio
--      avance despues.
--   4. Estados: pendiente -> aprobada/rechazada (ambos finales). El rechazo lleva
--      motivo_rechazo. Se registra subida_por/en y decidida_por/en (auditoria).
--   5. Permiso nuevo evidencia_avance:aprobar para la DECISION (aprobar/rechazar),
--      asignado a admin_empresa y gerente. SUBIR una evidencia usa el permiso
--      existente proyecto:actualizar (mismo actor operativo que avanza la fase).
-- ============================================================================

CREATE TABLE evidencia_avance_sitio (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    -- Avance de Sitio al que respalda la evidencia (Req 3.2). FK a avance_sitio (V78).
    avance_sitio_id UUID           NOT NULL,
    -- Fase que la evidencia documenta, congelada al subir (etiquetas ASCII de V78).
    fase            VARCHAR(16)    NOT NULL,
    -- Clave opaca del archivo en el almacen de objetos (no el binario).
    clave_almacen   VARCHAR(255)   NOT NULL,
    -- Nombre original del archivo subido (para mostrar/descargar).
    nombre_original VARCHAR(255)   NOT NULL,
    -- Tipo MIME (image/jpeg, image/png, image/webp, application/pdf).
    tipo_mime       VARCHAR(100)   NOT NULL,
    -- Tamano del archivo en bytes (> 0).
    tamano_bytes    BIGINT         NOT NULL,
    -- Estado de aprobacion (Decision 4).
    estado          VARCHAR(16)    NOT NULL DEFAULT 'pendiente',
    -- Motivo del rechazo; NULL salvo cuando estado = 'rechazada'.
    motivo_rechazo  VARCHAR(500),
    -- Instante UTC en que se subio la evidencia.
    subida_en       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Actor e instante de la decision; NULL mientras esta pendiente.
    decidida_por    VARCHAR(255),
    decidida_en     TIMESTAMPTZ,
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria (Req 21.6).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    CONSTRAINT pk_evidencia_avance_sitio PRIMARY KEY (id),
    CONSTRAINT fk_evidencia_avance_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    CONSTRAINT fk_evidencia_avance_avance FOREIGN KEY (avance_sitio_id)
        REFERENCES avance_sitio (id),
    CONSTRAINT ck_evidencia_avance_fase CHECK (
        fase IN ('pendiente', 'en_preparacion', 'en_instalacion', 'entregado')),
    CONSTRAINT ck_evidencia_avance_estado CHECK (
        estado IN ('pendiente', 'aprobada', 'rechazada')),
    CONSTRAINT ck_evidencia_avance_tamano CHECK (tamano_bytes > 0)
);

CREATE INDEX ix_evidencia_avance_tenant_id ON evidencia_avance_sitio (tenant_id);

-- Apoyo a listar por avance y a la guarda de entrega (avance + fase + estado).
CREATE INDEX ix_evidencia_avance_tenant_avance
    ON evidencia_avance_sitio (tenant_id, avance_sitio_id);
CREATE INDEX ix_evidencia_avance_guardaentrega
    ON evidencia_avance_sitio (tenant_id, avance_sitio_id, fase, estado);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V78/V25/V2.
-- ----------------------------------------------------------------------------
ALTER TABLE evidencia_avance_sitio ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidencia_avance_sitio FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON evidencia_avance_sitio
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso de DECISION (aprobar/rechazar) de evidencias — Decision 5.
--   Subir evidencia usa proyecto:actualizar (V5). Aprobar/rechazar es una
--   operacion de control de calidad sensible que se restringe con el permiso
--   nuevo evidencia_avance:aprobar, asignado solo a los roles administrativos
--   (admin_empresa, gerente). Estilo de V9/V30/V78: ON CONFLICT DO NOTHING.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('evidencia_avance', 'aprobar')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- admin_empresa (a0000000-...-000000000002) y gerente (a0000000-...-000000000003).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM (VALUES
        ('a0000000-0000-0000-0000-000000000002'::uuid),
        ('a0000000-0000-0000-0000-000000000003'::uuid)) AS r(id)
CROSS JOIN permiso p
WHERE p.recurso = 'evidencia_avance' AND p.operacion = 'aprobar'
ON CONFLICT DO NOTHING;

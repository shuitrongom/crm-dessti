-- ============================================================================
-- V78 — Avance generico por Sitio para Proyectos multi-sitio (Req 21, 3.2)
-- ----------------------------------------------------------------------------
-- Contexto: el modulo Proyectos (V25) ya es GENERICO a todos los giros. Para el
-- giro anuncios-luminosos el avance de cada Sitio se DERIVA de otros modulos
-- (levantamiento/permiso/OF/instalacion) via AvanceSitioPort; ese avance NO se
-- materializa. Pero para los demas giros (clientes con muchas sucursales:
-- cadenas bancarias, grupos comerciales) no existe una fuente de avance por
-- Sitio, y el perfil generico solo podia mirar "produccion si/no".
--
-- Esta migracion agrega una fuente de avance MATERIALIZADA y EDITABLE por Sitio
-- para los giros genericos: cada Sitio tiene una fase operativa que el usuario
-- avanza manualmente (Pendiente -> En preparacion -> En instalacion -> Entregado).
-- El estado consolidado del Proyecto se deriva de la fase de todos sus Sitios.
--
-- Decisiones:
--   1. Tabla 1:1 opcional con sitio (no columna en `sitio`) para no tocar la
--      entidad Sitio existente ni el flujo de anuncios. Un Sitio sin fila aqui se
--      interpreta como fase 'pendiente' (default logico en el dominio).
--   2. tenant-scoped con RLS identica al resto (V25/V37): ENABLE + FORCE + policy
--      tenant_isolation sobre app.current_tenant.
--   3. Avanzar la fase (secuencia lineal) reutiliza proyecto:actualizar (V5).
--      RETROCEDER/corregir la fase es una correccion administrativa y exige el
--      permiso nuevo proyecto:cambiar_estado, que esta migracion siembra y asigna
--      solo a los roles administrativos (admin_empresa y gerente).
--   4. UNIQUE (tenant_id, sitio_id): a lo sumo una fila de avance por Sitio.
--   5. Cada avance puede referenciar una EVIDENCIA (evidencia_url): enlace al
--      documento/foto/acta que respalda la fase. Se guarda la referencia (no el
--      binario), consistente con el patron del resto del sistema.
-- ============================================================================

CREATE TABLE avance_sitio (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL,
    -- Sitio al que pertenece el avance (Req 21.2). FK a sitio (V25); 1:1 opcional.
    sitio_id    UUID           NOT NULL,
    -- Fase operativa generica del Sitio (Req 3.2). Etiquetas ASCII minusculas.
    -- Secuencia: pendiente -> en_preparacion -> en_instalacion -> entregado.
    fase        VARCHAR(16)    NOT NULL DEFAULT 'pendiente',
    -- Nota opcional del avance (contexto de la etapa actual).
    nota        VARCHAR(500),
    -- Referencia opcional a la evidencia que respalda la fase (URL/enlace a foto,
    -- acta o documento). Se guarda la referencia, no el binario (Decision 5).
    evidencia_url VARCHAR(1000),
    -- Columnas heredadas de TenantScopedEntity (Req 49) y auditoria (Req 21.6).
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT pk_avance_sitio PRIMARY KEY (id),
    CONSTRAINT fk_avance_sitio_empresa FOREIGN KEY (tenant_id) REFERENCES empresa (id),
    -- Sitio existente (Req 21.2). Sin cascada: registro operativo.
    CONSTRAINT fk_avance_sitio_sitio FOREIGN KEY (sitio_id) REFERENCES sitio (id),
    -- Fases permitidas (Req 3.2).
    CONSTRAINT ck_avance_sitio_fase CHECK (
        fase IN ('pendiente', 'en_preparacion', 'en_instalacion', 'entregado')),
    -- A lo sumo un avance por Sitio dentro del tenant (Decision 4).
    CONSTRAINT uq_avance_sitio_sitio UNIQUE (tenant_id, sitio_id)
);

CREATE INDEX ix_avance_sitio_tenant_id ON avance_sitio (tenant_id);

-- Apoyo a la carga del avance por Sitio (y por Proyecto via join a sitio),
-- acotado al tenant.
CREATE INDEX ix_avance_sitio_tenant_sitio ON avance_sitio (tenant_id, sitio_id);

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V25/V37/V17/V2.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE avance_sitio ENABLE ROW LEVEL SECURITY;
ALTER TABLE avance_sitio FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON avance_sitio
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permiso de CORRECCION de fase (retroceso) — Decision 3.
--   Avanzar la fase usa proyecto:actualizar (V5). Retroceder/corregir la fase de
--   un Sitio (p. ej. deshacer un avance marcado por error) es una operacion
--   sensible que se restringe con proyecto:cambiar_estado, asignado solo a los
--   roles administrativos (admin_empresa, gerente). Estilo de V9/V30: ON CONFLICT
--   DO NOTHING por la clave natural (recurso, operacion).
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('proyecto', 'cambiar_estado')
ON CONFLICT (recurso, operacion) DO NOTHING;

-- admin_empresa (a0000000-...-000000000002) y gerente (a0000000-...-000000000003).
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM (VALUES
        ('a0000000-0000-0000-0000-000000000002'::uuid),
        ('a0000000-0000-0000-0000-000000000003'::uuid)) AS r(id)
CROSS JOIN permiso p
WHERE p.recurso = 'proyecto' AND p.operacion = 'cambiar_estado'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- V46__respaldo_recuperacion.sql
--
-- Respaldo y recuperacion de datos (Tarea 49.2, Req 50). Persiste la BITACORA
-- de ejecuciones de respaldo y restauracion (metadatos, NUNCA el contenido ni
-- material de llaves) y siembra los permisos de plataforma que restringen la
-- operacion de restauracion al rol `super_admin` (Req 50.3).
--
-- ----------------------------------------------------------------------------
-- ALCANCE / RLS (IMPORTANTE)
-- ----------------------------------------------------------------------------
--   El respaldo cubre TODOS los datos de negocio, fiscales y contables del
--   Sistema (Req 50.1): es una operacion de NIVEL PLATAFORMA, no de una Empresa
--   concreta. Por ello `respaldo` es una tabla de plataforma (tenant_id NULL en
--   el contexto del super_admin), siguiendo el MISMO patron que `plan`,
--   `empresa` y `catalogo_modulo` (V1/V22): @Entity plana con su propia columna
--   `version` y marcas de auditoria, SIN heredar de TenantScopedEntity y SIN
--   Row-Level Security. El aislamiento se garantiza por RBAC (permisos de
--   plataforma reservados a super_admin, Req 50.3, 24.3), no por filtro de
--   tenant. Se registra `alcance` textual (p. ej. 'completo') como metadato.
--
-- ----------------------------------------------------------------------------
-- CONVENCIONES
-- ----------------------------------------------------------------------------
--   * PK UUID con gen_random_uuid() (pgcrypto habilitado en V1); la aplicacion
--     tambien puede proveer su propio UUID (fabrica de dominio).
--   * timestamptz (UTC) para todas las marcas temporales (Req 50.4).
--   * `tipo`   -> tipo de operacion registrada: 'respaldo' | 'restauracion'.
--   * `estado` -> ciclo de la operacion: 'en_proceso' | 'completado' | 'fallido'.
--   * `tamano_bytes` -> tamano del artefacto CIFRADO producido (NULL si fallo o
--     si la operacion es una restauracion).
--   * `ubicacion` -> ruta/referencia del artefacto CIFRADO en el almacenamiento
--     restringido (NUNCA contiene el contenido ni la llave). Para restauraciones
--     apunta al artefacto de origen.
--   * `alias_llave` -> alias/version de la Llave_Cifrado usada para cifrar el
--     artefacto (Req 67), para poder descifrar en la restauracion. NUNCA el
--     material de la llave (Req 67.2).
--   * `checksum` -> hash SHA-256 (hex) del artefacto cifrado para verificar
--     integridad en la restauracion.
--   * `actor` -> identificador de quien/que dispara la operacion (super_admin o
--     el proceso programado 'sistema'), Req 50.4.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- respaldo
--   Bitacora de ejecuciones de respaldo/restauracion. Solo metadatos. Tabla de
--   plataforma (sin tenant, sin RLS).
-- ----------------------------------------------------------------------------
CREATE TABLE respaldo (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tipo           VARCHAR(20)  NOT NULL,
    estado         VARCHAR(20)  NOT NULL,
    alcance        VARCHAR(60)  NOT NULL DEFAULT 'completo',
    instante       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ubicacion      VARCHAR(1000),
    alias_llave    VARCHAR(60),
    checksum       VARCHAR(64),
    tamano_bytes   BIGINT,
    actor          VARCHAR(255) NOT NULL,
    detalle_error  VARCHAR(2000),
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    CONSTRAINT pk_respaldo PRIMARY KEY (id),
    CONSTRAINT ck_respaldo_tipo   CHECK (tipo IN ('respaldo', 'restauracion')),
    CONSTRAINT ck_respaldo_estado CHECK (estado IN ('en_proceso', 'completado', 'fallido')),
    CONSTRAINT ck_respaldo_tamano CHECK (tamano_bytes IS NULL OR tamano_bytes >= 0)
);

-- Indice para el listado del historial ordenado por instante descendente.
CREATE INDEX ix_respaldo_instante ON respaldo (instante DESC);

-- ----------------------------------------------------------------------------
-- Permisos de plataforma para respaldo/recuperacion (Req 3.1, 50.3, 27.7). Se
-- anaden al catalogo de permisos atomicos y se enlazan UNICAMENTE al rol
-- super_admin (UUID a0000000-0000-0000-0000-000000000001, definido en V5).
-- `respaldo` es un recurso de NIVEL PLATAFORMA (ver ClasificadorRecursosPlataforma):
-- ningun rol de empresa ni Rol_Personalizado puede incluirlo (Req 27.7, 28.5).
--   * ('respaldo','ejecutar')  -> disparar un respaldo bajo demanda.
--   * ('respaldo','restaurar') -> restaurar desde un respaldo (Req 50.2, 50.3).
--   * ('respaldo','leer')      -> consultar el historial de la bitacora.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('respaldo', 'ejecutar'),
    ('respaldo', 'restaurar'),
    ('respaldo', 'leer')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-000000000001', p.id
FROM permiso p
WHERE p.recurso = 'respaldo' AND p.operacion IN ('ejecutar', 'restaurar', 'leer')
ON CONFLICT DO NOTHING;

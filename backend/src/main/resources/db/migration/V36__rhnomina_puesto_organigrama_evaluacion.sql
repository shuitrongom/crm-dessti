-- ============================================================================
-- V36__rhnomina_puesto_organigrama_evaluacion.sql
--
-- Submodulo de ORGANIZACION DE PERSONAL del modulo rhnomina (Tarea 36.1,
-- Req 61, 40, 23): tablas `puesto`, `asignacion_puesto` y `evaluacion_desempeno`.
-- Estructura el organigrama de la Empresa (Puestos y su jerarquia), la asignacion
-- de Empleados a Puestos y las Evaluacion_Desempeno con escala e historial.
--
-- Replica el PATRON REUTILIZABLE tenant-scoped establecido por V11/V32:
--   * columna tenant_id UUID NOT NULL (Req 23.1),
--   * version BIGINT para concurrencia optimista (Req 49),
--   * marcas de auditoria created_at/updated_at/created_by/updated_by en UTC,
--   * indice por tenant_id (y por relaciones frecuentes),
--   * Row-Level Security (RLS) con la politica tenant_isolation (Capa 2, Req 23),
--     replicando el patron de V17/V32 (ENABLE + FORCE + tenant_isolation).
--
-- ----------------------------------------------------------------------------
-- Requisitos cubiertos (Req 61 -- organizacion de personal)
-- ----------------------------------------------------------------------------
--   - 61.1 Definicion de Puestos y su jerarquia conforma el organigrama de la
--          Empresa: tabla `puesto` con auto-referencia puesto_superior_id.
--   - 61.2 Asignacion de un Empleado a un Puesto: tabla `asignacion_puesto`
--          (FK a empleado de V32 y a puesto), reflejada en el organigrama.
--   - 61.3 Registro de Evaluacion_Desempeno de un Empleado en un periodo,
--          conservando su historial: tabla `evaluacion_desempeno` (no se
--          sobrescribe; cada evaluacion es una fila nueva por periodo).
--   - 61.4 Listado paginado 20/100 de Puestos y de Evaluacion_Desempeno con
--          filtro por Empleado o por periodo (lo aplican las capas web/app).
--   - 61.5 Auditoria al crear/modificar Puesto, asignacion o Evaluacion (la
--          aplica la capa de aplicacion via AuditoriaPort).
--   - 61.6 Integracion con la gestion de Empleado del Req 40: FK empleado_id
--          -> empleado(id) (V32).
--   - 61.7 JERARQUIA ACICLICA: un Puesto no puede ser su propio superior directo
--          ni indirecto. Un CHECK evita el auto-superior directo a nivel de BD
--          (ck_puesto_no_auto_superior); el CICLO MULTI-NODO se valida en el
--          dominio/aplicacion (GrafoOrganigrama, Property 35), porque un CHECK
--          declarativo no puede recorrer la cadena de ancestros de forma portable.
--   - 61.8 La Evaluacion_Desempeno registra una calificacion dentro de una escala
--          definida (1.00..5.00) y conserva los comentarios asociados: columna
--          calificacion NUMERIC(4,2) con CHECK(1..5) y columna comentarios.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. UUID por defecto con gen_random_uuid() (pgcrypto habilitado en V1); la
--      aplicacion tambien provee su propio UUID desde la fabrica del dominio.
--   2. timestamptz para las marcas temporales (UTC), coherente con V1/V11/V32.
--   3. JERARQUIA DE PUESTOS (Req 61.1, 61.7): auto-referencia puesto_superior_id
--      UUID NULL -> puesto(id). Un Puesto sin superior es una raiz del organigrama
--      (forestal: puede haber varias raices). El auto-superior DIRECTO se bloquea
--      con ck_puesto_no_auto_superior (puesto_superior_id <> id). El CICLO
--      INDIRECTO (A->B->C->A) NO se puede detectar con un CHECK declarativo
--      portable, por lo que la validacion aciclica completa vive en el dominio
--      puro GrafoOrganigrama.introduciriaCiclo (recorre ancestros desde el nuevo
--      superior; si alcanza el propio Puesto, hay ciclo -> HTTP 422). Esta funcion
--      es exactamente la que ejercita la Property 35 (Validates Req 61.7).
--   4. ORGANIGRAMA DERIVADO (Req 61.1): NO se materializa. Se deriva de solo
--      lectura de las filas de `puesto` (GrafoOrganigrama.derivar) como un bosque
--      de nodos con sus hijos, sin modificar los datos de origen.
--   5. BORRADO LOGICO del Puesto: columna activo BOOLEAN NOT NULL DEFAULT TRUE,
--      coherente con el patron; la baja conserva el historico.
--   6. ASIGNACION_PUESTO (Req 61.2): vincula empleado_id -> empleado(id) (V32) con
--      puesto_id -> puesto(id). fecha_inicio obligatoria; fecha_fin NULL mientras
--      la asignacion esta vigente; activa BOOLEAN para cerrar una asignacion
--      conservando su historico.
--   7. EVALUACION_DESEMPENO (Req 61.3, 61.8): calificacion NUMERIC(4,2) con
--      CHECK(calificacion >= 1 AND calificacion <= 5) -> ESCALA 1.00..5.00. El
--      periodo se guarda como codigo VARCHAR(7) ('AAAA-MM' u otro codigo de
--      periodo). Cada fila es una evaluacion independiente: el HISTORIAL se
--      conserva sin sobrescribir (varias evaluaciones por empleado/periodo).
--   8. PERMISOS (Req 61, 3.1): V5 sembro puesto:{crear,leer,listar},
--      organigrama:leer y evaluacion_desempeno:{crear,leer} y los enlazo al rol
--      `rh`. Para esta implementacion faltan dos operaciones atomicas:
--        - puesto:actualizar   -> mover un Puesto en la jerarquia (cambiar su
--                                 superior) y asignar Empleados a Puestos.
--        - evaluacion_desempeno:listar -> listado paginado de evaluaciones.
--      Se agregan al catalogo y se enlazan al rol predefinido `rh`
--      (UUID fijo a0000000-0000-0000-0000-00000000000c de V5), replicando lo que
--      V32 hizo para empleado:eliminar. La asignacion de Empleado a Puesto reutiliza
--      el permiso puesto:actualizar (es una modificacion de la estructura del
--      Puesto), evitando crear un recurso RBAC nuevo no contemplado en V5.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- puesto
--   Puesto de la Empresa dentro del organigrama (Req 61.1). tenant-scoped
--   (Req 23). La jerarquia se modela con la auto-referencia puesto_superior_id;
--   un Puesto sin superior es una raiz del organigrama. El ciclo directo se
--   bloquea por CHECK; el ciclo indirecto lo valida el dominio (Req 61.7).
-- ----------------------------------------------------------------------------
CREATE TABLE puesto (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    nombre              VARCHAR(200) NOT NULL,
    descripcion         VARCHAR(500),
    puesto_superior_id  UUID,
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT pk_puesto PRIMARY KEY (id),
    CONSTRAINT fk_puesto_empresa  FOREIGN KEY (tenant_id)          REFERENCES empresa (id),
    CONSTRAINT fk_puesto_superior FOREIGN KEY (puesto_superior_id) REFERENCES puesto (id),
    CONSTRAINT ck_puesto_nombre_no_vacio CHECK (length(btrim(nombre)) >= 1),
    -- Bloquea el auto-superior DIRECTO (A->A). El ciclo INDIRECTO (A->B->..->A)
    -- se valida en el dominio (GrafoOrganigrama, Req 61.7): un CHECK no puede
    -- recorrer la cadena de ancestros de forma portable.
    CONSTRAINT ck_puesto_no_auto_superior
        CHECK (puesto_superior_id IS NULL OR puesto_superior_id <> id)
);

CREATE INDEX ix_puesto_tenant_superior ON puesto (tenant_id, puesto_superior_id);

COMMENT ON CONSTRAINT ck_puesto_no_auto_superior ON puesto IS
    'Evita el auto-superior DIRECTO de un Puesto (Req 61.7). El ciclo INDIRECTO '
    '(A->B->..->A) lo detecta el dominio puro GrafoOrganigrama.introduciriaCiclo, '
    'que recorre los ancestros del nuevo superior y rechaza (HTTP 422) si alcanza '
    'el propio Puesto (Property 35).';

-- ----------------------------------------------------------------------------
-- asignacion_puesto
--   Asignacion de un Empleado a un Puesto (Req 61.2). tenant-scoped (Req 23).
--   Se conserva como historico: una asignacion se cierra con fecha_fin/activa
--   sin eliminar la fila. FK a empleado(id) (V32) y a puesto(id).
-- ----------------------------------------------------------------------------
CREATE TABLE asignacion_puesto (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    empleado_id   UUID        NOT NULL,
    puesto_id     UUID        NOT NULL,
    fecha_inicio  DATE        NOT NULL,
    fecha_fin     DATE,
    activa        BOOLEAN     NOT NULL DEFAULT TRUE,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    CONSTRAINT pk_asignacion_puesto PRIMARY KEY (id),
    CONSTRAINT fk_asignacion_puesto_empresa  FOREIGN KEY (tenant_id)   REFERENCES empresa (id),
    CONSTRAINT fk_asignacion_puesto_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id),
    CONSTRAINT fk_asignacion_puesto_puesto   FOREIGN KEY (puesto_id)   REFERENCES puesto (id),
    CONSTRAINT ck_asignacion_puesto_fechas
        CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);

CREATE INDEX ix_asignacion_puesto_tenant_empleado ON asignacion_puesto (tenant_id, empleado_id);
CREATE INDEX ix_asignacion_puesto_tenant_puesto   ON asignacion_puesto (tenant_id, puesto_id);

-- ----------------------------------------------------------------------------
-- evaluacion_desempeno
--   Evaluacion de desempeno de un Empleado en un periodo (Req 61.3, 61.8).
--   tenant-scoped (Req 23). La calificacion se acota a la ESCALA 1.00..5.00 por
--   CHECK; los comentarios se conservan. El HISTORIAL se conserva: cada fila es
--   una evaluacion independiente (no se sobrescribe por empleado/periodo).
-- ----------------------------------------------------------------------------
CREATE TABLE evaluacion_desempeno (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL,
    empleado_id   UUID          NOT NULL,
    periodo       VARCHAR(7)    NOT NULL,
    calificacion  NUMERIC(4,2)  NOT NULL,
    comentarios   VARCHAR(1000),
    evaluada_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    CONSTRAINT pk_evaluacion_desempeno PRIMARY KEY (id),
    CONSTRAINT fk_evaluacion_desempeno_empresa  FOREIGN KEY (tenant_id)   REFERENCES empresa (id),
    CONSTRAINT fk_evaluacion_desempeno_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id),
    -- ESCALA de la Evaluacion_Desempeno: 1.00..5.00 (Req 61.8).
    CONSTRAINT ck_evaluacion_desempeno_calificacion
        CHECK (calificacion >= 1 AND calificacion <= 5)
);

CREATE INDEX ix_evaluacion_desempeno_tenant_empleado ON evaluacion_desempeno (tenant_id, empleado_id);
CREATE INDEX ix_evaluacion_desempeno_tenant_periodo  ON evaluacion_desempeno (tenant_id, periodo);

COMMENT ON CONSTRAINT ck_evaluacion_desempeno_calificacion ON evaluacion_desempeno IS
    'Escala de calificacion de la Evaluacion_Desempeno: 1.00..5.00 (Req 61.8). '
    'El dominio (EvaluacionDesempeno.registrar) valida el rango antes de persistir '
    '(HTTP 422); el CHECK lo refuerza en la base de datos.';

-- ----------------------------------------------------------------------------
-- Row-Level Security (Capa 2, Req 23) -- patron replicado de V17/V32.
-- ENABLE activa las politicas para roles normales; FORCE las aplica tambien al
-- dueno de la tabla (rol migrador). Sin app.current_tenant fijada, ninguna fila
-- tenant-scoped es visible ni modificable (deny-by-default fail-safe).
-- ----------------------------------------------------------------------------
ALTER TABLE puesto ENABLE ROW LEVEL SECURITY;
ALTER TABLE puesto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON puesto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE asignacion_puesto ENABLE ROW LEVEL SECURITY;
ALTER TABLE asignacion_puesto FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON asignacion_puesto
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE evaluacion_desempeno ENABLE ROW LEVEL SECURITY;
ALTER TABLE evaluacion_desempeno FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON evaluacion_desempeno
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Permisos atomicos faltantes (Req 61, 3.1). V5 sembro puesto:{crear,leer,listar},
-- organigrama:leer y evaluacion_desempeno:{crear,leer} enlazados al rol `rh`.
-- Aqui se agregan:
--   * puesto:actualizar            -> mover un Puesto en la jerarquia (cambiar su
--                                     superior) y asignar Empleados a Puestos.
--   * evaluacion_desempeno:listar  -> listado paginado de Evaluacion_Desempeno.
-- Se enlazan al rol predefinido `rh` (UUID fijo de V5), replicando lo que V32 hizo
-- para empleado:eliminar. El guardado REST con @PreAuthorize se aplica en el
-- controlador.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion)
VALUES ('puesto', 'actualizar'),
       ('evaluacion_desempeno', 'listar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000c', p.id
FROM permiso p
WHERE (p.recurso = 'puesto' AND p.operacion = 'actualizar')
   OR (p.recurso = 'evaluacion_desempeno' AND p.operacion = 'listar')
ON CONFLICT DO NOTHING;

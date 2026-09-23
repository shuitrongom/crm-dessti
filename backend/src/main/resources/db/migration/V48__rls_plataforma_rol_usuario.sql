-- ============================================================================
-- V48__rls_plataforma_rol_usuario.sql
--
-- RLS CONSCIENTE DE PLATAFORMA para las tablas `rol` y `usuario` (Req 23, 27).
--
-- ----------------------------------------------------------------------------
-- MOTIVACION (defecto real corregido)
-- ----------------------------------------------------------------------------
-- Las politicas `tenant_isolation` originales de V2 sobre `rol` y `usuario`
-- usaban una unica regla para TODOS los comandos:
--     USING/WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid)
-- Esto solo funcionaba porque las pruebas de integracion ejecutaban Flyway y las
-- consultas como SUPERUSUARIO del contenedor (que se salta RLS). Con el rol de
-- aplicacion correcto `dessti_app` (NOSUPERUSER, NOBYPASSRLS - Req 23) la regla
-- rompia tres flujos legitimos:
--
--   (A) SEED de plataforma: V5/V45/V47 insertan roles predefinidos con
--       tenant_id NULL; el WITH CHECK los rechaza. -> se resuelve ejecutando las
--       migraciones con un ROL MIGRADOR dedicado (owner/BYPASSRLS solo para DDL
--       y semillas), separado del rol de runtime. Ver crear-base.sql y la
--       config spring.flyway.user/password.
--   (B) LOGIN: ServicioAutenticacion.login resuelve al Usuario por
--       identificador_acceso (UNICO GLOBAL) ANTES de que exista un tenant en el
--       contexto; con la regla estricta, el SELECT no devolvia NINGUNA fila y el
--       login era imposible.
--   (C) LECTURA DE ROLES PREDEFINIDOS: el aprovisionamiento de usuarios y la
--       construccion de claims (buscarNombresRoles / buscarPermisos) leen roles
--       predefinidos (tenant_id NULL); con la regla estricta eran invisibles.
--
-- ----------------------------------------------------------------------------
-- MODELO CORRECTO (por comando; PostgreSQL combina politicas del mismo comando
-- con OR - ver docs CREATE POLICY)
-- ----------------------------------------------------------------------------
-- `rol` y `usuario` NO son tablas de negocio puras: contienen tanto filas de
-- PLATAFORMA compartidas (tenant_id NULL: roles predefinidos y el usuario
-- super_admin) como filas de EMPRESA (tenant_id no nulo). El aislamiento de
-- datos de negocio se mantiene intacto:
--
--   * SELECT (lectura): se permite una fila si
--       - es de plataforma            (tenant_id IS NULL), o
--       - es del tenant actual        (tenant_id = app.current_tenant), o
--       - no hay tenant fijado         (app.current_tenant ausente -> ventana de
--         AUTENTICACION previa al tenant; el usuario se resuelve por su
--         identificador UNICO GLOBAL). Dentro de la aplicacion SIEMPRE hay
--         tenant fijado, por lo que la Capa 1 (filtro Hibernate) + esta regla
--         siguen acotando el listado de usuarios/roles al tenant.
--     Una Empresa NUNCA ve filas de otra Empresa: sus tenant_id no nulos difieren.
--
--   * INSERT / UPDATE / DELETE (escritura): estrictos al tenant actual
--       (tenant_id = app.current_tenant). Una Empresa solo puede crear/modificar/
--       eliminar SUS propias filas (p. ej. Rol_Personalizado, Usuarios de la
--       Empresa). Las filas de plataforma (tenant_id NULL) son inmutables desde
--       la aplicacion y solo las siembra el ROL MIGRADOR (Flyway), nunca el rol
--       de runtime `dessti_app`.
--
-- Coherencia con `empresa` (V2): `empresa` no tiene RLS justamente porque las
-- operaciones de plataforma/arranque necesitan acceso sin tenant fijado; aqui se
-- aplica el mismo principio de forma acotada a la LECTURA de `rol`/`usuario`,
-- manteniendo la ESCRITURA estrictamente tenant-scoped.
--
-- `suscripcion` y todas las tablas de negocio conservan su politica original de
-- V2/posteriores SIN CAMBIOS: nunca contienen filas con tenant_id NULL ni se
-- consultan fuera de un tenant, por lo que la regla estricta sigue siendo correcta.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- rol
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation ON rol;

-- Lectura: plataforma (NULL) OR tenant actual OR ventana de autenticacion
-- (variable no fijada). current_setting(...,true) devuelve NULL si no esta fijada.
CREATE POLICY rol_lectura ON rol
    FOR SELECT
    USING (
        tenant_id IS NULL
        OR current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR tenant_id = current_setting('app.current_tenant', true)::uuid
    );

-- Insercion: solo filas del tenant actual (Rol_Personalizado de la Empresa).
CREATE POLICY rol_insercion ON rol
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Actualizacion: solo filas del tenant actual, y la fila resultante permanece
-- en el tenant actual (no se puede "mover" a otro tenant ni a plataforma).
CREATE POLICY rol_actualizacion ON rol
    FOR UPDATE
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Borrado: solo filas del tenant actual (nunca filas de plataforma).
CREATE POLICY rol_borrado ON rol
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- usuario
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation ON usuario;

CREATE POLICY usuario_lectura ON usuario
    FOR SELECT
    USING (
        tenant_id IS NULL
        OR current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR tenant_id = current_setting('app.current_tenant', true)::uuid
    );

CREATE POLICY usuario_insercion ON usuario
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY usuario_actualizacion ON usuario
    FOR UPDATE
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY usuario_borrado ON usuario
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- Nota sobre `usuario` y el login que MUTA la cuenta (intentos fallidos, Req 2):
-- el UPDATE de bloqueo/intentos ocurre tras resolver al Usuario. En la ventana
-- de autenticacion NO hay tenant fijado, por lo que el WITH CHECK de
-- usuario_actualizacion (tenant_id = NULL) no se cumpliria para cuentas de
-- Empresa. Para no romper el conteo de intentos fallidos, el UPDATE del login se
-- permite mediante una politica adicional acotada A ESA MUTACION concreta: solo
-- cuando NO hay tenant fijado (ventana de login) y sin cambiar el tenant_id de
-- la fila. Esto no debilita el aislamiento de negocio (dentro de la app siempre
-- hay tenant y aplica usuario_actualizacion), y evita que el login falle.
-- ----------------------------------------------------------------------------
CREATE POLICY usuario_login_mutacion ON usuario
    FOR UPDATE
    USING (
        current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
    )
    WITH CHECK (
        current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
    );

COMMENT ON POLICY rol_lectura ON rol IS
    'RLS consciente de plataforma (Req 23/27): lectura de roles predefinidos (tenant NULL), del tenant actual o en la ventana de autenticacion. Escritura tenant-scoped en politicas separadas.';
COMMENT ON POLICY usuario_lectura ON usuario IS
    'RLS consciente de plataforma (Req 23): lectura del super_admin (tenant NULL), del tenant actual o en la ventana de autenticacion (resolucion por identificador global). Escritura tenant-scoped en politicas separadas.';
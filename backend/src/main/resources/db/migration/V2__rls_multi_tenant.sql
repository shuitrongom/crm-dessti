-- ============================================================================
-- V2__rls_multi_tenant.sql
--
-- Row-Level Security (RLS) como SEGUNDA CAPA de defensa del aislamiento
-- multi-empresa (design.md -> Multi-Tenancy -> "Capa 2 - Row-Level Security").
-- Corresponde a la Tarea 4.2. Complementa (no sustituye) el filtro de
-- aplicacion de Hibernate (Capa 1, tarea 4.1): aunque una consulta olvidara el
-- filtro de aplicacion, PostgreSQL igual impide el acceso cruzado entre tenants.
--
-- Requisito cubierto: Req 23 (aislamiento multi-empresa).
--
-- ----------------------------------------------------------------------------
-- MODELO DE LA VARIABLE DE SESION
-- ----------------------------------------------------------------------------
-- La aplicacion fija, por transaccion y sobre la MISMA conexion, la variable de
-- sesion de PostgreSQL:
--
--     SET LOCAL app.current_tenant = '<tenant-del-jwt>';
--
-- Las politicas comparan la columna tenant_id contra esa variable. Se usa
-- current_setting('app.current_tenant', true) con el segundo parametro en TRUE
-- (missing_ok): si la variable NO esta fijada, current_setting devuelve NULL en
-- lugar de lanzar un error.
--
-- COMPORTAMIENTO CUANDO app.current_tenant NO ESTA ESTABLECIDA (fail-safe):
--   La expresion de la politica queda como  tenant_id = NULL::uuid , que en SQL
--   se evalua a NULL (no TRUE) para toda fila. Por tanto, sin la variable fijada
--   NINGUNA fila tenant-scoped es visible ni modificable. Esto es el
--   comportamiento seguro por defecto (deny-by-default): una peticion que no
--   establezca el tenant simplemente no ve datos de negocio, en lugar de verlos
--   todos.
--
-- ----------------------------------------------------------------------------
-- ENABLE vs FORCE ROW LEVEL SECURITY
-- ----------------------------------------------------------------------------
-- ENABLE ROW LEVEL SECURITY activa las politicas para los roles normales, pero
-- el DUENO de la tabla (table owner) las OMITE por defecto. Como las migraciones
-- Flyway suelen ejecutarse con el rol owner/migrador, se agrega FORCE ROW LEVEL
-- SECURITY para que las politicas apliquen TAMBIEN al dueno. Asi el aislamiento
-- se garantiza con independencia de con que rol se conecte la aplicacion.
-- (El superusuario y los roles con BYPASSRLS siguen saltandose RLS; por eso el
-- rol de la aplicacion NO debe ser superusuario ni BYPASSRLS - ver
-- db/roles/app_role.sql, Req 23.)
--
-- ----------------------------------------------------------------------------
-- AMBITO DE LAS TABLAS
-- ----------------------------------------------------------------------------
-- Tenant-scoped (llevan columna tenant_id -> se les aplica RLS):
--     suscripcion, usuario, rol
-- Catalogos de PLATAFORMA (NO tenant-scoped -> NO se les aplica RLS):
--     plan, permiso  (comunes a todas las empresas; administrados por super_admin)
-- Tablas puente N:M (rol_permiso, usuario_rol): NO llevan tenant_id propio; su
--     aislamiento se hereda de las tablas tenant-scoped a las que referencian
--     (rol / usuario). No se les aplica RLS en esta migracion.
--
-- ----------------------------------------------------------------------------
-- LA TABLA empresa (decision documentada)
-- ----------------------------------------------------------------------------
-- empresa ES la entidad tenant: su PK (id) cumple el rol de tenant_id, no tiene
-- columna tenant_id adicional. Ademas es la tabla que el ambito de PLATAFORMA
-- (super_admin) necesita consultar/administrar SIN un tenant fijado (alta,
-- suspension, listado de empresas - Req 24). Por eso NO se habilita RLS sobre
-- empresa: hacerlo con una politica id = app.current_tenant impediria al
-- super_admin listar/crear empresas cuando no hay tenant en el contexto (que es
-- justamente el ambito de plataforma) y crearia un problema de arranque
-- (insertar la primera empresa). El aislamiento de los DATOS DE NEGOCIO se logra
-- porque toda tabla de negocio referencia empresa por tenant_id y ESAS si estan
-- protegidas por RLS. La confidencialidad de la lista de empresas frente a
-- usuarios de negocio se controla en la capa de autorizacion (RBAC, tarea 10:
-- solo super_admin accede a la administracion de empresas) y en la Capa 1
-- (el filtro de Hibernate acota las consultas de negocio al tenant).
--
-- ----------------------------------------------------------------------------
-- AMBITO DE PLATAFORMA (super_admin) - Req 24.3
-- ----------------------------------------------------------------------------
-- Las operaciones de plataforma del super_admin NO fijan app.current_tenant.
-- Consecuencias con este diseno:
--   * Sobre tablas tenant-scoped (suscripcion, usuario, rol): sin variable
--     fijada NO se ven filas (fail-safe). El super_admin NO opera sobre datos de
--     negocio de las empresas (Req 24.3), por lo que esto es correcto. Cuando el
--     super_admin necesite administrar una empresa concreta (p. ej. su
--     suscripcion), el caso de uso de plataforma fijara explicitamente el tenant
--     objetivo antes de la operacion.
--   * Sobre catalogos de plataforma (plan, permiso) y sobre empresa: no tienen
--     RLS, por lo que el super_admin los administra con normalidad sin tenant.
-- No se implementa aqui logica de aplicacion; esta es la estrategia documentada.
--
-- ----------------------------------------------------------------------------
-- PATRON REUTILIZABLE PARA TABLAS DE NEGOCIO FUTURAS
-- ----------------------------------------------------------------------------
-- Cada migracion que cree una nueva tabla tenant-scoped (con columna tenant_id)
-- debe replicar, en su propia migracion, el mismo patron aplicado aqui:
--
--     ALTER TABLE <tabla> ENABLE ROW LEVEL SECURITY;
--     ALTER TABLE <tabla> FORCE  ROW LEVEL SECURITY;
--     CREATE POLICY tenant_isolation ON <tabla>
--         USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
--         WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
--
-- ============================================================================

-- ----------------------------------------------------------------------------
-- suscripcion
-- ----------------------------------------------------------------------------
ALTER TABLE suscripcion ENABLE ROW LEVEL SECURITY;
ALTER TABLE suscripcion FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON suscripcion
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- usuario
--   Nota: el super_admin tiene tenant_id NULL. Con esta politica, el
--   super_admin NO es visible bajo ningun tenant de negocio (tenant_id = NULL
--   nunca iguala a un uuid), lo que es coherente con Req 24.3 (aislamiento del
--   ambito de plataforma respecto a los datos de negocio). El login del
--   super_admin resuelve al usuario por identificador_acceso (unico global)
--   ANTES de fijar tenant, en un contexto/consulta de plataforma.
-- ----------------------------------------------------------------------------
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON usuario
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ----------------------------------------------------------------------------
-- rol
--   Nota: los roles predefinidos de sistema tienen tenant_id NULL. Igual que el
--   super_admin en usuario, no son visibles bajo un tenant de negocio via RLS;
--   su consulta/asignacion se resuelve en el ambito de autorizacion (tarea 10),
--   no por acceso directo tenant-scoped.
-- ----------------------------------------------------------------------------
ALTER TABLE rol ENABLE ROW LEVEL SECURITY;
ALTER TABLE rol FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON rol
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

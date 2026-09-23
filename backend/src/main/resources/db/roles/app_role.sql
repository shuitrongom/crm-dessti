-- ============================================================================
-- app_role.sql  (REFERENCIA - NO se ejecuta automaticamente por Flyway)
--
-- Guion de aprovisionamiento del rol de aplicacion de PostgreSQL para el CRM.
-- Debe ejecutarlo manualmente un administrador de la BD al preparar el entorno.
--
-- Requisito clave (Req 23): el rol de la aplicacion NO debe ser superusuario ni
-- tener BYPASSRLS, para que las politicas de Row-Level Security (Tarea 4.2)
-- SIEMPRE apliquen y se garantice el aislamiento multi-tenant.
--
-- La contrasena NO se versiona (Req 11 - gestion de secretos). Sustituya el
-- placeholder por un secreto provisto por el almacen externo / variable de entorno
-- al momento de ejecutar este guion.
-- ============================================================================

-- Crear el rol de aplicacion SIN privilegios elevados:
--   NOSUPERUSER  -> no es superusuario
--   NOBYPASSRLS  -> las politicas RLS se aplican (NO se saltan)
--   NOCREATEDB / NOCREATEROLE -> minimo privilegio
--   LOGIN        -> puede conectarse
CREATE ROLE crm_app
    LOGIN
    NOSUPERUSER
    NOBYPASSRLS
    NOCREATEDB
    NOCREATEROLE
    PASSWORD 'CAMBIAR_POR_SECRETO_EXTERNO';

-- Permisos sobre la base de datos y el esquema (ajuste el nombre de la BD/esquema):
GRANT CONNECT ON DATABASE crm TO crm_app;
GRANT USAGE ON SCHEMA public TO crm_app;

-- Privilegios sobre tablas existentes y futuras (DML tipico de la aplicacion).
-- El DDL (migraciones Flyway) puede ejecutarse con un rol distinto (owner/migrador).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crm_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO crm_app;

-- NOTA: la habilitacion de RLS y las politicas tenant_isolation se definen en la
-- migracion de la Tarea 4.2, no aqui.

-- ============================================================================
-- crear-base.sql  -  Provisiona la base de datos de la plataforma Dessti con
-- SEPARACION DE PRIVILEGIOS DDL/RUNTIME (Req 23).
--
-- Roles:
--   * dessti_migrator : rol MIGRADOR. Owner del esquema y de las tablas que crea
--                       Flyway; tiene BYPASSRLS para poder sembrar filas de
--                       PLATAFORMA (roles predefinidos con tenant_id NULL) que la
--                       Row-Level Security rechazaria de otro modo. Se usa SOLO
--                       para ejecutar migraciones (spring.flyway.user).
--   * dessti_app      : rol de APLICACION (runtime). NOSUPERUSER, NOBYPASSRLS,
--                       para que la RLS multi-tenant (Capa 2) SIEMPRE aplique a
--                       las consultas de negocio. Solo tiene DML (SELECT/INSERT/
--                       UPDATE/DELETE). Es el spring.datasource del backend.
--
-- El "super_admin" del negocio es un Usuario de la APLICACION (fila en `usuario`
-- con tenant_id NULL), NO un rol de base de datos.
--
-- Uso (una vez, como superusuario postgres):
--   psql -h localhost -p 5433 -U postgres -f local-dev\crear-base.sql
--
-- Idempotente: puede re-ejecutarse sin romper.
-- ============================================================================

-- 1) Rol MIGRADOR (idempotente). BYPASSRLS solo para migrar/sembrar plataforma.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dessti_migrator') THEN
        CREATE ROLE dessti_migrator WITH LOGIN PASSWORD 'Pa55worD'
            NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS;
    ELSE
        ALTER ROLE dessti_migrator WITH LOGIN PASSWORD 'Pa55worD'
            NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS;
    END IF;
END
$$;

-- 2) Rol de APLICACION (idempotente). NO se salta la RLS.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dessti_app') THEN
        CREATE ROLE dessti_app WITH LOGIN PASSWORD 'Pa55worD'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE dessti_app WITH LOGIN PASSWORD 'Pa55worD'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END
$$;

-- 3) Base de datos propiedad del MIGRADOR (idempotente via \gexec).
--    El migrador es owner para poder crear/alterar el esquema y las tablas.
SELECT 'CREATE DATABASE dessti_plataforma OWNER dessti_migrator'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'dessti_plataforma')\gexec

-- 4) Permisos dentro de la base (ejecutar conectado a dessti_plataforma).
\connect dessti_plataforma

-- El esquema public pertenece al migrador (owner del DDL).
ALTER SCHEMA public OWNER TO dessti_migrator;

-- La aplicacion puede conectarse y usar el esquema, pero NO crear objetos.
GRANT CONNECT ON DATABASE dessti_plataforma TO dessti_app;
GRANT USAGE  ON SCHEMA public TO dessti_app;

-- DML de runtime sobre las tablas y secuencias EXISTENTES (por si se re-ejecuta
-- tras haber migrado) y sobre las FUTURAS que cree el migrador (ALTER DEFAULT
-- PRIVILEGES ejecutado como el rol que creara los objetos: dessti_migrator).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO dessti_app;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA public TO dessti_app;

ALTER DEFAULT PRIVILEGES FOR ROLE dessti_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dessti_app;
ALTER DEFAULT PRIVILEGES FOR ROLE dessti_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO dessti_app;

-- NOTA: la habilitacion de RLS y las politicas tenant_isolation / conscientes de
-- plataforma se definen en las migraciones Flyway (V2 y V48), no aqui.
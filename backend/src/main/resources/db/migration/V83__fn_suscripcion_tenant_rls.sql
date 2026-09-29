-- ============================================================================
-- V83 — Funcion de resolucion de tenant de una Suscripcion (para RLS)
-- ----------------------------------------------------------------------------
-- CONTEXTO / PROBLEMA
--   La tabla `suscripcion` tiene Row-Level Security con FORCE y politica
--   `tenant_isolation` (V2, endurecida en V53): es deny-by-default. El
--   super_admin gestiona las Suscripciones desde el ambito de PLATAFORMA, donde
--   NO hay `app.current_tenant` fijado; por diseno fail-safe, ninguna fila de
--   `suscripcion` es visible en ese contexto.
--
--   Las operaciones de plataforma que operan sobre UNA empresa concreta ya
--   resuelven esto fijando el tenant destino con `applyTenant(tenantId)` ANTES
--   de leer/escribir (p. ej. `listarPorEmpresa`, `fijarMonedaFacturacion`,
--   `actualizarModulosEmpresa`, y el enriquecimiento del EmpresaDto). Pero las
--   acciones que reciben SOLO el `suscripcionId` (convertir a plan, activar,
--   suspender, cancelar, actualizar vigencia, extender prueba, activar
--   facturacion, consultar) NO conocen el tenant y, al hacer `findById`, chocan
--   con la RLS y devuelven un FALSO 404 ("No se encontro la Suscripcion
--   solicitada"), aunque la fila exista fisicamente.
--
-- SOLUCION
--   Una funcion SECURITY DEFINER, propiedad del rol MIGRADOR (owner del esquema,
--   con BYPASSRLS solo para DDL/migracion), que devuelve UNICAMENTE el
--   `tenant_id` de una Suscripcion por su `id`. Al ejecutarse con los privilegios
--   del *definer* (no del invocador), evade la RLS de forma acotada: expone solo
--   el tenant (un UUID), nunca el resto de la fila. El servicio la usa para
--   resolver el tenant y luego `applyTenant(tenant_id)` ANTES de leer la
--   Suscripcion ya con RLS activa (misma fila visible, sin abrir nada mas).
--
--   Es el mismo espiritu que los catalogos de plataforma sin RLS (`plan`,
--   `paquete_suscripcion`): dar visibilidad minima y controlada al super_admin
--   sin desactivar el aislamiento de negocio.
--
-- SEGURIDAD
--   - La funcion NO recibe texto arbitrario: su unico parametro es un UUID.
--   - Devuelve solo `tenant_id` (UUID); no filtra ni expone datos de negocio.
--   - `search_path` fijado a `public` para evitar secuestro de resolucion de
--     nombres en funciones SECURITY DEFINER (hardening estandar).
--   - EXECUTE se concede al rol de aplicacion; el resto de columnas de
--     `suscripcion` siguen protegidas por la RLS normal.
-- ============================================================================

CREATE OR REPLACE FUNCTION suscripcion_tenant(p_suscripcion_id UUID)
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT tenant_id FROM suscripcion WHERE id = p_suscripcion_id
$$;

COMMENT ON FUNCTION suscripcion_tenant(UUID) IS
    'Devuelve el tenant_id de una Suscripcion por su id evadiendo la RLS de '
    'suscripcion (SECURITY DEFINER, owner=migrador). Uso exclusivo de '
    'plataforma: resolver el tenant para fijar app.current_tenant antes de leer '
    'la fila con RLS activa. No expone ninguna otra columna.';

-- El rol de aplicacion (runtime, NOBYPASSRLS) debe poder invocarla. En pruebas
-- (Testcontainers) migrador y runtime son el mismo superusuario, por lo que el
-- GRANT es idempotente y no molesta. En produccion, el rol de app se llama
-- `dessti_app` (ver application.yml); el guion de aprovisionamiento usa el
-- nombre real del entorno. Se protege con un bloque que ignora el rol ausente
-- para que la migracion sea robusta si el rol aun no existe al migrar.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dessti_app') THEN
        GRANT EXECUTE ON FUNCTION suscripcion_tenant(UUID) TO dessti_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'crm_app') THEN
        GRANT EXECUTE ON FUNCTION suscripcion_tenant(UUID) TO crm_app;
    END IF;
END $$;

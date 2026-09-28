-- ============================================================================
-- V77__usuario_cliente_portal.sql
--
-- Vinculo Usuario -> Cliente para el Portal del Cliente (Req 45). Habilita que un
-- Usuario con rol externo `cliente_portal` (V45) quede asociado a UN Cliente
-- (comercial.cliente, V11), de modo que el Portal pueda acotar por ese Cliente
-- sin aceptar jamas el clienteId de la peticion.
--
-- El acotamiento en tiempo de ejecucion se hace emitiendo una GrantedAuthority
-- `cliente_id:<uuid>` en el JWT (leida por ClientePortalActualDesdeAuthenticationAdapter,
-- ya implementado). Esta migracion aporta la fuente de verdad persistente de ese
-- vinculo: la columna usuario.cliente_id.
--
-- DECISIONES DE DISENO
--   1. COLUMNA NULLABLE en `usuario`: la inmensa mayoria de Usuarios (staff de la
--      Empresa, super_admin) NO tienen Cliente asociado; solo los de portal lo
--      llevan. Por eso es NULL por defecto (no rompe ningun Usuario existente).
--   2. FK a `cliente(id)` SIN cascada: el Cliente es historico; si se intentara
--      borrar un Cliente con usuarios de portal, la FK lo impide (RESTRICT). El
--      Cliente se da de baja logica (activo=false), no se borra.
--   3. COHERENCIA DE TENANT: `usuario.tenant_id` y `cliente.tenant_id` deben
--      coincidir; la validacion la aplica la capa de aplicacion al asignar (el
--      alta deriva el tenant del contexto, nunca de la peticion, Req 23.4). No se
--      fuerza por FK compuesta para no alterar el esquema base de `usuario` (V1),
--      cuyo tenant_id es nullable (super_admin).
--   4. INDICE por cliente_id para resolver "usuarios de un Cliente" y para la FK.
--   5. Sin cambios de permisos: el Portal autoriza por hasRole('cliente_portal')
--      + acotamiento por cliente_id; no hay recursos RBAC nuevos.
-- ============================================================================

ALTER TABLE usuario
    ADD COLUMN cliente_id UUID NULL;

ALTER TABLE usuario
    ADD CONSTRAINT fk_usuario_cliente_portal
        FOREIGN KEY (cliente_id) REFERENCES cliente (id);

CREATE INDEX ix_usuario_cliente_id ON usuario (cliente_id);

-- ============================================================================
-- V57__usuario_nombre_visible.sql
--
-- NOMBRE PARA MOSTRAR (nombre visible) del Usuario (Req 4).
--
-- La migracion V1 creo la tabla `usuario` con su identidad de acceso
-- (`identificador_acceso`, que es el correo/login UNICO GLOBAL). Esta migracion
-- agrega un nombre PARA MOSTRAR, independiente del identificador de acceso, para
-- que la interfaz de administracion de la Empresa pueda presentar a las personas
-- por su nombre y no por su correo ni su UUID.
--
-- La columna es OPCIONAL (NULLABLE) por dos motivos:
--   1) Es un dato descriptivo, no obligatorio para autenticar ni autorizar.
--   2) Ya existen filas en `usuario`: agregarla como NOT NULL romperia la
--      migracion. Se agrega NULLABLE de forma NO destructiva (solo suma una
--      columna vacia; las filas existentes conservan NULL).
--
-- ALCANCE / SEGURIDAD:
--   * NO se toca RLS. La tabla `usuario` tiene `tenant_id` NULLABLE (el
--     super_admin es de plataforma) y su aislamiento por Empresa lo aplica la
--     capa de aplicacion (ServicioUsuarios), no un filtro global de Hibernate
--     ni una politica RLS que dependa de esta columna. Esta columna jamas
--     participa en el aislamiento por tenant (Req 8.1).
--   * NO es un secreto (Req 11.3): es un dato puramente descriptivo.
--
-- Requisitos cubiertos:
--   - Req 4 : nombre para mostrar del Usuario, separado del identificador de acceso.
--   - Migracion versionada y NO destructiva (solo agrega una columna nullable).
-- ============================================================================

-- IF NOT EXISTS para tolerar re-aplicaciones manuales sin dejar estado parcial
-- (Flyway envuelve la migracion en una sola transaccion). VARCHAR(200) alineado
-- con la cota de dominio (Usuario.LONGITUD_MAXIMA_NOMBRE_VISIBLE = 200).
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS nombre_visible VARCHAR(200);

COMMENT ON COLUMN usuario.nombre_visible IS
    'Nombre para mostrar del Usuario (separado del identificador de acceso/login); dato descriptivo opcional (Req 4).';

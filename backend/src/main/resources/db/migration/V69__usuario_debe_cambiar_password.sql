-- ============================================================================
-- V69__usuario_debe_cambiar_password.sql
--
-- Bugfix (forzar cambio de contrasena temporal): cuando el super_admin crea una
-- Empresa con contrasena generada por el Sistema, o restablece la contrasena del
-- administrador, la cuenta debe OBLIGAR al Usuario a cambiar su contrasena en el
-- primer inicio de sesion. Se agrega la bandera `debe_cambiar_password`.
--
-- Semantica:
--   * true  -> el Usuario debe cambiar su contrasena antes de operar; el login
--              lo informa y el frontend fuerza el cambio.
--   * false -> operacion normal (valor por defecto para las cuentas existentes).
--
-- La bandera se limpia (false) cuando el Usuario cambia su propia contrasena
-- (PUT /auth/perfil/password).
-- ============================================================================

ALTER TABLE usuario
    ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT false;
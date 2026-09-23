-- ============================================================================
-- V68__login_identificador_case_insensitive.sql
--
-- Bugfix (login case-insensitive): el identificador de acceso debe permitir el
-- inicio de sesion sin importar mayusculas/minusculas. La aplicacion ahora
-- almacena y resuelve el identificador en minusculas. Esta migracion normaliza
-- a minusculas los identificadores existentes para que las cuentas creadas antes
-- del cambio tambien puedan iniciar sesion con cualquier combinacion de
-- mayusculas/minusculas.
--
-- La contrasena NO se ve afectada (sigue siendo sensible a mayusculas).
--
-- Nota de unicidad: el indice unico global uq_usuario_identificador_acceso (V1)
-- es sobre el valor tal cual. Si existieran dos cuentas que difieran solo en
-- mayusculas/minusculas, este UPDATE fallaria por conflicto de unicidad; en la
-- practica no se esperan tales colisiones (los identificadores son correos o
-- usuarios unicos). De existir, deben resolverse manualmente antes de migrar.
-- ============================================================================

UPDATE usuario
SET identificador_acceso = LOWER(identificador_acceso)
WHERE identificador_acceso <> LOWER(identificador_acceso);
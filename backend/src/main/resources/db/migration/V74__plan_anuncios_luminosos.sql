-- ============================================================================
-- V74__plan_anuncios_luminosos.sql
--
-- Siembra el Plan "Anuncios Luminosos" en el catalogo de Planes de la PLATAFORMA.
--
-- POLITICA (decision de negocio): el Plan de Anuncios Luminosos —el giro completo
-- de referencia— debe existir SIEMPRE en cualquier instalacion, para no perderlo
-- al provisionar una maquina/base nueva (antes se capturaba a mano y se perdia).
-- Se siembra por migracion versionada, con UUID determinista e idempotente.
--
-- Contenido del Plan:
--   * nombre               : 'Anuncios Luminosos' (UNICO, uq_plan_nombre en V1).
--   * max_usuarios          : 30 (VALOR POR DEFECTO AJUSTABLE por el super_admin).
--                             Es el numero de Usuarios con acceso al sistema
--                             ADEMAS del administrador; el super_admin lo ajusta
--                             al asignar la Suscripcion a cada Empresa.
--   * modulos_habilitados   : los 15 modulos COMUNES del catalogo (V22), que son
--                             los bloques base transversales. Las claves coinciden
--                             EXACTAMENTE con catalogo_modulo.clave (normalizadas
--                             en minusculas/kebab), para que el gating por Plan
--                             (Req 25.4) y la facturacion de modulos emparejen.
--
-- IDEMPOTENCIA: ON CONFLICT (nombre) DO NOTHING (patron V50/V22/V5). Si el Plan ya
-- existe (por captura previa o re-ejecucion), no se duplica ni se sobreescribe.
--
-- NOTA: los PRECIOS de modulo (precio_modulo) NO se siembran aqui: son decision
-- comercial que captura el super_admin. Las monedas MXN/USD/EUR ya las siembra V22.
-- ============================================================================

-- Columnas de `plan` respetadas (esquema real): nombre (V1), max_usuarios (V1),
-- modulos_habilitados jsonb (V1), giro_id/moneda_codigo/precios_modulos (V55),
-- duracion_dias NOT NULL con CHECK > 365 (V64). Se amarra al Giro
-- `anuncios-luminosos` (V50) por subconsulta a su clave natural, y a la moneda MXN
-- (V22). duracion_dias = 730 (2 anios, plan de largo plazo; CHECK exige > 365).
-- precios_modulos se deja en '{}' (los precios los captura el super_admin).
INSERT INTO plan (id, nombre, max_usuarios, duracion_dias, giro_id, moneda_codigo,
                  modulos_habilitados, precios_modulos)
VALUES
    ('b1a00000-0000-0000-0000-000000000001',
     'Anuncios Luminosos',
     30,
     730,
     (SELECT id FROM giro WHERE clave = 'anuncios-luminosos'),
     'MXN',
     '["comercial","redes-sociales","operacion","inventario-avanzado","mantenimiento","compras","facturacion","contabilidad","tesoreria","activos-fijos","rh-nomina","portal-cliente","estrategia","presupuestos","reportes-bi"]'::jsonb,
     '{}'::jsonb)
ON CONFLICT (nombre) DO NOTHING;

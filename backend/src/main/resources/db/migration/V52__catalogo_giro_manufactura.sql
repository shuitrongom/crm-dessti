-- ============================================================================
-- V52__catalogo_giro_manufactura.sql
--
-- Siembra del Giro `manufactura` en el Catalogo_Giros de la PLATAFORMA MULTIGIRO
-- (Tarea 12.1, Req 12.1, 12.2, 12.3).
--
-- El Vertical_Manufactura (paquete `com.dessti.crm.vertical.manufactura`) es la
-- PRUEBA DEL MODELO enchufable: un segundo Giro, hermano del Vertical_Anuncios,
-- que encaja en el mismo Contrato_Vertical sin tocar anuncios ni el Nucleo. Para
-- que el giro `manufactura` sea ASIGNABLE a una Empresa (alta/cambio de giro,
-- V51 `empresa.giro_id`) y para que el IT de coexistencia entre dos verticales
-- (tarea 12.3) pueda dar de alta una Empresa de manufactura, el giro debe existir
-- en el catalogo. Esta migracion lo siembra como Giro activo, replicando
-- EXACTAMENTE el patron con que V50 sembro `anuncios-luminosos`.
--
-- DECISION (trade-off): se siembra `manufactura` por migracion versionada, en
-- lugar de dejarlo solo como bean de demostracion. Aunque manufactura es un
-- esqueleto de prueba del modelo, sembrarlo en el catalogo de plataforma es lo
-- correcto para que sea un giro REALMENTE coexistente y asignable (Req 12.2/12.4),
-- de forma reproducible y verificable en integracion; el coste es una entrada de
-- catalogo adicional, que el Super_Administrador puede desactivar (Req 1.5).
--
-- Alcance de esta migracion (analogo a V50, seccion 2):
--   1) siembra el Giro `manufactura` como activo (Req 11.1 / patron V50);
--   NO crea tablas de negocio de manufactura: el vertical es un ESQUELETO cuyas
--   entidades (Bom, OrdenProduccion) son dominio puro sin @Entity ni persistencia.
--   NO siembra recursos RBAC de manufactura (bom/orden_produccion/
--   planeacion_produccion): son recursos de DEMOSTRACION declarados por el
--   ContratoVertical (ManufacturaVertical#recursos()); no se persisten al ser un
--   esqueleto de prueba del modelo, evitando ademas duplicacion (Req 13.3).
--
-- CONVENCIONES (identicas a V50):
--   * DATO DE PLATAFORMA, SIN RLS (Req 8.4): `giro` es catalogo de plataforma.
--   * CLAVE CANONICA NORMALIZADA (Req 1.2): `manufactura` (minusculas, kebab).
--   * UUID FIJO/DETERMINISTA para enlace reproducible en pruebas/backfill; el
--     enlace real se hace por la clave natural `clave` = 'manufactura'.
--   * IDEMPOTENCIA: ON CONFLICT (clave) DO NOTHING (patron V50/V5/V44).
--
-- Requisitos cubiertos:
--   - Req 12.1 : el segundo Giro `manufactura` existe y es asignable, encajando
--                en el Contrato_Vertical (ManufacturaVertical).
--   - Req 12.2 : se registra el Giro `manufactura` SIN modificar el
--                Vertical_Anuncios ni el Nucleo (solo se anade catalogo).
--   - Req 12.4 : habilita la coexistencia real de anuncios y manufactura para el
--                aislamiento por Gating_Por_Giro entre ambos verticales.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- SIEMBRA DEL GIRO `manufactura` (activo)
--   Segundo Giro de la plataforma. UUID fijo/determinista para enlace
--   reproducible; el enlace real se hace por la clave natural. Idempotente por
--   ON CONFLICT (clave). Mismo patron que V50 seccion 2.
-- ----------------------------------------------------------------------------
INSERT INTO giro (id, clave, nombre_visible, descripcion, activo) VALUES
    ('c1a00000-0000-0000-0000-000000000002',
     'manufactura',
     'Manufactura',
     'Vertical de manufactura: listas de materiales (BOM), ordenes de produccion y planeacion. Segundo giro enchufable de la plataforma multigiro, usado como prueba del modelo de verticales.',
     TRUE)
ON CONFLICT (clave) DO NOTHING;

-- ============================================================================
-- V63__social_canales_facebook_tiktok.sql
--
-- Amplia el dominio de Canal_Social de tres a cinco redes (spec
-- redes-sociales-conexiones, Req 1): a los canales de Meta ya soportados
-- (WhatsApp, Facebook Messenger e Instagram) se suman FACEBOOK (paginas de
-- Facebook) y TIKTOK (que NO pertenece a Meta y se integra por su API propia).
-- El enum de dominio CanalSocial gana FACEBOOK('facebook') y TIKTOK('tiktok');
-- esta migracion RELAJA, de forma NO DESTRUCTIVA, los CHECK que acotan la
-- columna `canal` en las tablas del modulo social establecidas en V41 y V43
-- para que admitan los cinco valores.
--
-- ----------------------------------------------------------------------------
-- DECISIONES DE DISENO
-- ----------------------------------------------------------------------------
--   1. NO DESTRUCTIVA (Req 1.5): solo se AMPLIA el conjunto permitido de
--      `canal`. Ninguna fila existente ('whatsapp'/'messenger'/'instagram')
--      viola el nuevo CHECK, por lo que las cuentas/conversaciones/plantillas/
--      consentimientos/publicaciones/campanas previos se preservan intactos.
--   2. NOMBRES DE CONSTRAINT CONSERVADOS: cada CHECK se recrea con su MISMO
--      nombre (DROP + ADD), de modo que el esquema resultante es indistinguible
--      del original salvo por el conjunto de valores admitidos. Esto mantiene la
--      idempotencia de los nombres y la trazabilidad con V41/V43.
--   3. CONJUNTO UNIFORME DE CINCO CANALES:
--      ('whatsapp','facebook','instagram','messenger','tiktok'). Se aplica al
--      dominio completo de canal del modulo social; las restricciones de
--      subconjunto por tipo de entidad (si aplican) las gobierna el dominio, no
--      la BD, coherente con las DECISIONES de V41/V43.
--   4. NULABILIDAD PRESERVADA: campana_publicitaria.canal es OPCIONAL; su CHECK
--      conserva la clausula `canal IS NULL OR canal IN (...)`.
--   5. FUERA DE ALCANCE: notificacion.canal (V42) NO se toca en esta migracion.
--
-- Tablas afectadas:
--   V41: cuenta_canal_social, conversacion, plantilla_mensaje, consentimiento_canal
--   V43: publicacion_social, campana_publicitaria (canal opcional)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- V41 - cuenta_canal_social.canal
-- ----------------------------------------------------------------------------
ALTER TABLE cuenta_canal_social DROP CONSTRAINT ck_cuenta_canal_social_canal;
ALTER TABLE cuenta_canal_social ADD CONSTRAINT ck_cuenta_canal_social_canal CHECK (
    canal IN ('whatsapp', 'facebook', 'instagram', 'messenger', 'tiktok'));

-- ----------------------------------------------------------------------------
-- V41 - conversacion.canal
-- ----------------------------------------------------------------------------
ALTER TABLE conversacion DROP CONSTRAINT ck_conversacion_canal;
ALTER TABLE conversacion ADD CONSTRAINT ck_conversacion_canal CHECK (
    canal IN ('whatsapp', 'facebook', 'instagram', 'messenger', 'tiktok'));

-- ----------------------------------------------------------------------------
-- V41 - plantilla_mensaje.canal
-- ----------------------------------------------------------------------------
ALTER TABLE plantilla_mensaje DROP CONSTRAINT ck_plantilla_mensaje_canal;
ALTER TABLE plantilla_mensaje ADD CONSTRAINT ck_plantilla_mensaje_canal CHECK (
    canal IN ('whatsapp', 'facebook', 'instagram', 'messenger', 'tiktok'));

-- ----------------------------------------------------------------------------
-- V41 - consentimiento_canal.canal
-- ----------------------------------------------------------------------------
ALTER TABLE consentimiento_canal DROP CONSTRAINT ck_consentimiento_canal_canal;
ALTER TABLE consentimiento_canal ADD CONSTRAINT ck_consentimiento_canal_canal CHECK (
    canal IN ('whatsapp', 'facebook', 'instagram', 'messenger', 'tiktok'));

-- ----------------------------------------------------------------------------
-- V43 - publicacion_social.canal
-- ----------------------------------------------------------------------------
ALTER TABLE publicacion_social DROP CONSTRAINT ck_publicacion_social_canal;
ALTER TABLE publicacion_social ADD CONSTRAINT ck_publicacion_social_canal CHECK (
    canal IN ('whatsapp', 'facebook', 'instagram', 'messenger', 'tiktok'));

-- ----------------------------------------------------------------------------
-- V43 - campana_publicitaria.canal (OPCIONAL: preserva la nulabilidad)
-- ----------------------------------------------------------------------------
ALTER TABLE campana_publicitaria DROP CONSTRAINT ck_campana_publicitaria_canal;
ALTER TABLE campana_publicitaria ADD CONSTRAINT ck_campana_publicitaria_canal CHECK (
    canal IS NULL OR canal IN ('whatsapp', 'facebook', 'instagram', 'messenger', 'tiktok'));

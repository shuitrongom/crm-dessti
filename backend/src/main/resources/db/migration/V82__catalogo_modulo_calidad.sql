-- ============================================================================
-- V82__catalogo_modulo_calidad.sql
--
-- Registra el modulo CALIDAD (SGC ISO 9001) en el catalogo de modulos vendibles
-- (`catalogo_modulo`, sembrado en V22). El modulo de Calidad ya existe completo
-- en el backend (migracion V47 + paquete com.dessti.crm.calidad: quejas de
-- cliente, no conformidades, acciones correctivas, riesgos, oportunidades de
-- calidad, cambios del SGC, contexto de la organizacion, indicadores y
-- trazabilidad ISO), pero su clave nunca se dio de alta en el catalogo de
-- monetizacion, por lo que NO aparecia en el selector "Modulos y precios" al
-- crear/editar un Plan. Esta migracion lo expone como modulo comercializable.
--
-- ----------------------------------------------------------------------------
-- POR QUE ES SEGURO (NO ROMPE NADA):
-- ----------------------------------------------------------------------------
--   1. Es PURAMENTE ADITIVA: solo INSERTA una fila en `catalogo_modulo` con
--      ON CONFLICT (clave) DO NOTHING, igual que V22. No altera columnas, ni
--      indices, ni RLS, ni datos existentes. Re-ejecutable sin efectos.
--   2. NO cambia el control de acceso vigente. Los endpoints de Calidad se
--      protegen SOLO con permisos RBAC (@autorizador.tiene('calidad','leer'),
--      'no_conformidad', 'accion_correctiva', 'queja_cliente', 'riesgo', ...),
--      NO con @autorizador.moduloHabilitado('calidad'). Del mismo modo, la
--      navegacion del frontend muestra la seccion "Calidad (ISO 9001)" por
--      PERMISO, sin depender de `modulo`. Por tanto, registrar la clave en el
--      catalogo NO habilita ni deshabilita nada que hoy funcione: solo hace que
--      el modulo pueda SELECCIONARSE y PRECIARSE en un Plan (monetizacion).
--   3. La clave canonica 'calidad' coincide EXACTAMENTE con la usada por los
--      permisos y por el resto del sistema, evitando divergencias.
--
-- Requisitos: Req 22 (monetizacion por modulos), Req 47 (Calidad ISO 9001).
-- ============================================================================

INSERT INTO catalogo_modulo (clave, nombre, descripcion) VALUES
    ('calidad', 'Calidad (ISO 9001)',
     'Sistema de Gestion de Calidad ISO 9001: quejas de cliente, no conformidades, '
     || 'acciones correctivas, matriz de riesgos, oportunidades de mejora, cambios del '
     || 'SGC, contexto de la organizacion e indicadores con trazabilidad de clausulas.')
ON CONFLICT (clave) DO NOTHING;

-- ============================================================================
-- V7__ventana_intentos_fallidos.sql
--
-- Ventana deslizante de 15 minutos para el bloqueo por intentos fallidos
-- (Req 2.1). Agrega a la tabla `usuario` (creada en V1) la columna que ancla la
-- ventana en el instante del PRIMER fallo de la racha en curso, de modo que el
-- bloqueo se calcule de forma LITERAL: cinco fallos consecutivos DENTRO de una
-- ventana de 15 minutos bloquean la cuenta 15 minutos; un fallo posterior a la
-- ventana reinicia la racha (nuevo conteo desde 1).
--
-- Requisito cubierto: Req 2.1 (bloqueo por 5 intentos fallidos DENTRO de una
--   ventana de 15 minutos y reinicio del contador a 0 al finalizar el bloqueo).
--
-- ----------------------------------------------------------------------------
-- MODELO ELEGIDO: anclar la ventana en el primer fallo de la racha
-- ----------------------------------------------------------------------------
-- Hasta V1 la tabla `usuario` persistia `intentos_fallidos` y `bloqueado_hasta`,
-- pero NO el instante en que arranco la racha de fallos, por lo que la "ventana
-- de 15 minutos" del Req 2.1 no podia aplicarse literalmente (un fallo muy
-- separado en el tiempo seguia sumando al contador). Se agrega una unica columna
-- NULLABLE `primer_intento_fallido TIMESTAMPTZ` (UTC) que registra el instante
-- del primer fallo de la racha vigente:
--   * NULL cuando no hay racha en curso (cuenta limpia, tras exito o tras
--     expirar/limpiarse el bloqueo).
--   * Con valor, la ventana vive en [primer_intento_fallido,
--     primer_intento_fallido + 15 min]; un fallo posterior a ese limite reinicia
--     la racha y se vuelve a anclar la ventana en ese nuevo primer fallo.
-- Toda la logica temporal reside en la entidad de dominio UsuarioAuth y opera
-- con un Clock inyectado (UTC), nunca con Instant.now(), para pruebas
-- deterministas.
--
-- ----------------------------------------------------------------------------
-- AMBITO MULTI-TENANT Y RLS
-- ----------------------------------------------------------------------------
-- La columna se agrega sobre `usuario`, que ya tiene su politica RLS definida en
-- V2. Un simple ADD COLUMN nullable no altera esa politica ni requiere tocarla.
-- La columna es de estado interno de seguridad (no se expone en DTOs) y, como el
-- resto de `usuario`, no es tenant-scoped a efectos del flujo de autenticacion.
-- ============================================================================

ALTER TABLE usuario
    ADD COLUMN primer_intento_fallido TIMESTAMPTZ;

COMMENT ON COLUMN usuario.primer_intento_fallido IS
    'Instante (UTC) del primer fallo de autenticacion de la racha en curso; '
    'ancla la ventana deslizante de 15 minutos del bloqueo por intentos fallidos '
    '(Req 2.1). NULL cuando no hay racha activa (cuenta limpia, tras un login '
    'exitoso o tras expirar/limpiarse el bloqueo).';

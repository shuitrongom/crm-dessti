package com.dessti.crm.comercial.canalventa.application;

/**
 * Comando de actualizacion de un {@link com.dessti.crm.comercial.canalventa.domain.CanalVenta}
 * (Req 63.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * @param nombre      nuevo nombre del canal; obligatorio (1..100).
 * @param descripcion nueva descripcion; opcional.
 */
public record ActualizarCanalVentaCommand(String nombre, String descripcion) {
}

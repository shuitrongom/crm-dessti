package com.dessti.crm.comercial.canalventa.application;

/**
 * Comando de creacion de un {@link com.dessti.crm.comercial.canalventa.domain.CanalVenta}
 * (Req 63.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param nombre      nombre del canal; obligatorio (1..100).
 * @param descripcion descripcion; opcional.
 */
public record CrearCanalVentaCommand(String nombre, String descripcion) {
}

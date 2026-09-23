package com.dessti.crm.comercial.oportunidad.adapter.in.rest;

import java.util.UUID;

/**
 * Cuerpo de la peticion para clasificar una Oportunidad por canal de venta
 * (Req 63.1). DTO de entrada del contrato REST. El {@code canalVentaId} es
 * <strong>opcional</strong>: un valor nulo limpia la clasificacion (desclasifica
 * la Oportunidad). La existencia del canal en el tenant la verifica la capa de
 * aplicacion (404 si no existe); la asignacion se audita (Req 63.3).
 *
 * @param canalVentaId identificador del Canal_Venta; {@code null} para limpiar
 *                     la clasificacion.
 */
public record AsignarCanalVentaRequest(UUID canalVentaId) {
}

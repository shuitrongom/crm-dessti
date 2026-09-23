package com.dessti.crm.comercial.canalventa.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar un Canal_Venta (Req 63.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.comercial.canalventa.application.ActualizarCanalVentaCommand}
 * (Req 12.2).
 *
 * @param nombre      nuevo nombre del canal; obligatorio (1..100).
 * @param descripcion nueva descripcion; opcional (hasta 500).
 */
public record ActualizarCanalVentaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 500) String descripcion) {
}

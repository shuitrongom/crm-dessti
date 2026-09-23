package com.dessti.crm.comercial.canalventa.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Canal_Venta (Req 63.1). DTO de
 * entrada del contrato REST, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.comercial.canalventa.application.CrearCanalVentaCommand}
 * (Req 12.2). El {@code tenant_id} se deriva del contexto (Req 23.4).
 *
 * <p>La validacion de campo se limita a presencia y longitud (Bean Validation
 * -&gt; 400); las reglas de dominio (422) las aplica la capa de aplicacion.</p>
 *
 * @param nombre      nombre del canal; obligatorio (1..100).
 * @param descripcion descripcion; opcional (hasta 500).
 */
public record CrearCanalVentaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 500) String descripcion) {
}

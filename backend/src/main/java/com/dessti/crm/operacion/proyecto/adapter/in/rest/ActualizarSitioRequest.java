package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para editar un Sitio de un Proyecto (Req 21.2, 21.6). DTO de
 * entrada del contrato REST, distinto de la entidad JPA. El Proyecto y el Sitio se
 * indican en la ruta ({@code /proyectos/{id}/sitios/{sitioId}}); el {@code tenant_id}
 * y el actor se derivan del contexto (Req 23.4). El Proyecto asociado es inmutable.
 *
 * @param nombre    nuevo nombre del Sitio; obligatorio (1..200, Req 21.2).
 * @param direccion nueva direccion fisica del Sitio; opcional (hasta 500 caracteres).
 */
public record ActualizarSitioRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 500) String direccion) {
}

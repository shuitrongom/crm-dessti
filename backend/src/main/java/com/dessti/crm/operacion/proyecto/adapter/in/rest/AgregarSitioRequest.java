package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para agregar un Sitio a un Proyecto existente (Req 21.2).
 * DTO de entrada del contrato REST, distinto de la entidad JPA. El Proyecto se
 * indica en la ruta ({@code /proyectos/{id}/sitios}); el {@code tenant_id} y el
 * actor se derivan del contexto (Req 23.4).
 *
 * @param nombre    nombre del Sitio; obligatorio (1..200, Req 21.2).
 * @param direccion direccion fisica del Sitio; opcional (hasta 500 caracteres).
 */
public record AgregarSitioRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 500) String direccion) {
}

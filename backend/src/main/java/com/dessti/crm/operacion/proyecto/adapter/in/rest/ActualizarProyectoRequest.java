package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para editar un Proyecto (Req 21.1, 21.6). DTO de entrada del
 * contrato REST, distinto de la entidad JPA. Solo permite cambiar el nombre; el
 * Cliente asociado es inmutable. La validacion de negocio (nombre 1..200 tras recortar
 * espacios) la refuerza el dominio.
 *
 * @param nombre nuevo nombre del Proyecto; obligatorio (1..200, Req 21.1).
 */
public record ActualizarProyectoRequest(
        @NotBlank @Size(max = 200) String nombre) {
}

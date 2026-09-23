package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear un Proyecto asociado a un Cliente existente
 * (Req 21.1). DTO de entrada del contrato REST, distinto de la entidad JPA. El
 * {@code tenant_id} y el actor se derivan del contexto (Req 23.4). La validacion de
 * negocio (nombre 1..200 tras recortar espacios) la refuerza el dominio.
 *
 * @param clienteId identificador del Cliente asociado; obligatorio (Req 21.1).
 * @param nombre    nombre del Proyecto; obligatorio (1..200, Req 21.1).
 */
public record CrearProyectoRequest(
        @NotNull UUID clienteId,
        @NotBlank @Size(max = 200) String nombre) {
}

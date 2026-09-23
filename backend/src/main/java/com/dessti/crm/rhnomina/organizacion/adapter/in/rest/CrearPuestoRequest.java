package com.dessti.crm.rhnomina.organizacion.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear un Puesto (Req 61.1, tarea 36.1).
 *
 * <p>DTO de entrada del contrato REST, <strong>distinto</strong> de las entidades
 * y del comando de aplicacion (Req 12.2). El {@code tenant_id} NO se acepta en la
 * peticion; se deriva del contexto autenticado (Req 23.4).</p>
 *
 * @param nombre           nombre del Puesto; obligatorio (1..200).
 * @param descripcion      descripcion opcional (&lt;= 500).
 * @param puestoSuperiorId superior directo en la jerarquia; {@code null} si es raiz.
 */
public record CrearPuestoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 500) String descripcion,
        UUID puestoSuperiorId) {
}

package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para editar (renombrar/reclasificar) un Almacen (Req 60). DTO de
 * entrada del contrato REST, distinto de la entidad JPA. Las validaciones de formato se
 * refuerzan ademas en el dominio.
 *
 * @param nombre nuevo nombre del Almacen; obligatorio, 1..200 caracteres.
 * @param tipo   nuevo tipo del Almacen; obligatorio ({@code sucursal} o {@code bodega}).
 */
public record ActualizarAlmacenRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Pattern(regexp = "sucursal|bodega") String tipo) {
}

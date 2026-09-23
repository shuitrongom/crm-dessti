package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Almacen (Req 60). DTO de entrada del
 * contrato REST, distinto de la entidad JPA. Las validaciones de formato (nombre 1..200,
 * tipo {@code sucursal}/{@code bodega}) se refuerzan ademas en el dominio.
 *
 * @param nombre nombre del Almacen; obligatorio, 1..200 caracteres.
 * @param tipo   tipo del Almacen; obligatorio ({@code sucursal} o {@code bodega}).
 */
public record CrearAlmacenRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Pattern(regexp = "sucursal|bodega") String tipo) {
}

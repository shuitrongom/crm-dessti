package com.dessti.crm.operacion.inventario.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Material (Req 18.1). DTO de entrada del
 * contrato REST, distinto de la entidad JPA. Las validaciones de formato (nombre 1..200,
 * unidad obligatoria, stock minimo &gt;= 0) se refuerzan ademas en el dominio.
 *
 * @param nombre       nombre del Material; obligatorio, 1..200 caracteres.
 * @param unidadMedida unidad de medida; obligatoria.
 * @param stockMinimo  stock minimo; obligatorio y &gt;= 0.
 */
public record CrearMaterialRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 50) String unidadMedida,
        @NotNull @DecimalMin("0.0") BigDecimal stockMinimo) {
}

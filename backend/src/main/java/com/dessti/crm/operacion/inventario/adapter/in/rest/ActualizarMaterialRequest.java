package com.dessti.crm.operacion.inventario.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para editar un Material (Req 18). DTO de entrada del contrato
 * REST, distinto de la entidad JPA. Solo permite cambiar los datos descriptivos
 * (nombre, unidad de medida y stock minimo); las existencias no se editan aqui (solo
 * cambian por movimientos). Las validaciones de formato se refuerzan en el dominio.
 *
 * @param nombre       nuevo nombre del Material; obligatorio, 1..200 caracteres.
 * @param unidadMedida nueva unidad de medida; obligatoria.
 * @param stockMinimo  nuevo stock minimo; obligatorio y &gt;= 0.
 */
public record ActualizarMaterialRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 50) String unidadMedida,
        @NotNull @DecimalMin("0.0") BigDecimal stockMinimo) {
}

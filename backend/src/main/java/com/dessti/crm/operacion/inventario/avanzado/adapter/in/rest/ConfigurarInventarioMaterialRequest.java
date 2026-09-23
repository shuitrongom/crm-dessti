package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de la peticion para configurar (upsert) el inventario avanzado de un Material
 * (Req 60). DTO de entrada del contrato REST, distinto de la entidad JPA. Las validaciones
 * (metodo {@code promedio}/{@code peps}, valores &gt;= 0, stock maximo nulo o &gt;= 0) se
 * refuerzan ademas en el dominio.
 *
 * @param metodoCosteo      metodo de costeo; obligatorio ({@code promedio} o {@code peps}).
 * @param stockMaximo       stock maximo; {@code null} (sin tope) o &gt;= 0.
 * @param controlLote       {@code true} si el Material controla lotes; obligatorio.
 * @param consumoPromedio   consumo promedio por dia; obligatorio y &gt;= 0.
 * @param tiempoEntregaDias tiempo de entrega en dias; obligatorio y &gt;= 0.
 * @param stockSeguridad    stock de seguridad; obligatorio y &gt;= 0.
 */
public record ConfigurarInventarioMaterialRequest(
        @NotBlank @Pattern(regexp = "promedio|peps") String metodoCosteo,
        @DecimalMin("0.0") BigDecimal stockMaximo,
        @NotNull Boolean controlLote,
        @NotNull @DecimalMin("0.0") BigDecimal consumoPromedio,
        @NotNull @Min(0) Integer tiempoEntregaDias,
        @NotNull @DecimalMin("0.0") BigDecimal stockSeguridad) {
}

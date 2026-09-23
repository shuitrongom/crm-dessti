package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para registrar una ENTRADA de inventario en un Almacen con costeo
 * (Req 60.5, 60.10, 60.11). DTO de entrada del contrato REST, distinto de la entidad JPA.
 * El Almacen viaja en la ruta. Las validaciones (cantidad &gt; 0, costo &gt;= 0) se refuerzan
 * ademas en el dominio y el {@code MotorCosteo}.
 *
 * @param materialId    Material afectado; obligatorio.
 * @param loteCodigo    codigo del Lote a asociar; opcional (solo si el Material controla lotes).
 * @param cantidad      cantidad de la entrada; obligatoria y &gt; 0.
 * @param costoUnitario costo unitario de la entrada; obligatorio y &gt;= 0.
 * @param motivo        nota opcional del movimiento.
 */
public record RegistrarEntradaRequest(
        @NotNull UUID materialId,
        String loteCodigo,
        @NotNull @DecimalMin(value = "0.001") BigDecimal cantidad,
        @NotNull @DecimalMin(value = "0.0") BigDecimal costoUnitario,
        String motivo) {
}
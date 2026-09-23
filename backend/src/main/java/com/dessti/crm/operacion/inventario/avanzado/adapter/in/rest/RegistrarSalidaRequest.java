package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para registrar una SALIDA de inventario de un Almacen (Req 60.5,
 * 60.10, 60.11). DTO de entrada del contrato REST, distinto de la entidad JPA. El Almacen
 * viaja en la ruta. En una salida el costo NO se envia: lo determina el metodo de costeo
 * configurado del Material (promedio vigente o consumo de capas PEPS). La cantidad debe ser
 * &gt; 0 y no exceder el saldo (se rechaza con 422 "existencias insuficientes").
 *
 * @param materialId Material afectado; obligatorio.
 * @param loteCodigo codigo del Lote a asociar; opcional (solo si el Material controla lotes).
 * @param cantidad   cantidad de la salida; obligatoria y &gt; 0.
 * @param motivo     nota opcional del movimiento.
 */
public record RegistrarSalidaRequest(
        @NotNull UUID materialId,
        String loteCodigo,
        @NotNull @DecimalMin(value = "0.001") BigDecimal cantidad,
        String motivo) {
}
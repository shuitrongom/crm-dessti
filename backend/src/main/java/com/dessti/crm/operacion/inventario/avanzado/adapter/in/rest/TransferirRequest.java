package com.dessti.crm.operacion.inventario.avanzado.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para transferir existencias de un Material entre dos Almacenes
 * (Req 60.13). DTO de entrada del contrato REST, distinto de la entidad JPA. La transferencia
 * genera una salida en el origen y una entrada por la misma cantidad en el destino,
 * conservando el costo. Origen y destino deben ser distintos (se rechaza con 422 en caso
 * contrario) y debe haber saldo suficiente en el origen.
 *
 * @param almacenOrigenId  Almacen origen; obligatorio.
 * @param almacenDestinoId Almacen destino; obligatorio y distinto del origen.
 * @param materialId       Material a transferir; obligatorio.
 * @param cantidad         cantidad a transferir; obligatoria y &gt; 0.
 * @param motivo           nota opcional del movimiento.
 */
public record TransferirRequest(
        @NotNull UUID almacenOrigenId,
        @NotNull UUID almacenDestinoId,
        @NotNull UUID materialId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal cantidad,
        String motivo) {
}
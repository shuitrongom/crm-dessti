package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de aplicacion para TRANSFERIR una cantidad de un Material entre dos Almacenes
 * (Req 60.13, tarea 23.2). Transporta los datos de entrada del caso de uso, desacoplados del
 * contrato REST y de las entidades JPA.
 *
 * <p>La transferencia se materializa como una SALIDA en el Almacen de origen y una ENTRADA
 * por la MISMA cantidad en el de destino, dentro de una unica transaccion y agrupadas por un
 * {@code transferencia_id}, CONSERVANDO EL COSTO: la entrada en destino usa el costo unitario
 * calculado por la salida en origen (Req 60.13). Se rechaza (422) si origen y destino
 * coinciden o si el origen no tiene existencias suficientes.</p>
 *
 * @param almacenOrigenId  Almacen del que sale el Material; obligatorio.
 * @param almacenDestinoId Almacen al que entra el Material; obligatorio y distinto del origen.
 * @param materialId       Material a transferir; obligatorio.
 * @param cantidad         cantidad a transferir; obligatoria y &gt; 0.
 * @param motivo           nota opcional de la transferencia.
 */
public record TransferirCommand(
        UUID almacenOrigenId,
        UUID almacenDestinoId,
        UUID materialId,
        BigDecimal cantidad,
        String motivo) {
}

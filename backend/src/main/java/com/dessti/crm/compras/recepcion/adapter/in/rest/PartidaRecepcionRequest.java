package com.dessti.crm.compras.recepcion.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de un renglon de recepcion en las peticiones REST (Req 32.1). DTO de
 * entrada del contrato, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.compras.recepcion.application.RegistrarPartidaRecepcionCommand}
 * (Req 12.2).
 *
 * <p>La validacion de campo (Bean Validation -&gt; 400) cubre presencia y rango
 * (cantidad estrictamente positiva); el tope acumulado por partida (no exceder lo
 * ordenado, Req 32.3) lo aplica el dominio/servicio (422), y la pertenencia del
 * renglon a la Orden_Compra la verifica la capa de aplicacion.</p>
 *
 * @param partidaOrdenCompraId Partida_Orden_Compra contra la que se recibe;
 *                             obligatoria (Req 32.1).
 * @param cantidadRecibida     cantidad recibida; estrictamente positiva, escala 3.
 */
public record PartidaRecepcionRequest(
        @NotNull UUID partidaOrdenCompraId,
        @NotNull
        @DecimalMin(value = "0.001")
        @DecimalMax(value = "999999.999")
        @Digits(integer = 6, fraction = 3)
        BigDecimal cantidadRecibida) {
}

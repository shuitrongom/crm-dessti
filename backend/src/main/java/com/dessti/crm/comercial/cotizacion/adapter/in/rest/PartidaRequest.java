package com.dessti.crm.comercial.cotizacion.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de una Partida_Cotizacion en las peticiones REST (Req 6.3, 6.4). DTO de
 * entrada del contrato, distinto de la entidad y del comando de aplicacion
 * {@link com.dessti.crm.comercial.cotizacion.application.CrearPartidaCommand}
 * (Req 12.2).
 *
 * <p>La validacion de campo (Bean Validation -&gt; 400) cubre presencia, longitud
 * y rango; las reglas de dominio finas (422) las aplica el dominio. El
 * {@code precioUnitario} es <strong>opcional</strong>: si se omite y hay
 * {@code productoId}, la capa de aplicacion sugiere el precio via
 * {@code SugerenciaPrecioPort} (Req 59.4); si se indica, el valor del Usuario
 * prevalece sobre la sugerencia.</p>
 *
 * @param productoId     Producto referido; opcional ({@code null} = texto libre).
 * @param descripcion    descripcion de la partida; obligatoria (1..500).
 * @param cantidad       cantidad; entero en [1, 999,999] (Req 6.3, 6.4).
 * @param precioUnitario precio unitario; opcional, en [0.01, 999,999,999.99].
 */
public record PartidaRequest(
        UUID productoId,
        @NotBlank @Size(max = 500) String descripcion,
        @Min(1) @Max(999999) int cantidad,
        @DecimalMin(value = "0.01")
        @DecimalMax(value = "999999999.99")
        @Digits(integer = 9, fraction = 2)
        BigDecimal precioUnitario) {
}

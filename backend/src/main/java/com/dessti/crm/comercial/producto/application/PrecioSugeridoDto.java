package com.dessti.crm.comercial.producto.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de salida de la <strong>sugerencia de precio</strong> de un Producto
 * (Req 59.4, 59.9). Expone el resultado de la regla de seleccion de precio
 * ({@link SugerenciaPrecioPort}) como una consulta HTTP, para que la UI pueda
 * previsualizar el precio de lista <em>antes</em> de crear la Cotizacion (sin
 * imponerlo: el Usuario puede ajustarlo).
 *
 * <p>Cuando ninguna Lista_Precios vigente asigna precio al Producto, el campo
 * {@code precioSugerido} es {@code null} y {@code disponible} es {@code false};
 * en ese caso el Usuario debera capturar el precio manualmente. El precio nunca
 * se degrada a {@code 0}: la ausencia de sugerencia se comunica explicitamente.
 *
 * @param productoId      identificador del Producto consultado.
 * @param precioSugerido  precio unitario sugerido (escala 2), o {@code null} si
 *                        ninguna lista vigente aplica.
 * @param disponible      {@code true} si hubo una sugerencia; {@code false} si no.
 * @param fechaReferencia fecha usada para evaluar la vigencia de las listas.
 * @param segmentoCliente segmento del Cliente aplicado a la seleccion, o
 *                        {@code null} si no se indico ninguno.
 */
public record PrecioSugeridoDto(
        UUID productoId,
        BigDecimal precioSugerido,
        boolean disponible,
        LocalDate fechaReferencia,
        String segmentoCliente) {

    /**
     * Construye el DTO a partir del resultado de la seleccion de precio.
     *
     * @param productoId      Producto consultado.
     * @param precioSugerido  precio sugerido o {@code null} si no hubo.
     * @param fechaReferencia fecha de vigencia evaluada.
     * @param segmentoCliente segmento aplicado, o {@code null}.
     * @return el DTO con {@code disponible} derivado de la presencia del precio.
     */
    public static PrecioSugeridoDto de(UUID productoId, BigDecimal precioSugerido,
                                       LocalDate fechaReferencia, String segmentoCliente) {
        return new PrecioSugeridoDto(
                productoId, precioSugerido, precioSugerido != null, fechaReferencia, segmentoCliente);
    }
}

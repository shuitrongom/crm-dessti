package com.dessti.crm.comercial.cotizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de creacion de una Partida_Cotizacion dentro de una Cotizacion
 * (Req 6.3, 6.4). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad.
 *
 * <p>Cuando la partida refiere un {@code productoId} y no se indica
 * {@code precioUnitario} ({@code null}), la capa de aplicacion sugiere el precio a
 * traves de {@code SugerenciaPrecioPort} (Req 59.4); el precio sugerido es un
 * <strong>valor por defecto</strong> que el Usuario puede ajustar aportando su
 * propio {@code precioUnitario}.</p>
 *
 * @param productoId     Producto referido; opcional ({@code null} = texto libre).
 * @param descripcion    descripcion de la partida; obligatoria (1..500).
 * @param cantidad       cantidad; entero en [1, 999,999] (Req 6.3, 6.4).
 * @param precioUnitario precio unitario; en [0.01, 999,999,999.99]. Si es
 *                       {@code null} y hay {@code productoId}, se intenta sugerir.
 * @param descuento      descuento (monto) de la partida; {@code null}=0, no
 *                       negativo, no mayor que el importe bruto (V80).
 * @param tasaIva        etiqueta de la tasa de IVA ({@code 16}/{@code 8}/{@code 0}/
 *                       {@code exento}); {@code null}/blanco usa 16% (V80).
 */
public record CrearPartidaCommand(
        UUID productoId,
        String descripcion,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        String tasaIva) {

    /**
     * Constructor de conveniencia sin desglose fiscal (descuento 0, IVA por
     * defecto 16%). Preserva el contrato previo a V80.
     *
     * @param productoId     Producto referido; opcional.
     * @param descripcion    descripcion de la partida.
     * @param cantidad       cantidad.
     * @param precioUnitario precio unitario; opcional.
     */
    public CrearPartidaCommand(UUID productoId, String descripcion, int cantidad,
                               BigDecimal precioUnitario) {
        this(productoId, descripcion, cantidad, precioUnitario, null, null);
    }
}

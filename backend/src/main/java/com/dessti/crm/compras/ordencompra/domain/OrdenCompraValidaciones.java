package com.dessti.crm.compras.ordencompra.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion, normalizacion y aritmetica monetaria compartidas por
 * las entidades del submodulo de Ordenes de Compra ({@link OrdenCompra},
 * {@link PartidaOrdenCompra}) (Req 31). Centraliza las reglas de rango y el
 * redondeo <em>half-up</em> para evitar duplicarlas y garantizar un
 * comportamiento consistente y verificable. Es analoga a
 * {@code CotizacionValidaciones} del submodulo de Cotizaciones.
 *
 * <h2>Reglas (Req 31)</h2>
 * <ul>
 *   <li><strong>Cantidad de la partida:</strong> entero en el rango cerrado
 *       [1, 999,999] (Req 31.2). Fuera de rango se rechaza (422).</li>
 *   <li><strong>Precio unitario de la partida:</strong> dentro del rango cerrado
 *       [0.01, 999,999,999.99], escala 2 half-up (Req 31.2). Fuera de rango se
 *       rechaza (422).</li>
 *   <li><strong>Numero de partidas de la Orden_Compra:</strong> al crear, entre 1
 *       y 500 (Req 31.1).</li>
 *   <li><strong>Subtotal de la partida:</strong> {@code round(cantidad *
 *       precio_unitario, 2)} half-up (Req 31.3).</li>
 *   <li><strong>Total de la Orden_Compra:</strong> {@code round(Σ subtotales, 2)}
 *       half-up (Req 31.3).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class OrdenCompraValidaciones {

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    /** Cantidad minima aceptada por partida (Req 31.2). */
    public static final int CANTIDAD_MINIMA = 1;

    /** Cantidad maxima aceptada por partida (Req 31.2). */
    public static final int CANTIDAD_MAXIMA = 999_999;

    /** Precio unitario minimo aceptado (Req 31.2). */
    public static final BigDecimal PRECIO_MINIMO = new BigDecimal("0.01");

    /** Precio unitario maximo aceptado (Req 31.2). */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("999999999.99");

    /** Numero minimo de Partida_Orden_Compra al crear una Orden_Compra (Req 31.1). */
    public static final int PARTIDAS_MINIMAS = 1;

    /** Numero maximo de Partida_Orden_Compra al crear una Orden_Compra (Req 31.1). */
    public static final int PARTIDAS_MAXIMAS = 500;

    private OrdenCompraValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida la cantidad entera de una partida dentro del rango [1, 999,999]
     * (Req 31.2).
     *
     * @param cantidad cantidad a validar.
     * @return la misma cantidad si es valida.
     * @throws ReglaNegocioException si esta fuera del rango permitido (422).
     */
    public static int validarCantidad(int cantidad) {
        if (cantidad < CANTIDAD_MINIMA || cantidad > CANTIDAD_MAXIMA) {
            throw new ReglaNegocioException(
                    "La cantidad " + cantidad + " esta fuera del rango permitido ["
                            + CANTIDAD_MINIMA + ", " + CANTIDAD_MAXIMA + "].");
        }
        return cantidad;
    }

    /**
     * Valida un precio unitario y lo normaliza a 2 decimales (half-up). El precio
     * debe estar dentro del rango cerrado [0.01, 999,999,999.99] (Req 31.2). El
     * redondeo se aplica <em>antes</em> de la comprobacion de rango, de modo que
     * un valor con mas de 2 decimales se evalua por su representacion monetaria
     * efectiva (por ejemplo, {@code 0.004} redondea a {@code 0.00} y se rechaza).
     *
     * @param valor precio unitario a validar; obligatorio.
     * @return el precio normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera del rango (422).
     */
    public static BigDecimal validarPrecioUnitario(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El precio unitario de la partida es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRECIO_MINIMO) < 0 || normalizado.compareTo(PRECIO_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El precio unitario " + normalizado.toPlainString()
                            + " esta fuera del rango permitido [" + PRECIO_MINIMO.toPlainString()
                            + ", " + PRECIO_MAXIMO.toPlainString() + "].");
        }
        return normalizado;
    }

    /**
     * Calcula el subtotal de una partida como {@code round(cantidad *
     * precio_unitario, 2)} con redondeo half-up (Req 31.3). Asume que la cantidad
     * y el precio ya se validaron.
     *
     * @param cantidad       cantidad de la partida (>= 1).
     * @param precioUnitario precio unitario ya normalizado a escala 2.
     * @return el subtotal a escala 2 (half-up).
     */
    public static BigDecimal calcularSubtotalPartida(int cantidad, BigDecimal precioUnitario) {
        return precioUnitario
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Normaliza un importe monetario a escala 2 (half-up). Se usa para consolidar
     * el total de la Orden_Compra como {@code round(Σ subtotales, 2)} (Req 31.3).
     *
     * @param valor importe a normalizar; obligatorio.
     * @return el importe a escala 2 (half-up).
     */
    public static BigDecimal normalizarMonto(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}

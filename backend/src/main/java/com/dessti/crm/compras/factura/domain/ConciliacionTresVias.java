package com.dessti.crm.compras.factura.domain;

import java.math.BigDecimal;
import java.util.List;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Logica de dominio <strong>pura</strong> de la Conciliacion_Tres_Vias de las
 * Facturas de Proveedor (Req 33.3, 33.4, 33.5; Property 12). Funcion determinista,
 * sin estado ni dependencias de framework, verificable directamente con pruebas
 * unitarias y de propiedad.
 *
 * <p>Por cada renglon conciliado se verifica:</p>
 * <ol>
 *   <li><strong>Cantidad (Req 33.3):</strong> la cantidad facturada no puede
 *       exceder la cantidad recibida acumulada en las Recepcion_Mercancia
 *       asociadas ({@code cantidadFacturada <= cantidadRecibida}).</li>
 *   <li><strong>Precio dentro de tolerancia (Req 33.3):</strong> el precio
 *       facturado coincide con el precio de la Orden_Compra dentro de una tolerancia
 *       RELATIVA configurable: {@code |precioFacturado - precioOrden| <=
 *       precioOrden * tolerancia}.</li>
 * </ol>
 *
 * <p><strong>Invariante (Property 12):</strong> la conciliacion resulta
 * satisfactoria ({@link #esConciliable}) <em>si y solo si</em> TODOS los renglones
 * cumplen ambas condiciones. Basta que un renglon tenga
 * {@code cantidadFacturada > cantidadRecibida} o el precio fuera de tolerancia para
 * que la conciliacion NO sea satisfactoria y, por tanto, NUNCA se autorice el pago
 * fuera de tolerancia (Req 33.4).</p>
 *
 * <p>Clase de utilidad no instanciable. Las comparaciones usan
 * {@link BigDecimal#compareTo(BigDecimal)} para ser insensibles a la escala.</p>
 */
public final class ConciliacionTresVias {

    private ConciliacionTresVias() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Indica si el conjunto de renglones es conciliable con la tolerancia dada
     * (Property 12). Funcion pura: no muta nada.
     *
     * @param renglones  renglones a conciliar (cantidad facturada/recibida y precio
     *                   facturado/de OC); no vacio.
     * @param tolerancia fraccion decimal de tolerancia relativa de precio
     *                   ({@code >= 0}); obligatoria.
     * @return {@code true} si TODOS los renglones cumplen cantidad y precio dentro
     *         de tolerancia; {@code false} si alguno excede la cantidad recibida o
     *         se desvia del precio mas alla de la tolerancia.
     * @throws ReglaNegocioException si {@code renglones} es nulo/vacio, la tolerancia
     *         es nula/negativa, o algun renglon es nulo o tiene valores invalidos
     *         (422).
     */
    public static boolean esConciliable(List<RenglonConciliacion> renglones, BigDecimal tolerancia) {
        if (renglones == null || renglones.isEmpty()) {
            throw new ReglaNegocioException(
                    "La conciliacion requiere al menos un renglon de comparacion.");
        }
        if (tolerancia == null || tolerancia.signum() < 0) {
            throw new ReglaNegocioException(
                    "La tolerancia de conciliacion debe ser una fraccion no negativa.");
        }
        for (RenglonConciliacion renglon : renglones) {
            if (!renglonConciliable(renglon, tolerancia)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Indica si un renglon individual es conciliable: cantidad facturada &lt;=
     * recibida y precio dentro de tolerancia relativa (Req 33.3). Funcion pura.
     *
     * @param renglon    renglon a evaluar; obligatorio.
     * @param tolerancia fraccion decimal de tolerancia relativa de precio
     *                   ({@code >= 0}); obligatoria.
     * @return {@code true} si el renglon cumple ambas condiciones.
     * @throws ReglaNegocioException si el renglon es nulo o la tolerancia es
     *         nula/negativa (422).
     */
    public static boolean renglonConciliable(RenglonConciliacion renglon, BigDecimal tolerancia) {
        if (renglon == null) {
            throw new ReglaNegocioException("El renglon de conciliacion es obligatorio.");
        }
        if (tolerancia == null || tolerancia.signum() < 0) {
            throw new ReglaNegocioException(
                    "La tolerancia de conciliacion debe ser una fraccion no negativa.");
        }
        // Cantidad: la facturada no puede exceder la recibida acumulada (Req 33.3).
        if (renglon.cantidadFacturada().compareTo(renglon.cantidadRecibida()) > 0) {
            return false;
        }
        // Precio: |facturado - orden| <= orden * tolerancia (tolerancia relativa).
        BigDecimal desviacion = renglon.precioFacturado().subtract(renglon.precioOrden()).abs();
        BigDecimal maximaDesviacion = renglon.precioOrden().abs().multiply(tolerancia);
        return desviacion.compareTo(maximaDesviacion) <= 0;
    }

    /**
     * Renglon de comparacion de la Conciliacion_Tres_Vias: contrasta lo facturado
     * contra lo recibido y contra el precio de la Orden_Compra (Req 33.3). Objeto de
     * valor puro.
     *
     * @param cantidadFacturada cantidad facturada del renglon; obligatoria, &gt;= 0.
     * @param cantidadRecibida  cantidad recibida acumulada del renglon; obligatoria,
     *                          &gt;= 0.
     * @param precioFacturado   precio unitario facturado; obligatorio, &gt;= 0.
     * @param precioOrden       precio unitario de la Orden_Compra; obligatorio,
     *                          &gt;= 0.
     */
    public record RenglonConciliacion(BigDecimal cantidadFacturada, BigDecimal cantidadRecibida,
                                      BigDecimal precioFacturado, BigDecimal precioOrden) {

        /**
         * Construye el renglon validando presencia y no negatividad de todas las
         * magnitudes.
         *
         * @throws ReglaNegocioException si alguna magnitud es nula o negativa (422).
         */
        public RenglonConciliacion {
            if (cantidadFacturada == null || cantidadRecibida == null
                    || precioFacturado == null || precioOrden == null) {
                throw new ReglaNegocioException(
                        "El renglon de conciliacion requiere cantidades y precios.");
            }
            if (cantidadFacturada.signum() < 0 || cantidadRecibida.signum() < 0
                    || precioFacturado.signum() < 0 || precioOrden.signum() < 0) {
                throw new ReglaNegocioException(
                        "Las cantidades y precios del renglon de conciliacion no pueden ser negativos.");
            }
        }
    }
}

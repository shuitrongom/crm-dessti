package com.dessti.crm.compras.recepcion.domain;

import java.math.BigDecimal;
import java.util.Collection;

import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Reglas de dominio <strong>puras</strong> de la Recepcion_Mercancia (Req 32.3,
 * 32.5, 32.6). Centraliza dos funciones deterministas, sin estado ni dependencias
 * de framework, para poder verificarlas directamente con pruebas unitarias y de
 * propiedad (Property 10 y Property 11):
 *
 * <ol>
 *   <li><strong>No exceder lo ordenado (Property 10, Req 32.3):</strong>
 *       {@link #validarNoExcederOrdenado(BigDecimal, BigDecimal, BigDecimal)} —
 *       dada la cantidad ordenada de una partida, lo ya recibido (acumulado) y la
 *       nueva cantidad a recibir, rechaza con {@link ReglaNegocioException} (422)
 *       si {@code previa + nueva > ordenada}.</li>
 *   <li><strong>Estado de la Orden_Compra derivado (Property 11, Req 32.5,
 *       32.6):</strong>
 *       {@link #derivarEstadoOrdenCompra(Collection)} — dado, por cada partida, el
 *       par (ordenada, recibida acumulada), devuelve
 *       {@link EstadoOrdenCompra#RECIBIDA_TOTAL} si TODAS las partidas quedan
 *       completas, {@link EstadoOrdenCompra#RECIBIDA_PARCIAL} si alguna tiene
 *       recepcion pero no todas estan completas, o {@link EstadoOrdenCompra#ABIERTA}
 *       si no se ha recibido nada.</li>
 * </ol>
 *
 * <p>Clase de utilidad no instanciable. Las comparaciones monetarias/cantidad usan
 * {@link BigDecimal#compareTo(BigDecimal)} para ser insensibles a la escala.</p>
 */
public final class ReglasRecepcion {

    private ReglasRecepcion() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida que la cantidad recibida ACUMULADA de una partida no exceda la
     * cantidad ordenada (Property 10, Req 32.3). Es una funcion pura: no muta nada
     * y solo decide si la recepcion es admisible.
     *
     * @param ordenada       cantidad ordenada de la partida; obligatoria, &gt;= 0.
     * @param recibidaPrevia cantidad ya recibida (acumulada) de la partida antes de
     *                       esta recepcion; obligatoria, &gt;= 0.
     * @param recibidaNueva  cantidad que se pretende recibir ahora; obligatoria,
     *                       &gt; 0.
     * @throws ReglaNegocioException si algun argumento es nulo o negativo, si la
     *         nueva cantidad no es positiva, o si {@code recibidaPrevia +
     *         recibidaNueva > ordenada} (422), indicando la cantidad en exceso.
     */
    public static void validarNoExcederOrdenado(BigDecimal ordenada, BigDecimal recibidaPrevia,
                                                BigDecimal recibidaNueva) {
        if (ordenada == null || recibidaPrevia == null || recibidaNueva == null) {
            throw new ReglaNegocioException(
                    "Las cantidades de la recepcion (ordenada, previa y nueva) son obligatorias.");
        }
        if (ordenada.signum() < 0 || recibidaPrevia.signum() < 0) {
            throw new ReglaNegocioException(
                    "Las cantidades ordenada y previamente recibida no pueden ser negativas.");
        }
        if (recibidaNueva.signum() <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad recibida de la partida debe ser estrictamente positiva.");
        }
        BigDecimal acumulado = recibidaPrevia.add(recibidaNueva);
        if (acumulado.compareTo(ordenada) > 0) {
            BigDecimal exceso = acumulado.subtract(ordenada);
            throw new ReglaNegocioException(
                    "La cantidad recibida acumulada (" + acumulado.toPlainString()
                            + ") excede la cantidad ordenada (" + ordenada.toPlainString()
                            + ") en " + exceso.toPlainString() + ".");
        }
    }

    /**
     * Deriva el estado de una Orden_Compra a partir del avance de recepcion de sus
     * partidas (Property 11, Req 32.5, 32.6). Funcion pura y determinista:
     *
     * <ul>
     *   <li>{@link EstadoOrdenCompra#RECIBIDA_TOTAL} si TODAS las partidas estan
     *       completas ({@code recibida >= ordenada} en cada una, Req 32.5).</li>
     *   <li>{@link EstadoOrdenCompra#RECIBIDA_PARCIAL} si alguna partida tiene
     *       cantidad recibida &gt; 0 pero no todas estan completas (Req 32.6).</li>
     *   <li>{@link EstadoOrdenCompra#ABIERTA} si ninguna partida tiene recepcion
     *       (acumulado 0 en todas), estado inicial (sin cambio).</li>
     * </ul>
     *
     * @param avances avance por partida (ordenada, recibida acumulada); no vacio.
     * @return el estado derivado de la Orden_Compra.
     * @throws ReglaNegocioException si la coleccion es nula o vacia, o si algun
     *         avance es nulo/negativo (422).
     */
    public static EstadoOrdenCompra derivarEstadoOrdenCompra(Collection<AvancePartida> avances) {
        if (avances == null || avances.isEmpty()) {
            throw new ReglaNegocioException(
                    "La derivacion del estado requiere el avance de al menos una partida.");
        }
        boolean todasCompletas = true;
        boolean algunaConRecepcion = false;
        for (AvancePartida avance : avances) {
            if (avance == null) {
                throw new ReglaNegocioException("El avance de una partida no puede ser nulo.");
            }
            if (avance.recibidaAcumulada().signum() > 0) {
                algunaConRecepcion = true;
            }
            if (avance.recibidaAcumulada().compareTo(avance.ordenada()) < 0) {
                todasCompletas = false;
            }
        }
        if (todasCompletas) {
            return EstadoOrdenCompra.RECIBIDA_TOTAL;
        }
        if (algunaConRecepcion) {
            return EstadoOrdenCompra.RECIBIDA_PARCIAL;
        }
        return EstadoOrdenCompra.ABIERTA;
    }

    /**
     * Avance de recepcion de una partida de la Orden_Compra: cantidad ordenada y
     * cantidad recibida ACUMULADA (sumando todas las recepciones registradas).
     * Objeto de valor puro usado por {@link #derivarEstadoOrdenCompra(Collection)}
     * (Property 11).
     *
     * @param ordenada          cantidad ordenada de la partida; obligatoria, &gt;= 0.
     * @param recibidaAcumulada cantidad recibida acumulada de la partida;
     *                          obligatoria, &gt;= 0.
     */
    public record AvancePartida(BigDecimal ordenada, BigDecimal recibidaAcumulada) {

        /**
         * Construye el avance validando presencia y no negatividad de ambas
         * cantidades.
         *
         * @throws ReglaNegocioException si alguna cantidad es nula o negativa (422).
         */
        public AvancePartida {
            if (ordenada == null || recibidaAcumulada == null) {
                throw new ReglaNegocioException(
                        "El avance de la partida requiere la cantidad ordenada y la recibida.");
            }
            if (ordenada.signum() < 0 || recibidaAcumulada.signum() < 0) {
                throw new ReglaNegocioException(
                        "Las cantidades del avance de la partida no pueden ser negativas.");
            }
        }
    }
}

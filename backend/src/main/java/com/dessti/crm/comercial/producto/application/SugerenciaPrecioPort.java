package com.dessti.crm.comercial.producto.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de aplicacion que expone la <strong>sugerencia de precio unitario</strong>
 * de un Producto a partir de sus Listas_Precios vigentes (Req 59.4, 59.9). Es el
 * contrato que el submodulo de <em>Cotizaciones</em> (bloque 17, tarea 17.2)
 * consume para sugerir el precio de una {@code Partida_Cotizacion} referida a un
 * Producto, <strong>sin imponerlo</strong> (el Usuario puede ajustarlo).
 *
 * <p><strong>Por que un puerto:</strong> se define aqui, en el submodulo de
 * catalogo, para que el submodulo de Cotizaciones dependa de esta abstraccion y
 * no al reves, evitando un ciclo de dependencias entre submodulos comerciales. La
 * implementacion es {@code ServicioSeleccionPrecio}.</p>
 *
 * <h2>Regla de seleccion (Req 59.9)</h2>
 * <p>Entre las Listas_Precios <em>vigentes</em> a la fecha indicada que asignan
 * un precio al Producto:</p>
 * <ol>
 *   <li>Se prefiere la lista <strong>especifica del segmento</strong> del Cliente
 *       sobre la general.</li>
 *   <li>Dentro de cada grupo (segmento vs general), gana la de <strong>mayor
 *       prioridad</strong>.</li>
 *   <li>Si no hay ninguna lista vigente aplicable, no se sugiere precio
 *       ({@link Optional#empty()}).</li>
 * </ol>
 */
public interface SugerenciaPrecioPort {

    /**
     * Sugiere el precio unitario de un Producto para un Cliente en una fecha,
     * aplicando la regla de seleccion (Req 59.4, 59.9).
     *
     * @param consulta datos de la consulta (Producto, segmento del Cliente y
     *                 fecha); obligatoria.
     * @return el precio sugerido (escala 2), o {@link Optional#empty()} si ninguna
     *         Lista_Precios vigente asigna precio al Producto.
     */
    Optional<BigDecimal> sugerirPrecioUnitario(ConsultaSugerenciaPrecio consulta);

    /**
     * Datos de entrada para sugerir un precio (Req 59.4, 59.9).
     *
     * @param productoId       identificador del Producto; obligatorio.
     * @param segmentoCliente  segmento del Cliente; {@code null}/blanco para no
     *                         preferir ninguna lista de segmento.
     * @param fecha            fecha de referencia para la vigencia; obligatoria.
     */
    record ConsultaSugerenciaPrecio(UUID productoId, String segmentoCliente, LocalDate fecha) {
    }
}

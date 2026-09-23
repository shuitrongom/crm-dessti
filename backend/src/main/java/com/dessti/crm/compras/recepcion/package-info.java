/**
 * Submodulo <strong>Recepcion de Mercancia</strong> del modulo
 * compras-abastecimiento (Req 32, 18, 23; tarea 27.1). Registra la recepcion de la
 * mercancia de una {@code Orden_Compra}, actualiza el inventario y deriva el estado
 * de la Orden_Compra.
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code RecepcionMercancia} (raiz de agregado),
 *       {@code PartidaRecepcion} y {@code ReglasRecepcion} (funciones puras: tope
 *       acumulado por partida —Property 10— y derivacion del estado de la
 *       Orden_Compra —Property 11—).</li>
 *   <li>{@code application}: {@code ServicioRecepciones} (casos de uso), DTOs y
 *       comandos.</li>
 *   <li>{@code adapter.in.rest}: {@code RecepcionMercanciaController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code RecepcionMercanciaRepository} (con
 *       la agregacion del recibido acumulado por partida).</li>
 * </ul>
 *
 * <h2>Reglas (Req 32)</h2>
 * <p>Recepcion contra una Orden_Compra en estado {@code abierta}/
 * {@code recibida_parcial} (Req 32.1, 32.2); rechazo del exceso sobre lo ordenado
 * acumulado (Req 32.3, Property 10); generacion de un Movimiento_Inventario
 * {@code entrada} por Material recibido con incremento de existencias (Req 32.4,
 * via {@code RecepcionMaterialPort} del inventario); derivacion del estado de la
 * Orden_Compra a {@code recibida_parcial}/{@code recibida_total} (Req 32.5, 32.6,
 * Property 11); listado paginado con filtro por Orden_Compra (Req 32.7); auditoria
 * del alta y del cambio de estado (Req 32.8).</p>
 */
package com.dessti.crm.compras.recepcion;

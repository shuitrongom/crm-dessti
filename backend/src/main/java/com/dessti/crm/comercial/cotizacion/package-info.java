/**
 * Submodulo <strong>cotizacion</strong> del modulo comercial-crm: elaboracion y
 * seguimiento de Cotizaciones con partidas, totales y maquina de estados (Req 6).
 * Sigue la arquitectura hexagonal del proyecto con los paquetes {@code domain}
 * (raiz del agregado {@code Cotizacion}, entidad hija {@code PartidaCotizacion},
 * {@code EstadoCotizacion} y su maquina de estados pura, y
 * {@code CotizacionValidaciones}), {@code application} (casos de uso y proyeccion
 * a DTO), {@code adapter.out.persistence} (repositorios JPA y el adaptador del
 * puerto de conversion) y {@code adapter.in.rest} (controlador REST).
 *
 * <p>Este submodulo <em>implementa</em> el puerto
 * {@code CreacionCotizacionPort} declarado por el submodulo de Oportunidades
 * (Req 14.5): la conversion crea un cascaron de Cotizacion en {@code borrador}
 * vinculado a la Oportunidad de origen, al que se agregan partidas despues.
 * Ademas <em>consume</em> el puerto {@code SugerenciaPrecioPort} del submodulo de
 * catalogo (Req 59.4) para sugerir —sin imponer— el precio unitario de una
 * partida que refiere un Producto. El Canal_Venta (Req 63) agregara una FK
 * opcional en la tarea 17.3.</p>
 */
package com.dessti.crm.comercial.cotizacion;

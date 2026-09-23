/**
 * Submodulo <strong>oportunidad</strong> del modulo comercial-crm: gestion de
 * Oportunidades y del pipeline de ventas (Req 14). Sigue la arquitectura
 * hexagonal del proyecto con los paquetes {@code domain} (entidad
 * {@code Oportunidad}, {@code EtapaOportunidad} y su maquina de estados pura),
 * {@code application} (casos de uso y puertos), {@code adapter.out.persistence}
 * (repositorio JPA y adaptadores) y {@code adapter.in.rest} (controlador REST).
 *
 * <p>La conversion de una Oportunidad ganada en Cotizacion (Req 14.5) se expone
 * mediante el puerto {@code CreacionCotizacionPort}, cuya implementacion aporta
 * la tarea 17.2. El Canal_Venta (Req 63) agregara una FK opcional en la tarea
 * 17.3.</p>
 */
package com.dessti.crm.comercial.oportunidad;

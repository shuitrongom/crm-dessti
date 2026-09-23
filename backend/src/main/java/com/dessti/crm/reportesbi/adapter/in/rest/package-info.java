/**
 * Adaptadores de entrada REST del modulo reportes-bi (Req 22, 48).
 *
 * <p>Exponen el Tablero de indicadores por area
 * ({@link com.dessti.crm.reportesbi.adapter.in.rest.TableroController}) y la
 * Inteligencia de Negocio consolidada con la gestion de tableros personalizados
 * ({@link com.dessti.crm.reportesbi.adapter.in.rest.InteligenciaNegocioController}).
 * Cada endpoint aplica {@code @PreAuthorize("@autorizador.tiene(recurso, operacion)")}
 * para responder 403 sin el permiso correspondiente (Req 22.5, 48.6), recibe/devuelve
 * DTOs distintos de las entidades JPA y delega la logica en los servicios de
 * aplicacion.</p>
 */
package com.dessti.crm.reportesbi.adapter.in.rest;

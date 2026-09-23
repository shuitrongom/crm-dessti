/**
 * Adaptadores de entrada REST del catalogo de Giros de plataforma (Req 1.1, 1.6,
 * tarea 2.6).
 *
 * <p>Expone el {@link com.dessti.crm.platform.giros.rest.GiroController} bajo
 * {@code /api/v1/plataforma/giros}, guardado con permisos de plataforma
 * {@code giro:*} (V50) reservados al {@code super_admin}. Los DTO de peticion
 * ({@link com.dessti.crm.platform.giros.rest.CrearGiroRequest}) y de respuesta
 * ({@link com.dessti.crm.platform.giros.GiroDto}) son distintos de la entidad
 * JPA {@code Giro}. Sigue el patron de {@code platform.empresas.rest}.</p>
 */
package com.dessti.crm.platform.giros.rest;

/**
 * Adaptador de entrada REST del catalogo de modulos de plataforma.
 *
 * <p>Expone el {@link com.dessti.crm.platform.modulos.rest.ModuloController} bajo
 * {@code /api/v1/plataforma/modulos}, guardado con el permiso de plataforma
 * {@code plan:listar} (que solo posee el {@code super_admin}). Devuelve
 * {@link com.dessti.crm.platform.modulos.ModuloCatalogoDto}, distinto de cualquier
 * entidad JPA. Sigue el patron de {@code platform.giros.rest}.</p>
 */
package com.dessti.crm.platform.modulos.rest;

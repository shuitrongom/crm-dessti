/**
 * Adaptadores de entrada REST de la administracion de plataforma de Empresas
 * (Req 24, tarea 14.1).
 *
 * <p>Expone el {@link com.dessti.crm.platform.empresas.rest.EmpresaController}
 * bajo {@code /api/v1/empresas}, guardado con permisos de plataforma
 * {@code empresa:*} (V5) reservados al {@code super_admin}. Los DTO de peticion
 * son distintos de las entidades JPA y nunca exponen datos de negocio de las
 * Empresas (Req 24.3).</p>
 */
package com.dessti.crm.platform.empresas.rest;

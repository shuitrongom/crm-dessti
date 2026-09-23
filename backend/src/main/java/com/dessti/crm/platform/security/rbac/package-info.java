/**
 * Capa de autorizacion RBAC con denegacion por defecto (Req 3, 25.4).
 *
 * <p>Contiene el modelo de {@link com.dessti.crm.platform.security.rbac.Permiso
 * permiso atomico} {@code (recurso, operacion)}, el evaluador reutilizable
 * {@link com.dessti.crm.platform.security.rbac.Autorizador} usado desde
 * {@code @PreAuthorize} (bean {@code autorizador}), y el contrato de gating por
 * Plan {@link com.dessti.crm.platform.security.rbac.PlanModulosPort} con su
 * implementacion placeholder pendiente de la tarea 14.2.</p>
 *
 * <p>La habilitacion de la seguridad de metodo vive en
 * {@link com.dessti.crm.platform.security.MethodSecurityConfig}, separada de la
 * cadena de filtros/JWT de la tarea 9.1.</p>
 */
package com.dessti.crm.platform.security.rbac;

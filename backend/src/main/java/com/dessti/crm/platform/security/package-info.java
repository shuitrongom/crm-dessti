/**
 * Seguridad transversal del Sistema.
 *
 * <p>Tarea 9.1 (implementada): autenticacion JWT stateless. Contiene la
 * configuracion de Spring Security ({@link com.dessti.crm.platform.security.SecurityConfig}),
 * el filtro de autenticacion por Bearer
 * ({@link com.dessti.crm.platform.security.JwtAuthenticationFilter}), el
 * principal autenticado {@code TenantAware}
 * ({@link com.dessti.crm.platform.security.UsuarioAutenticado}), el servicio de
 * emision/validacion de tokens (subpaquete {@code jwt}) y el servicio y
 * endpoints de autenticacion (subpaquete {@code auth}).</p>
 *
 * <p>Pendiente: bloqueo por intentos fallidos y rate limiting (tarea 9.2),
 * RBAC deny-by-default (tarea 10) y revocacion de sesiones / denylist de
 * refresco (tarea 11.2). El subpaquete {@code crypto} cubre el cifrado en
 * reposo (tarea 6.2).</p>
 */
package com.dessti.crm.platform.security;

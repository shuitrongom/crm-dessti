/**
 * Perfil propio del Usuario autenticado (CHANGE 2): consulta de la cuenta y
 * cambio de la propia contrasena.
 *
 * <h2>Alcance</h2>
 * <p>Expone dos casos de uso que operan siempre sobre la cuenta del Usuario
 * autenticado, resuelta del principal del contexto de seguridad (nunca de la
 * peticion, Req 23.4):</p>
 * <ul>
 *   <li>{@code GET /auth/perfil}: devuelve id, identificador de acceso legible,
 *       roles y tenant. Funciona para el {@code super_admin} (tenant NULL) y para
 *       cualquier Usuario de Empresa.</li>
 *   <li>{@code PUT /auth/perfil/password}: cambia la contrasena propia previa
 *       verificacion de la actual.</li>
 * </ul>
 *
 * <h2>Relacion con otros paquetes</h2>
 * <p>Reutiliza la entidad de gestion
 * {@link com.dessti.crm.platform.security.usuarios.Usuario} y su repositorio; no
 * duplica el mapeo. La autorizacion es simplemente "estar autenticado": cada
 * Usuario gestiona su propia cuenta, sin permisos atomicos especiales.</p>
 *
 * <h2>RLS (Req 23)</h2>
 * <p>El cambio de contrasena respeta las politicas RLS de la tabla
 * {@code usuario}: para un Usuario de Empresa se fija {@code app.current_tenant}
 * antes del UPDATE; para el {@code super_admin} (tenant NULL) el UPDATE se
 * permite sin tenant fijado (politica {@code usuario_login_mutacion}). Nunca se
 * registran contrasenas ni hashes en logs o auditoria (Req 11.3).</p>
 */
package com.dessti.crm.platform.security.perfil;

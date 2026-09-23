/**
 * Gestion y revocacion de <strong>Sesiones</strong> (Token_Refresco) del
 * Sistema (Req 68, tarea 11.2): el almacen de refresh tokens y la denylist que
 * sustentan el corte inmediato de accesos ante un riesgo.
 *
 * <h2>Alcance</h2>
 * <ul>
 *   <li>Registrar cada Token_Refresco emitido con su {@code jti}, cuenta,
 *       empresa e instantes de emision/expiracion (Req 68.3).</li>
 *   <li>Revocar sesiones en cuatro escenarios (Req 68.1, 68.2, 68.4): cierre de
 *       sesion del Usuario, revocacion por Administrador, desactivacion de la
 *       cuenta y evento de seguridad (cambio de contrasena).</li>
 *   <li>Rechazar todo Token_Refresco revocado conforme al Req 1.9.</li>
 *   <li>Listar de forma paginada las sesiones activas de una cuenta (Req 68.5).</li>
 * </ul>
 *
 * <h2>Modelo elegido: fila por sesion con bandera {@code revocado}</h2>
 * <p>En lugar de una denylist separada, se persiste una <b>fila por
 * Token_Refresco</b> ({@link com.dessti.crm.platform.security.sesiones.SesionRefresco})
 * con una bandera {@code revocado}. Esta modalidad, prevista por el diseno como
 * "denylist o su equivalente", satisface con una sola tabla tanto el
 * <em>rechazo</em> de refresco revocado (consulta por {@code jti}) como el
 * <em>listado</em> de sesiones activas, requerido por el Req 68.5. NUNCA se
 * almacena el valor del token, solo su {@code jti} y metadatos (Req 10.10).</p>
 *
 * <h2>Arquitectura hexagonal</h2>
 * <p>El caso de uso depende del puerto
 * {@link com.dessti.crm.platform.security.sesiones.RegistroSesionesPort}; la
 * persistencia concreta la aporta
 * {@link com.dessti.crm.platform.security.sesiones.RegistroSesionesJpaAdapter}
 * sobre PostgreSQL (migracion {@code V6}). La prueba de propiedad de la
 * Property 41 (tarea 11.3) se construira sobre este mismo puerto.</p>
 *
 * <h2>Multi-tenant (Req 23)</h2>
 * <p>Igual que la entidad de autenticacion, la sesion tiene {@code tenant_id}
 * nullable ({@code null} = super_admin) y no hereda de la entidad tenant-scoped;
 * el actor/tenant se derivan del contexto autenticado, nunca de la peticion
 * (Req 23.4).</p>
 */
package com.dessti.crm.platform.security.sesiones;

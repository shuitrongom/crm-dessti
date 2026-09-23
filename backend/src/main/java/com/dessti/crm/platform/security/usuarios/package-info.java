/**
 * Gestion administrativa de <strong>Usuarios</strong> y su asignacion de Roles
 * (Req 4, tarea 11.1): el lado de <em>escritura</em> de las cuentas de acceso.
 *
 * <h2>Alcance</h2>
 * <p>Este paquete cubre las operaciones de administracion que ejecuta un
 * {@code admin_empresa} dentro de su Empresa (Req 3.4, 4):</p>
 * <ul>
 *   <li>Crear una cuenta de Usuario con contrasena cifrada y al menos un Rol
 *       (Req 4.1).</li>
 *   <li>Desactivar una cuenta para impedir su inicio de sesion (Req 4.2).</li>
 *   <li>Asignar/reemplazar los Roles de un Usuario, cuyos Permisos aplican en la
 *       siguiente evaluacion de autorizacion (Req 4.3).</li>
 *   <li>Rechazar el conflicto de identificador de acceso duplicado con 409
 *       (Req 4.4).</li>
 *   <li>Auditar toda operacion de gestion de acceso (Req 4.5).</li>
 * </ul>
 *
 * <h2>Por que hay DOS mapeos JPA sobre la tabla {@code usuario}</h2>
 * <p>La tabla {@code usuario} (migracion V1) se mapea con <strong>dos entidades
 * distintas</strong>, cada una cohesiva con su caso de uso y con contextos de
 * persistencia separados:</p>
 * <ul>
 *   <li>{@code com.dessti.crm.platform.security.auth.UsuarioAuth}: entidad de
 *       <strong>solo autenticacion</strong>. Modela el login y el bloqueo por
 *       intentos fallidos ({@code intentos_fallidos}, {@code bloqueado_hasta}).
 *       No conoce los Roles como relacion navegable; los permisos del token se
 *       resuelven aparte. <em>No se modifica desde este paquete.</em></li>
 *   <li>{@link com.dessti.crm.platform.security.usuarios.Usuario}: entidad de
 *       <strong>gestion</strong> (este paquete). Modela la relacion N:M con
 *       {@code Rol} (join {@code usuario_rol}) para asignar Roles, la bandera
 *       {@code activo}, {@code tenant_id}, {@code identificador_acceso},
 *       {@code hash_password} y las columnas de auditoria. No expone el dominio
 *       de bloqueo (eso vive en la ruta de login).</li>
 * </ul>
 * <p>Dos {@code @Entity} sobre la misma tabla son validas en JPA mientras se
 * usen en casos de uso separados. El mapeo de columnas de {@code Usuario}
 * coincide <em>exactamente</em> con V1 (nombres y nulabilidad) para que un
 * arranque completo con {@code ddl-auto=validate} valide sin conflictos. La
 * separacion mantiene cada entidad enfocada y permite que la tarea 11.2
 * (revocacion de sesiones) reutilice esta entidad de gestion sin acoplar la
 * ruta de autenticacion.</p>
 *
 * <h2>Multi-tenant (Req 23)</h2>
 * <p>Como {@code usuario} tiene {@code tenant_id} nullable (el
 * {@code super_admin} es de plataforma), {@link Usuario} no hereda de la entidad
 * tenant-scoped ni activa el filtro global de Hibernate; el aislamiento por
 * Empresa se aplica explicitamente en las consultas del repositorio y en
 * {@link com.dessti.crm.platform.security.usuarios.ServicioUsuarios}, derivando
 * siempre el {@code tenant_id} del contexto autenticado, nunca de la peticion
 * (Req 23.4).</p>
 */
package com.dessti.crm.platform.security.usuarios;

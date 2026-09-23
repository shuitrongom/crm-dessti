/**
 * Modelo de roles del CRM: roles predefinidos del Sistema y roles
 * personalizables por Empresa (Req 3, 27, 28).
 *
 * <p>Este paquete concentra la gestion de {@code rol} y {@code permiso} sobre el
 * esquema de la migracion V1 (tablas {@code rol}, {@code permiso},
 * {@code rol_permiso}) y la semilla de la migracion V5 (catalogo de permisos y
 * roles predefinidos):</p>
 * <ul>
 *   <li>{@link com.dessti.crm.platform.security.roles.Rol} y
 *       {@link com.dessti.crm.platform.security.roles.PermisoEntity}: entidades
 *       JPA. {@code rol} es de {@code tenant_id} nullable (predefinido =
 *       plataforma; personalizado = empresa), por lo que no hereda de
 *       {@code TenantScopedEntity} y su aislamiento por tenant se aplica en el
 *       servicio.</li>
 *   <li>{@link com.dessti.crm.platform.security.roles.ServicioRoles}: caso de
 *       uso que crea/modifica/elimina {@code Rol_Personalizado}, rechaza
 *       permisos de plataforma o inexistentes (Req 28.5), protege la
 *       inmutabilidad de los predefinidos (Req 28.6), aplica la unicidad por
 *       tenant (Req 28.2) y audita cada operacion (Req 28.7).</li>
 *   <li>{@link com.dessti.crm.platform.security.roles.ClasificadorRecursosPlataforma}:
 *       regla que separa los permisos de nivel plataforma de los de empresa
 *       (Req 27.7, 28.5).</li>
 * </ul>
 *
 * <p>Las entidades y repositorios de este paquete estan pensados para ser
 * reutilizados por la gestion de usuarios (tarea 11.1), sin colisionar con la
 * entidad {@code UsuarioAuth} del paquete {@code auth} (que es solo de
 * autenticacion).</p>
 */
package com.dessti.crm.platform.security.roles;

/**
 * Submodulo <strong>permisos y zonificacion</strong> del modulo operacion-produccion
 * (Req 17; tarea 21.2). Establece la raiz de paquetes
 * {@code com.dessti.crm.vertical.anuncios.permiso}, coherente con la organizacion de los
 * demas submodulos del modulo operacion-produccion.
 *
 * <p>Gestiona el {@code Permiso_Instalacion}: alta con datos obligatorios (tipo
 * {@code municipal}/{@code arrendador} y fecha de vencimiento) vinculada a un Sitio,
 * con estado inicial {@code solicitado} (Req 17.1); maquina de estados
 * {@code solicitado -> {aprobado|rechazado}} con estados finales (Req 17.2, 17.3);
 * notificacion de vencimiento proximo a 30 dias de los permisos {@code aprobado}
 * (Req 17.5); listado paginado con filtros por Sitio, tipo y estado (Req 17.6) y
 * auditoria del alta y del cambio de estado (Req 17.7).</p>
 *
 * <h2>Guarda de programacion de instalacion (Req 17.4)</h2>
 * <p>La instalacion de un Sitio solo puede programarse si su Permiso_Instalacion
 * requerido esta {@code aprobado}. Esa guarda la expone el {@code PermisoAprobadoPort}
 * (implementado por {@code PermisoAprobadoAdapter}), que el bloque 22 (instalacion,
 * tarea 22.1) consumira junto con el {@code LevantamientoCompletadoPort} del
 * submodulo de levantamiento.</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code PermisoInstalacion} (entidad y raiz de agregado),
 *       {@code EstadoPermisoInstalacion} (enum + maquina de estados pura
 *       {@code solicitado -> {aprobado|rechazado}}), {@code TipoPermisoInstalacion}
 *       (enum {@code municipal}/{@code arrendador}) y sus convertidores JPA.</li>
 *   <li>{@code application}: {@code ServicioPermisos} (casos de uso, incluida la
 *       notificacion de vencimientos proximos del Req 17.5), sus DTOs y comandos, el
 *       puerto {@code PermisoAprobadoPort} (guarda del Req 17.4 para el bloque 22) y
 *       el puerto {@code NotificadorPermisoPort} con su implementacion por defecto
 *       {@code NotificadorPermisoRegistroLog} y la {@code PermisoConfig}.</li>
 *   <li>{@code adapter.in.rest}: {@code PermisoInstalacionController} y su DTO de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code PermisoInstalacionRepository} y el
 *       adaptador {@code PermisoAprobadoAdapter}.</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> {@code PermisoInstalacion} extiende
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V20). Los permisos
 * {@code permiso_instalacion:*} ya se sembraron en V5 y se asignaron al rol
 * {@code instalacion} (Req 27.6).</p>
 */
package com.dessti.crm.vertical.anuncios.permiso;

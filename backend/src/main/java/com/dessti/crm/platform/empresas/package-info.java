/**
 * Administracion de plataforma de las Empresas (tenants) y del
 * {@code super_admin} (Req 24, tarea 14.1).
 *
 * <p>Este paquete implementa las operaciones de <strong>nivel plataforma</strong>
 * sobre la Empresa (Tenant): alta, activacion, suspension, consulta y listado
 * paginado, todas reservadas al rol {@code super_admin} (Req 24.1) y auditadas
 * como eventos de plataforma (Req 24.6). Al crear una Empresa se aprovisiona su
 * primer Usuario {@code admin_empresa} y una Suscripcion basica al Plan inicial
 * indicado (Req 24.2).</p>
 *
 * <h2>Fronteras de responsabilidad</h2>
 * <ul>
 *   <li><strong>La Empresa ES el tenant</strong> (V1): su PK {@code id} es el
 *       {@code tenant_id} de todos los datos de negocio. Por eso {@code Empresa}
 *       NO hereda de {@code TenantScopedEntity} ni activa el filtro de Hibernate
 *       por tenant; el {@code super_admin} debe poder listar y crear Empresas
 *       (Req 24.1, 24.5), lo que seria imposible bajo el filtro tenant.</li>
 *   <li><strong>Aislamiento del super_admin (Req 24.3):</strong> este paquete
 *       expone unicamente datos de plataforma de la Empresa (nombre, RFC,
 *       estado, fechas). No expone ningun dato de negocio de las Empresas
 *       (Clientes, Cotizaciones, etc.); el {@code super_admin} solo recibe los
 *       permisos {@code empresa:*} sembrados en V5, nunca permisos de modulos de
 *       negocio.</li>
 *   <li><strong>Suspension bloquea el login (Req 24.4):</strong> se publica el
 *       puerto de solo lectura {@link com.dessti.crm.platform.empresas.EstadoEmpresaPort}
 *       que consume {@code ServicioAutenticacion} para rechazar el inicio de
 *       sesion de los Usuarios de una Empresa suspendida, con mensaje generico
 *       (anti-enumeracion, Req 1.3).</li>
 *   <li><strong>Coordinacion con la tarea 14.2:</strong> las entidades
 *       {@link com.dessti.crm.platform.empresas.Plan} (referencia de solo
 *       lectura) y {@link com.dessti.crm.platform.empresas.Suscripcion} se
 *       introducen aqui de forma minima y aditiva solo para asociar el Plan
 *       inicial (Req 24.2). La gestion completa de Planes y Suscripciones
 *       (limites de usuarios, gating de modulos, cambios de estado) pertenece a
 *       la tarea 14.2, que extendera este modelo sin reemplazarlo.</li>
 * </ul>
 */
package com.dessti.crm.platform.empresas;

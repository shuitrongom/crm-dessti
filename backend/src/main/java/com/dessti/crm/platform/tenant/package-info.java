/**
 * Contexto de tenant y multi-tenancy (Req 23).
 *
 * <p>Implementa la Capa 1 del aislamiento multi-empresa (discriminador
 * {@code tenant_id} en la aplicacion):</p>
 * <ul>
 *   <li>{@link com.dessti.crm.platform.tenant.TenantContext}: almacen
 *       ThreadLocal del {@code tenant_id} de la peticion.</li>
 *   <li>{@link com.dessti.crm.platform.tenant.TenantResolutionFilter}: filtro
 *       de servlet que resuelve el tenant del contexto autenticado (nunca de
 *       parametros de la peticion, Req 23.4) y limpia el contexto al terminar.</li>
 *   <li>{@link com.dessti.crm.platform.tenant.TenantScopedEntity}:
 *       {@code @MappedSuperclass} con el filtro global de Hibernate
 *       ({@code tenantFilter}), {@code version}, timestamps y asignacion
 *       automatica del {@code tenant_id} en la escritura.</li>
 *   <li>{@link com.dessti.crm.platform.tenant.TenantFilterActivator}: habilita
 *       el filtro de Hibernate por peticion con el tenant del contexto.</li>
 *   <li>{@link com.dessti.crm.platform.tenant.TenantAware}: punto de extension
 *       para que el principal del JWT exponga su {@code tenant_id} (tarea 9).</li>
 * </ul>
 *
 * <p>La Capa 2 (Row-Level Security de PostgreSQL) se implementa en la tarea 4.2.</p>
 */
package com.dessti.crm.platform.tenant;

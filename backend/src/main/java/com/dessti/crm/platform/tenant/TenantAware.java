package com.dessti.crm.platform.tenant;

import java.util.UUID;

/**
 * Contrato que expone el {@code tenant_id} de un principal autenticado.
 *
 * <p>Punto de extension para la resolucion de tenant (Req 23). El
 * {@link TenantResolutionFilter} usa esta interfaz para obtener el
 * {@code tenant_id} sin acoplarse a un mecanismo de autenticacion concreto.</p>
 *
 * <p><strong>TODO (tarea 9 - JWT):</strong> el principal derivado del JWT
 * autenticado debera implementar esta interfaz devolviendo el claim
 * {@code tenant_id}. Un {@code tenant_id} nulo indica un principal de
 * plataforma (super_admin) sin empresa de negocio asociada.</p>
 */
public interface TenantAware {

    /**
     * @return el {@code tenant_id} de la empresa del principal, o {@code null}
     *         si se trata de un principal de plataforma sin tenant de negocio.
     */
    UUID getTenantId();
}

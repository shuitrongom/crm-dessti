package com.dessti.crm.platform.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Habilita el filtro global de Hibernate {@link TenantScopedEntity#TENANT_FILTER_NAME}
 * sobre la {@link Session} activa, usando el {@code tenant_id} del
 * {@link TenantContext} (Capa 1 del aislamiento multi-tenant, Req 23).
 *
 * <p>Una vez habilitado, Hibernate anade automaticamente
 * {@code WHERE tenant_id = :tenantId} a las consultas de las entidades que
 * extienden {@link TenantScopedEntity}, de modo que el codigo de negocio no
 * necesita filtrar por tenant de forma explicita.</p>
 *
 * <p>Se invoca por peticion desde el {@link TenantResolutionFilter} (tras
 * resolver el contexto de tenant). La activacion se realiza de forma perezosa
 * sobre la sesion actual; el filtro se limpia al cerrarse la sesion/peticion,
 * por lo que basta con desactivar el {@link TenantContext} para no filtrar
 * entre peticiones.</p>
 *
 * <p>Complementa —no sustituye— la Row-Level Security de PostgreSQL (Capa 2),
 * que se activa en la tarea 4.2.</p>
 */
@Component
public class TenantFilterActivator {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Habilita el filtro de tenant en la sesion actual si hay un
     * {@code tenant_id} en el contexto. Si no hay contexto (por ejemplo,
     * operaciones de plataforma del super_admin) el filtro no se activa.
     *
     * <p>Nota: la obtencion de la {@link Session} inicializa el
     * {@code EntityManager} de la peticion; por ello se invoca perezosamente
     * solo cuando existe contexto de tenant.</p>
     */
    public void enableForCurrentTenant() {
        TenantContext.getCurrent().ifPresent(this::enableFilter);
    }

    /**
     * Habilita el filtro de tenant en la sesion actual con un {@code tenant_id}
     * explicito. Util para pruebas o casos de uso que fijan el tenant.
     *
     * @param tenantId identificador de la empresa; no nulo.
     */
    public void enableFilter(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.getEnabledFilter(TenantScopedEntity.TENANT_FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(TenantScopedEntity.TENANT_FILTER_NAME);
        }
        filter.setParameter(TenantScopedEntity.TENANT_PARAM, tenantId);
    }

    /**
     * Desactiva el filtro de tenant en la sesion actual.
     */
    public void disableFilter() {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter(TenantScopedEntity.TENANT_FILTER_NAME);
    }
}

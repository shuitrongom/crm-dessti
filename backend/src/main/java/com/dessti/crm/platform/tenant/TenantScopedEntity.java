package com.dessti.crm.platform.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Superclase base reutilizable para todas las entidades de negocio
 * multi-tenant (Req 23). Las entidades futuras (Cliente, Cotizacion, etc.,
 * tareas 15+) deben extender esta clase para heredar:
 *
 * <ul>
 *   <li>La columna discriminadora {@code tenant_id} con indice recomendado.</li>
 *   <li>El {@link FilterDef}/{@link Filter} global de Hibernate
 *       ({@value #TENANT_FILTER_NAME}) que anade automaticamente
 *       {@code WHERE tenant_id = :tenantId} a las consultas cuando el filtro
 *       esta habilitado por peticion (ver {@link TenantFilterActivator}).</li>
 *   <li>El control de concurrencia optimista via {@code version} (Req 49).</li>
 *   <li>Las marcas temporales de auditoria en UTC.</li>
 *   <li>La asignacion automatica del {@code tenant_id} en la escritura a partir
 *       del {@link TenantContext}, evitando que el codigo de negocio lo
 *       manipule.</li>
 * </ul>
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id} no se
 * asigna nunca desde datos de la peticion; proviene exclusivamente del
 * {@link TenantContext} derivado del JWT autenticado.</p>
 */
@MappedSuperclass
@FilterDef(
        name = TenantScopedEntity.TENANT_FILTER_NAME,
        parameters = @ParamDef(name = TenantScopedEntity.TENANT_PARAM, type = UUID.class))
@Filter(
        name = TenantScopedEntity.TENANT_FILTER_NAME,
        condition = "tenant_id = :" + TenantScopedEntity.TENANT_PARAM)
public abstract class TenantScopedEntity {

    /** Nombre del filtro global de Hibernate para el aislamiento por tenant. */
    public static final String TENANT_FILTER_NAME = "tenantFilter";

    /** Nombre del parametro del filtro que recibe el {@code tenant_id}. */
    public static final String TENANT_PARAM = "tenantId";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Asigna automaticamente el {@code tenant_id} del contexto autenticado y
     * las marcas temporales de creacion antes de persistir, evitando que el
     * codigo de negocio establezca el tenant manualmente (Req 23.4).
     */
    @PrePersist
    protected void onPersistAssignTenant() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.require();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    /**
     * Actualiza la marca temporal de modificacion antes de cada actualizacion.
     */
    @jakarta.persistence.PreUpdate
    protected void onUpdateTouchTimestamp() {
        this.updatedAt = Instant.now();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}

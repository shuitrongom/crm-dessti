package com.dessti.crm.platform.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA de una regla configurable de alerta de auditoria
 * (Alerta_Auditoria, Req 10.11), mapeada a la tabla {@code alerta_auditoria}
 * (migracion {@code V4}).
 *
 * <p>Una regla define un {@link PatronAlerta patron} sensible, un {@code umbral}
 * de eventos y una {@code ventana} temporal: cuando el numero de eventos que
 * cumplen el patron supera el umbral dentro de la ventana, el
 * {@link ServicioAlertasAuditoria} emite una Notificacion a los
 * {@code destinatarios} configurados.</p>
 *
 * <p><strong>Multi-tenant:</strong> a diferencia de las entidades de negocio,
 * NO extiende {@code TenantScopedEntity} porque el {@code tenantId} es
 * <em>nullable</em> ({@code null} = ambito de plataforma). El aislamiento se
 * aplica con RLS en BD (ver V4) y en la capa de autorizacion.</p>
 */
@Entity
@Table(name = "alerta_auditoria")
public class AlertaAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "patron", nullable = false, length = 30)
    private PatronAlerta patron;

    @Column(name = "umbral", nullable = false)
    private int umbral;

    @Column(name = "ventana", nullable = false)
    private Duration ventana;

    @Column(name = "destinatarios", nullable = false)
    private String destinatarios;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

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

    /** Constructor requerido por JPA. */
    protected AlertaAuditoria() {
    }

    /**
     * Crea una regla de alerta de auditoria.
     *
     * @param tenantId      empresa de la regla; {@code null} para plataforma.
     * @param patron        patron sensible que dispara la alerta.
     * @param umbral        numero de eventos que dispara la alerta (>= 1).
     * @param ventana       ventana temporal de conteo.
     * @param destinatarios destinatarios de la Notificacion.
     * @param activa        si la regla esta activa.
     */
    public AlertaAuditoria(UUID tenantId, PatronAlerta patron, int umbral,
                           Duration ventana, String destinatarios, boolean activa) {
        this.tenantId = tenantId;
        this.patron = patron;
        this.umbral = umbral;
        this.ventana = ventana;
        this.destinatarios = destinatarios;
        this.activa = activa;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public PatronAlerta getPatron() {
        return patron;
    }

    public int getUmbral() {
        return umbral;
    }

    public Duration getVentana() {
        return ventana;
    }

    public String getDestinatarios() {
        return destinatarios;
    }

    public boolean isActiva() {
        return activa;
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

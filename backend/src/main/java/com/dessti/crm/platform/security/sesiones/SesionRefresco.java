package com.dessti.crm.platform.security.sesiones;

import java.time.Clock;
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
 * Entidad JPA de una Sesion (Token_Refresco) del Sistema, mapeada a la tabla
 * {@code sesion_refresco} (migracion {@code V6}). Sustenta el registro de
 * sesiones y la denylist de refresco revocados para la revocacion de Sesion
 * (Req 68).
 *
 * <p>Se persiste una fila por Token_Refresco emitido, con su {@code jti}, la
 * cuenta y empresa a la que pertenece, sus instantes de emision/expiracion y la
 * bandera {@code revocado} con su motivo e instante. Un Token_Refresco con
 * {@code revocado = true} se rechaza conforme al Req 1.9. NUNCA se almacena el
 * valor del token (Req 10.10, 11.3).</p>
 *
 * <p><strong>Multi-tenant:</strong> igual que {@code UsuarioAuth}, NO extiende
 * {@code TenantScopedEntity} porque el {@code tenantId} es <em>nullable</em>
 * ({@code null} = super_admin) y la sesion se maneja fuera del flujo de tenant
 * (login/refresh/logout). El aislamiento se aplica en la capa de autorizacion y
 * al derivar el actor/tenant del contexto autenticado.</p>
 *
 * <p>Lleva {@link Version @Version} porque la fila es <b>mutable</b> (pasa de
 * activa a revocada), habilitando el bloqueo optimista (Req 49).</p>
 */
@Entity
@Table(name = "sesion_refresco")
public class SesionRefresco {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    /** Identificador unico del Token_Refresco (claim {@code jti}); unico. */
    @Column(name = "jti", nullable = false, updatable = false, unique = true)
    private String jti;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    /** Empresa del Usuario; {@code null} para super_admin. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "emitido_en", nullable = false, updatable = false)
    private Instant emitidoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "revocado", nullable = false)
    private boolean revocado;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revocacion", length = 20)
    private MotivoRevocacion motivoRevocacion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Constructor requerido por JPA. */
    protected SesionRefresco() {
    }

    /**
     * Crea una Sesion activa (no revocada) a partir de su comando de registro.
     *
     * @param comando metadatos de la sesion recien emitida.
     */
    public SesionRefresco(RegistroSesion comando) {
        this.jti = comando.jti();
        this.usuarioId = comando.usuarioId();
        this.tenantId = comando.tenantId();
        this.emitidoEn = comando.emitidoEn();
        this.expiraEn = comando.expiraEn();
        this.revocado = false;
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

    /**
     * Marca la sesion como revocada con el motivo dado, registrando el instante
     * de revocacion (Req 68). Es idempotente: revocar una sesion ya revocada no
     * cambia el motivo ni el instante originales.
     *
     * @param motivo causa de la revocacion.
     * @param clock  reloj (UTC) para fijar {@code revocado_en}.
     * @return {@code true} si esta invocacion revoco la sesion (transicion de
     *         activa a revocada); {@code false} si ya estaba revocada.
     */
    public boolean revocar(MotivoRevocacion motivo, Clock clock) {
        if (this.revocado) {
            return false;
        }
        this.revocado = true;
        this.motivoRevocacion = motivo;
        this.revocadoEn = clock.instant();
        return true;
    }

    /**
     * Indica si la sesion esta activa en el instante dado: ni revocada ni
     * expirada.
     *
     * @param clock reloj (UTC) para comparar con la expiracion.
     * @return {@code true} si la sesion sigue vigente.
     */
    public boolean estaActiva(Clock clock) {
        return !revocado && expiraEn.isAfter(clock.instant());
    }

    public UUID getId() {
        return id;
    }

    public String getJti() {
        return jti;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Instant getEmitidoEn() {
        return emitidoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public boolean isRevocado() {
        return revocado;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public MotivoRevocacion getMotivoRevocacion() {
        return motivoRevocacion;
    }

    public long getVersion() {
        return version;
    }

    /** @return la vista de solo lectura de esta sesion (sin el valor del token). */
    public SesionActivaView aVista() {
        return new SesionActivaView(jti, usuarioId, tenantId, emitidoEn, expiraEn);
    }
}

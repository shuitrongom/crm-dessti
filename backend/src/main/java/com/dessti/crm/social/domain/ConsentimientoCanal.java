package com.dessti.crm.social.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code consentimiento_canal}: el registro de Opt_In/Opt_Out de un
 * Cliente o Contacto por {@link CanalSocial}, mapeada sobre la tabla
 * {@code consentimiento_canal} de la migracion V41 (Req 64.8, 64.9, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V41.</p>
 *
 * <p>Cada otorgamiento o revocacion se registra como una fila con su
 * {@link #estado}, el canal, el {@link #actor} y la marca temporal UTC
 * ({@link #registradoEn}) (Req 64.9). El Opt_In vigente de un sujeto en un canal es
 * el <em>ultimo</em> registro por {@code (tenant, canal, sujeto_externo)}: se
 * considera vigente si su estado es {@link EstadoConsentimiento#OPT_IN}.</p>
 */
@Entity
@Table(name = "consentimiento_canal")
public class ConsentimientoCanal extends TenantScopedEntity {

    /** Longitud maxima del sujeto externo (coincide con VARCHAR(120) de V41). */
    static final int MAX_SUJETO = 120;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Canal_Social del consentimiento; inmutable (Req 64.9). */
    @Convert(converter = CanalSocialConverter.class)
    @Column(name = "canal", nullable = false, length = 12, updatable = false)
    private CanalSocial canal;

    /** Identificador del sujeto en el canal (remitente); inmutable (Req 64.9). */
    @Column(name = "sujeto_externo", nullable = false, length = MAX_SUJETO, updatable = false)
    private String sujetoExterno;

    /** Cliente asociado, si se resolvio; {@code null} en caso contrario. */
    @Column(name = "cliente_id")
    private UUID clienteId;

    /** Estado del consentimiento (opt_in/opt_out); inmutable por registro (Req 64.9). */
    @Convert(converter = EstadoConsentimientoConverter.class)
    @Column(name = "estado", nullable = false, length = 8, updatable = false)
    private EstadoConsentimiento estado;

    /** Marca temporal UTC del registro (Req 64.9); inmutable. */
    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    /** Actor que registro el consentimiento (Req 64.9); inmutable. */
    @Column(name = "actor", length = 255, updatable = false)
    private String actor;

    protected ConsentimientoCanal() {
        // Requerido por JPA.
    }

    /**
     * Registra un Opt_In u Opt_Out validando los datos obligatorios (Req 64.9). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param canal         Canal_Social; obligatorio.
     * @param sujetoExterno identificador del sujeto en el canal; obligatorio (1..120).
     * @param clienteId     Cliente asociado; opcional ({@code null}).
     * @param estado        {@link EstadoConsentimiento#OPT_IN} u {@code OPT_OUT}; obligatorio.
     * @param registradoEn  marca temporal UTC; obligatoria (Req 64.9).
     * @param actor         identificador de quien registra (auditoria); obligatorio.
     * @return el ConsentimientoCanal listo para persistir.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static ConsentimientoCanal registrar(CanalSocial canal, String sujetoExterno,
                                                 UUID clienteId, EstadoConsentimiento estado,
                                                 Instant registradoEn, String actor) {
        if (canal == null) {
            throw new ReglaNegocioException("El consentimiento debe indicar el Canal_Social.");
        }
        if (sujetoExterno == null || sujetoExterno.isBlank()) {
            throw new ReglaNegocioException("El consentimiento debe indicar el sujeto (remitente).");
        }
        String sujeto = sujetoExterno.strip();
        if (sujeto.length() > MAX_SUJETO) {
            throw new ReglaNegocioException(
                    "El sujeto del consentimiento no puede exceder " + MAX_SUJETO + " caracteres.");
        }
        if (estado == null) {
            throw new ReglaNegocioException("El consentimiento debe indicar el estado (opt_in/opt_out).");
        }
        if (registradoEn == null) {
            throw new ReglaNegocioException("El consentimiento debe indicar la marca temporal UTC.");
        }

        ConsentimientoCanal consentimiento = new ConsentimientoCanal();
        consentimiento.id = UUID.randomUUID();
        consentimiento.canal = canal;
        consentimiento.sujetoExterno = sujeto;
        consentimiento.clienteId = clienteId;
        consentimiento.estado = estado;
        consentimiento.registradoEn = registradoEn;
        consentimiento.actor = actor;
        consentimiento.setCreatedBy(actor);
        consentimiento.setUpdatedBy(actor);
        return consentimiento;
    }

    /**
     * Indica si este registro representa un Opt_In vigente.
     *
     * @return {@code true} si el estado es {@link EstadoConsentimiento#OPT_IN}.
     */
    public boolean esVigente() {
        return estado.esVigente();
    }

    public UUID getId() {
        return id;
    }

    public CanalSocial getCanal() {
        return canal;
    }

    public String getSujetoExterno() {
        return sujetoExterno;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public EstadoConsentimiento getEstado() {
        return estado;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public String getActor() {
        return actor;
    }
}

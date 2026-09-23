package com.dessti.crm.calidad.domain;

import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code cambio_sgc}: un cambio del Sistema de Gestion
 * de Calidad, mapeada sobre la tabla {@code cambio_sgc} de la migracion V47 (Req 70.4,
 * clausula 6.3, Req 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}. El
 * mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 *
 * <h2>Guarda de aprobacion y maquina de estados (Req 70.4)</h2>
 * <p>El Cambio_SGC exige {@link #proposito}, {@link #consecuenciasPotenciales},
 * {@link #recursosNecesarios} y {@link #responsableId} <em>antes</em> de aprobar; estos
 * campos son obligatorios desde la creacion, de modo que un Cambio_SGC bien formado
 * siempre puede aprobarse. La maquina de estados pura de {@link EstadoCambioSgc} rige
 * {@code propuesto -> aprobado -> implementado}, con {@code rechazado} como final
 * alterno. La aprobacion registra actor y marca temporal UTC ({@link #aprobadoPor},
 * {@link #aprobadoEn}).</p>
 */
@Entity
@Table(name = "cambio_sgc")
public class CambioSgc extends TenantScopedEntity {

    /** Longitud maxima del titulo (coincide con VARCHAR(200) de V47). */
    static final int MAX_TITULO = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Titulo del cambio (Req 70.4). */
    @Column(name = "titulo", nullable = false, length = MAX_TITULO)
    private String titulo;

    /** Proposito del cambio; requerido antes de aprobar (Req 70.4). */
    @Column(name = "proposito", nullable = false)
    private String proposito;

    /** Consecuencias potenciales; requeridas antes de aprobar (Req 70.4). */
    @Column(name = "consecuencias_potenciales", nullable = false)
    private String consecuenciasPotenciales;

    /** Recursos necesarios; requeridos antes de aprobar (Req 70.4). */
    @Column(name = "recursos_necesarios", nullable = false)
    private String recursosNecesarios;

    /** Usuario responsable del cambio; requerido antes de aprobar (Req 70.4). */
    @Column(name = "responsable_id", nullable = false)
    private UUID responsableId;

    /** Estado del cambio (propuesto/aprobado/implementado/rechazado) (Req 70.4). */
    @Convert(converter = EstadoCambioSgcConverter.class)
    @Column(name = "estado", nullable = false, length = 16)
    private EstadoCambioSgc estado;

    /** Actor que aprobo el cambio; {@code null} hasta la aprobacion (Req 70.4). */
    @Column(name = "aprobado_por")
    private String aprobadoPor;

    /** Instante de aprobacion (UTC); {@code null} hasta la aprobacion (Req 70.4). */
    @Column(name = "aprobado_en")
    private Instant aprobadoEn;

    protected CambioSgc() {
        // Requerido por JPA.
    }

    /**
     * Propone un Cambio_SGC nuevo en estado {@link EstadoCambioSgc#PROPUESTO} (Req 70.4).
     * Exige desde el inicio proposito, consecuencias potenciales, recursos necesarios y
     * responsable, de modo que el cambio pueda aprobarse luego sin datos faltantes. El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param titulo                    titulo del cambio; obligatorio.
     * @param proposito                 proposito; obligatorio (Req 70.4).
     * @param consecuenciasPotenciales  consecuencias potenciales; obligatorias (Req 70.4).
     * @param recursosNecesarios        recursos necesarios; obligatorios (Req 70.4).
     * @param responsableId             Usuario responsable; obligatorio (Req 70.4).
     * @param actor                     identificador de origen (auditoria).
     * @return el Cambio_SGC listo para persistir, en estado {@code propuesto}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static CambioSgc proponer(String titulo, String proposito, String consecuenciasPotenciales,
                                     String recursosNecesarios, UUID responsableId, String actor) {
        if (titulo == null || titulo.isBlank()) {
            throw new ReglaNegocioException("El Cambio_SGC debe indicar un titulo.");
        }
        String tituloNormalizado = titulo.strip();
        if (tituloNormalizado.length() > MAX_TITULO) {
            throw new ReglaNegocioException(
                    "El titulo del Cambio_SGC no puede exceder " + MAX_TITULO + " caracteres.");
        }
        if (proposito == null || proposito.isBlank()) {
            throw new ReglaNegocioException("El Cambio_SGC debe indicar su proposito antes de aprobarse.");
        }
        if (consecuenciasPotenciales == null || consecuenciasPotenciales.isBlank()) {
            throw new ReglaNegocioException(
                    "El Cambio_SGC debe indicar sus consecuencias potenciales antes de aprobarse.");
        }
        if (recursosNecesarios == null || recursosNecesarios.isBlank()) {
            throw new ReglaNegocioException(
                    "El Cambio_SGC debe indicar los recursos necesarios antes de aprobarse.");
        }
        if (responsableId == null) {
            throw new ReglaNegocioException("El Cambio_SGC debe indicar su responsable antes de aprobarse.");
        }

        CambioSgc cambio = new CambioSgc();
        cambio.id = UUID.randomUUID();
        cambio.titulo = tituloNormalizado;
        cambio.proposito = proposito.strip();
        cambio.consecuenciasPotenciales = consecuenciasPotenciales.strip();
        cambio.recursosNecesarios = recursosNecesarios.strip();
        cambio.responsableId = responsableId;
        cambio.estado = EstadoCambioSgc.PROPUESTO;
        cambio.aprobadoPor = null;
        cambio.aprobadoEn = null;
        cambio.setCreatedBy(actor);
        cambio.setUpdatedBy(actor);
        return cambio;
    }

    /**
     * Aprueba el Cambio_SGC transitando a {@link EstadoCambioSgc#APROBADO} y registrando
     * el actor y la marca temporal UTC de la aprobacion (Req 70.4). La guarda de campos
     * obligatorios se satisface por invariante de {@link #proponer}: un cambio bien
     * formado siempre tiene proposito, consecuencias, recursos y responsable.
     *
     * @param aprobadoPor actor que aprueba; obligatorio.
     * @param aprobadoEn  instante de aprobacion (UTC); obligatorio.
     * @throws ReglaNegocioException       si falta algun requisito de aprobacion (422).
     * @throws TransicionInvalidaException si el estado actual no admite la aprobacion (409).
     */
    public void aprobar(String aprobadoPor, Instant aprobadoEn) {
        if (!this.estado.puedeTransicionarA(EstadoCambioSgc.APROBADO)) {
            throw new TransicionInvalidaException(
                    "El Cambio_SGC en estado '" + this.estado.valorBd() + "' no admite aprobacion.");
        }
        if (aprobadoPor == null || aprobadoPor.isBlank()) {
            throw new ReglaNegocioException("La aprobacion del Cambio_SGC debe indicar el actor.");
        }
        if (aprobadoEn == null) {
            throw new ReglaNegocioException("La aprobacion del Cambio_SGC debe indicar su marca temporal.");
        }
        verificarRequisitosAprobacion();
        this.estado = EstadoCambioSgc.APROBADO;
        this.aprobadoPor = aprobadoPor.strip();
        this.aprobadoEn = aprobadoEn;
        this.setUpdatedBy(aprobadoPor);
    }

    /**
     * Rechaza el Cambio_SGC transitando al estado final alterno
     * {@link EstadoCambioSgc#RECHAZADO} (Req 70.4).
     *
     * @param actor identificador de quien rechaza, para {@code updated_by}.
     * @throws TransicionInvalidaException si el estado actual no admite el rechazo (409).
     */
    public void rechazar(String actor) {
        if (!this.estado.puedeTransicionarA(EstadoCambioSgc.RECHAZADO)) {
            throw new TransicionInvalidaException(
                    "El Cambio_SGC en estado '" + this.estado.valorBd() + "' no admite rechazo.");
        }
        this.estado = EstadoCambioSgc.RECHAZADO;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca el Cambio_SGC como implementado transitando a
     * {@link EstadoCambioSgc#IMPLEMENTADO} (Req 70.4).
     *
     * @param actor identificador de quien implementa, para {@code updated_by}.
     * @throws TransicionInvalidaException si el estado actual no admite la implementacion (409).
     */
    public void implementar(String actor) {
        if (!this.estado.puedeTransicionarA(EstadoCambioSgc.IMPLEMENTADO)) {
            throw new TransicionInvalidaException(
                    "El Cambio_SGC en estado '" + this.estado.valorBd()
                            + "' no admite implementacion; debe estar aprobado.");
        }
        this.estado = EstadoCambioSgc.IMPLEMENTADO;
        this.setUpdatedBy(actor);
    }

    private void verificarRequisitosAprobacion() {
        if (proposito == null || proposito.isBlank()
                || consecuenciasPotenciales == null || consecuenciasPotenciales.isBlank()
                || recursosNecesarios == null || recursosNecesarios.isBlank()
                || responsableId == null) {
            throw new ReglaNegocioException(
                    "El Cambio_SGC exige proposito, consecuencias potenciales, recursos necesarios "
                            + "y responsable antes de aprobarse.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getProposito() {
        return proposito;
    }

    public String getConsecuenciasPotenciales() {
        return consecuenciasPotenciales;
    }

    public String getRecursosNecesarios() {
        return recursosNecesarios;
    }

    public UUID getResponsableId() {
        return responsableId;
    }

    public EstadoCambioSgc getEstado() {
        return estado;
    }

    public String getAprobadoPor() {
        return aprobadoPor;
    }

    public Instant getAprobadoEn() {
        return aprobadoEn;
    }
}

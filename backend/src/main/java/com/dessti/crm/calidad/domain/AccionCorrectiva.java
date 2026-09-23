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
 * Entidad JPA y raiz del agregado {@code accion_correctiva}: una Accion_Correctiva
 * del Sistema de Gestion de Calidad, mapeada sobre la tabla {@code accion_correctiva}
 * de la migracion V47 (Req 70.2, clausula 10.2, Req 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 *
 * <h2>Maquina de estados y guarda de cierre (Req 70.2; Property 43)</h2>
 * <p>El {@link #estado} sigue la maquina pura de {@link EstadoAccionCorrectiva}
 * ({@code abierta -> en_analisis -> en_ejecucion -> verificacion -> cerrada}). El
 * estado {@code cerrada} es <strong>final</strong> y su alcance esta protegido por
 * una guarda adicional: {@link #cerrar(String)} exige que la
 * {@link #eficaciaVerificada} sea {@code true}; en caso contrario rechaza el cierre
 * (<strong>Property 43</strong>) y el estado no cambia. La verificacion de eficacia
 * se registra con {@link #verificarEficacia(String, String)}.</p>
 */
@Entity
@Table(name = "accion_correctiva")
public class AccionCorrectiva extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** No_Conformidad de origen; {@code null} si no procede de una (Req 70.2). */
    @Column(name = "no_conformidad_id")
    private UUID noConformidadId;

    /** Usuario responsable de la Accion_Correctiva (Req 70.2). */
    @Column(name = "responsable_id", nullable = false)
    private UUID responsableId;

    /** Causa raiz identificada (Req 70.2). */
    @Column(name = "causa_raiz", nullable = false)
    private String causaRaiz;

    /** Acciones planificadas (Req 70.2). */
    @Column(name = "acciones_planificadas", nullable = false)
    private String accionesPlanificadas;

    /** Evidencia del cierre; {@code null} mientras no se cierre (Req 70.2). */
    @Column(name = "evidencia_cierre")
    private String evidenciaCierre;

    /** Indica si la eficacia de las acciones fue verificada (Req 70.2; Property 43). */
    @Column(name = "eficacia_verificada", nullable = false)
    private boolean eficaciaVerificada;

    /** Estado de la Accion_Correctiva (maquina de estados, Req 70.2). */
    @Convert(converter = EstadoAccionCorrectivaConverter.class)
    @Column(name = "estado", nullable = false, length = 16)
    private EstadoAccionCorrectiva estado;

    /** Instante del cierre (UTC); {@code null} mientras no este cerrada (Req 70.2). */
    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    protected AccionCorrectiva() {
        // Requerido por JPA.
    }

    /**
     * Abre una Accion_Correctiva nueva en estado {@link EstadoAccionCorrectiva#ABIERTA}
     * (Req 70.2). El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir
     * (Req 23.4). La eficacia arranca sin verificar y no hay evidencia ni marca de
     * cierre.
     *
     * @param noConformidadId     No_Conformidad de origen; opcional.
     * @param responsableId       Usuario responsable; obligatorio.
     * @param causaRaiz           causa raiz identificada; obligatoria.
     * @param accionesPlanificadas acciones planificadas; obligatorias.
     * @param actor               identificador de origen (auditoria).
     * @return la Accion_Correctiva lista para persistir, en estado {@code abierta}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static AccionCorrectiva abrir(UUID noConformidadId, UUID responsableId, String causaRaiz,
                                         String accionesPlanificadas, String actor) {
        if (responsableId == null) {
            throw new ReglaNegocioException("La Accion_Correctiva debe indicar su responsable.");
        }
        if (causaRaiz == null || causaRaiz.isBlank()) {
            throw new ReglaNegocioException("La Accion_Correctiva debe indicar la causa raiz.");
        }
        if (accionesPlanificadas == null || accionesPlanificadas.isBlank()) {
            throw new ReglaNegocioException("La Accion_Correctiva debe indicar las acciones planificadas.");
        }

        AccionCorrectiva accion = new AccionCorrectiva();
        accion.id = UUID.randomUUID();
        accion.noConformidadId = noConformidadId;
        accion.responsableId = responsableId;
        accion.causaRaiz = causaRaiz.strip();
        accion.accionesPlanificadas = accionesPlanificadas.strip();
        accion.evidenciaCierre = null;
        accion.eficaciaVerificada = false;
        accion.estado = EstadoAccionCorrectiva.ABIERTA;
        accion.cerradaEn = null;
        accion.setCreatedBy(actor);
        accion.setUpdatedBy(actor);
        return accion;
    }

    /**
     * Avanza el estado de la Accion_Correctiva validando la transicion con la maquina
     * pura (Req 70.2). No debe usarse para alcanzar {@link EstadoAccionCorrectiva#CERRADA}:
     * el cierre pasa por {@link #cerrar(String)}, que aplica la guarda de eficacia
     * (Property 43).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @param actor   identificador de quien avanza, para {@code updated_by}.
     * @throws ReglaNegocioException       si {@code destino} es nulo o es {@code cerrada} (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void avanzar(EstadoAccionCorrectiva destino, String actor) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino de la Accion_Correctiva es obligatorio.");
        }
        if (destino == EstadoAccionCorrectiva.CERRADA) {
            throw new ReglaNegocioException(
                    "El cierre de la Accion_Correctiva debe realizarse por la operacion de cierre "
                            + "que verifica la eficacia.");
        }
        if (this.estado == destino) {
            return;
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion invalida de Accion_Correctiva: " + this.estado.valorBd()
                            + " -> " + destino.valorBd() + ".");
        }
        this.estado = destino;
        this.setUpdatedBy(actor);
    }

    /**
     * Registra la verificacion (o retiro de verificacion) de la eficacia de las
     * acciones (Req 70.2). Es requisito previo del cierre (Property 43).
     *
     * @param evidencia evidencia de la verificacion; opcional (puede ser {@code null}).
     * @param actor     identificador de quien verifica, para {@code updated_by}.
     */
    public void verificarEficacia(String evidencia, String actor) {
        this.eficaciaVerificada = true;
        if (evidencia != null && !evidencia.isBlank()) {
            this.evidenciaCierre = evidencia.strip();
        }
        this.setUpdatedBy(actor);
    }

    /**
     * Cierra la Accion_Correctiva transitando a {@link EstadoAccionCorrectiva#CERRADA}
     * (Req 70.2). <strong>Guarda de cierre (Property 43):</strong> solo procede si la
     * {@link #eficaciaVerificada} es {@code true}; en caso contrario se rechaza el
     * cierre y el estado no cambia. Ademas la transicion debe ser valida segun la
     * maquina de estados (solo desde {@code verificacion}).
     *
     * @param actor identificador de quien cierra, para {@code updated_by}.
     * @throws ReglaNegocioException       si la eficacia no esta verificada (422; Property 43).
     * @throws TransicionInvalidaException si el estado actual no admite el cierre (409).
     */
    public void cerrar(String actor) {
        if (!this.estado.puedeTransicionarA(EstadoAccionCorrectiva.CERRADA)) {
            throw new TransicionInvalidaException(
                    "La Accion_Correctiva en estado '" + this.estado.valorBd()
                            + "' no admite el cierre; debe estar en verificacion.");
        }
        if (!this.eficaciaVerificada) {
            throw new ReglaNegocioException(
                    "No se puede cerrar la Accion_Correctiva sin la eficacia verificada.");
        }
        this.estado = EstadoAccionCorrectiva.CERRADA;
        this.cerradaEn = Instant.now();
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la Accion_Correctiva esta cerrada (estado final).
     *
     * @return {@code true} si el estado es {@link EstadoAccionCorrectiva#CERRADA}.
     */
    public boolean estaCerrada() {
        return estado == EstadoAccionCorrectiva.CERRADA;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNoConformidadId() {
        return noConformidadId;
    }

    public UUID getResponsableId() {
        return responsableId;
    }

    public String getCausaRaiz() {
        return causaRaiz;
    }

    public String getAccionesPlanificadas() {
        return accionesPlanificadas;
    }

    public String getEvidenciaCierre() {
        return evidenciaCierre;
    }

    public boolean isEficaciaVerificada() {
        return eficaciaVerificada;
    }

    public EstadoAccionCorrectiva getEstado() {
        return estado;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }
}

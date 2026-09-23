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
 * Entidad JPA y raiz del agregado {@code no_conformidad}: una No_Conformidad
 * detectada en el Sistema de Gestion de Calidad, mapeada sobre la tabla
 * {@code no_conformidad} de la migracion V47 (Req 70.2, clausula 10.2, Req 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}.
 * El mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 *
 * <h2>Maquina de estados (Req 70.2)</h2>
 * <p>El {@link #estado} (abierta/en_tratamiento/cerrada) se gobierna con la maquina
 * de estados pura de {@link EstadoNoConformidad}. {@link #cambiarEstado} valida la
 * transicion antes de aplicarla.</p>
 */
@Entity
@Table(name = "no_conformidad")
public class NoConformidad extends TenantScopedEntity {

    /** Longitud maxima del proceso afectado (coincide con VARCHAR(200) de V47). */
    static final int MAX_PROCESO = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Origen de la No_Conformidad (queja/auditoria_interna/proceso/proveedor/otro). */
    @Convert(converter = OrigenNoConformidadConverter.class)
    @Column(name = "origen", nullable = false, length = 20)
    private OrigenNoConformidad origen;

    /** Descripcion de la No_Conformidad (Req 70.2). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Proceso afectado por la No_Conformidad (Req 70.2). */
    @Column(name = "proceso_afectado", nullable = false, length = MAX_PROCESO)
    private String procesoAfectado;

    /** Instante de deteccion (UTC). */
    @Column(name = "detectada_en", nullable = false)
    private Instant detectadaEn;

    /** Estado de la No_Conformidad (abierta/en_tratamiento/cerrada) (Req 70.2). */
    @Convert(converter = EstadoNoConformidadConverter.class)
    @Column(name = "estado", nullable = false, length = 16)
    private EstadoNoConformidad estado;

    protected NoConformidad() {
        // Requerido por JPA.
    }

    /**
     * Registra una No_Conformidad nueva en estado {@link EstadoNoConformidad#ABIERTA}
     * (Req 70.2). El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir
     * (Req 23.4).
     *
     * @param origen          origen de la No_Conformidad; obligatorio.
     * @param descripcion     descripcion; obligatoria.
     * @param procesoAfectado proceso afectado; obligatorio.
     * @param detectadaEn     instante de deteccion (UTC); obligatorio.
     * @param actor           identificador de origen (auditoria).
     * @return la No_Conformidad lista para persistir, en estado {@code abierta}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static NoConformidad registrar(OrigenNoConformidad origen, String descripcion,
                                          String procesoAfectado, Instant detectadaEn, String actor) {
        if (origen == null) {
            throw new ReglaNegocioException("La No_Conformidad debe indicar su origen.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("La No_Conformidad debe indicar una descripcion.");
        }
        if (procesoAfectado == null || procesoAfectado.isBlank()) {
            throw new ReglaNegocioException("La No_Conformidad debe indicar el proceso afectado.");
        }
        String proceso = procesoAfectado.strip();
        if (proceso.length() > MAX_PROCESO) {
            throw new ReglaNegocioException(
                    "El proceso afectado no puede exceder " + MAX_PROCESO + " caracteres.");
        }
        if (detectadaEn == null) {
            throw new ReglaNegocioException("La No_Conformidad debe indicar su marca temporal de deteccion.");
        }

        NoConformidad noConformidad = new NoConformidad();
        noConformidad.id = UUID.randomUUID();
        noConformidad.origen = origen;
        noConformidad.descripcion = descripcion.strip();
        noConformidad.procesoAfectado = proceso;
        noConformidad.detectadaEn = detectadaEn;
        noConformidad.estado = EstadoNoConformidad.ABIERTA;
        noConformidad.setCreatedBy(actor);
        noConformidad.setUpdatedBy(actor);
        return noConformidad;
    }

    /**
     * Cambia el estado de la No_Conformidad validando la transicion con la maquina de
     * estados pura (Req 70.2).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @param actor   identificador de quien cambia el estado, para {@code updated_by}.
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoNoConformidad destino, String actor) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino de la No_Conformidad es obligatorio.");
        }
        if (this.estado == destino) {
            return;
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion invalida de No_Conformidad: " + this.estado.valorBd()
                            + " -> " + destino.valorBd() + ".");
        }
        this.estado = destino;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public OrigenNoConformidad getOrigen() {
        return origen;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getProcesoAfectado() {
        return procesoAfectado;
    }

    public Instant getDetectadaEn() {
        return detectadaEn;
    }

    public EstadoNoConformidad getEstado() {
        return estado;
    }
}

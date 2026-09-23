package com.dessti.crm.calidad.domain;

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
 * Entidad JPA y raiz del agregado {@code oportunidad_calidad}: una oportunidad de
 * mejora del Sistema de Gestion de Calidad, mapeada sobre la tabla
 * {@code oportunidad_calidad} de la migracion V47 (Req 70.3, clausula 6.1.3, Req 23).
 *
 * <p>Es un agregado <strong>distinto</strong> del {@link Riesgo} (clausula 6.1.2), de
 * modo que las acciones para abordar riesgos y las acciones para aprovechar
 * oportunidades se determinan y consultan por separado (Req 70.3). Se nombra
 * {@code OportunidadCalidad} para no confundir con la {@code Oportunidad} comercial del
 * pipeline (Req 14).</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}. El
 * mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 */
@Entity
@Table(name = "oportunidad_calidad")
public class OportunidadCalidad extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Descripcion de la oportunidad (Req 70.3). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Beneficio esperado de la oportunidad (Req 70.3). */
    @Column(name = "beneficio_esperado", nullable = false)
    private String beneficioEsperado;

    /** Acciones para aprovechar la oportunidad; opcional (Req 70.3). */
    @Column(name = "acciones")
    private String acciones;

    /** Estado de la oportunidad (Req 70.3). */
    @Convert(converter = EstadoOportunidadCalidadConverter.class)
    @Column(name = "estado", nullable = false, length = 16)
    private EstadoOportunidadCalidad estado;

    protected OportunidadCalidad() {
        // Requerido por JPA.
    }

    /**
     * Identifica una Oportunidad_Calidad nueva en estado
     * {@link EstadoOportunidadCalidad#IDENTIFICADA} (Req 70.3). El {@code tenant_id} lo
     * fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param descripcion       descripcion; obligatoria.
     * @param beneficioEsperado beneficio esperado; obligatorio.
     * @param acciones          acciones para aprovecharla; opcional.
     * @param actor             identificador de origen (auditoria).
     * @return la Oportunidad_Calidad lista para persistir, en estado {@code identificada}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static OportunidadCalidad identificar(String descripcion, String beneficioEsperado,
                                                 String acciones, String actor) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("La Oportunidad_Calidad debe indicar una descripcion.");
        }
        if (beneficioEsperado == null || beneficioEsperado.isBlank()) {
            throw new ReglaNegocioException("La Oportunidad_Calidad debe indicar el beneficio esperado.");
        }

        OportunidadCalidad oportunidad = new OportunidadCalidad();
        oportunidad.id = UUID.randomUUID();
        oportunidad.descripcion = descripcion.strip();
        oportunidad.beneficioEsperado = beneficioEsperado.strip();
        oportunidad.acciones = (acciones == null || acciones.isBlank()) ? null : acciones.strip();
        oportunidad.estado = EstadoOportunidadCalidad.IDENTIFICADA;
        oportunidad.setCreatedBy(actor);
        oportunidad.setUpdatedBy(actor);
        return oportunidad;
    }

    /**
     * Cambia el estado de la oportunidad validando la transicion con la maquina pura
     * (Req 70.3).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @param actor   identificador de quien cambia el estado, para {@code updated_by}.
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoOportunidadCalidad destino, String actor) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino de la Oportunidad_Calidad es obligatorio.");
        }
        if (this.estado == destino) {
            return;
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion invalida de Oportunidad_Calidad: " + this.estado.valorBd()
                            + " -> " + destino.valorBd() + ".");
        }
        this.estado = destino;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getBeneficioEsperado() {
        return beneficioEsperado;
    }

    public String getAcciones() {
        return acciones;
    }

    public EstadoOportunidadCalidad getEstado() {
        return estado;
    }
}

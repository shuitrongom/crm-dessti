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
 * Entidad JPA y raiz del agregado {@code riesgo}: un Riesgo del Sistema de Gestion de
 * Calidad, mapeada sobre la tabla {@code riesgo} de la migracion V47 (Req 70.3,
 * clausula 6.1.2, Req 23). Es un agregado <strong>distinto</strong> de la
 * {@link OportunidadCalidad}, reflejando la separacion de las clausulas 6.1.2 y 6.1.3.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}. El
 * mapeo de columnas coincide <em>exactamente</em> con V47.</p>
 *
 * <h2>Nivel derivado (solo lectura, Req 70.3)</h2>
 * <p>El {@link #nivelDerivado} lo calcula la funcion pura
 * {@link NivelRiesgo#derivar(Probabilidad, Impacto)} cada vez que cambian la
 * probabilidad o el impacto; nunca lo fija el cliente del API. Se persiste para
 * consulta e indexacion.</p>
 */
@Entity
@Table(name = "riesgo")
public class Riesgo extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Descripcion del riesgo (Req 70.3). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Probabilidad del riesgo (Req 70.3, clausula 6.1.2). */
    @Convert(converter = ProbabilidadConverter.class)
    @Column(name = "probabilidad", nullable = false, length = 8)
    private Probabilidad probabilidad;

    /** Impacto del riesgo (Req 70.3, clausula 6.1.2). */
    @Convert(converter = ImpactoConverter.class)
    @Column(name = "impacto", nullable = false, length = 8)
    private Impacto impacto;

    /** Nivel derivado (solo lectura) de la matriz probabilidad x impacto (Req 70.3). */
    @Convert(converter = NivelRiesgoConverter.class)
    @Column(name = "nivel_derivado", nullable = false, length = 8)
    private NivelRiesgo nivelDerivado;

    /** Acciones para abordar el riesgo; opcional (Req 70.3). */
    @Column(name = "acciones")
    private String acciones;

    /** Estado del riesgo (identificado/en_tratamiento/mitigado/aceptado) (Req 70.3). */
    @Convert(converter = EstadoRiesgoConverter.class)
    @Column(name = "estado", nullable = false, length = 16)
    private EstadoRiesgo estado;

    protected Riesgo() {
        // Requerido por JPA.
    }

    /**
     * Identifica un Riesgo nuevo en estado {@link EstadoRiesgo#IDENTIFICADO} (Req 70.3).
     * El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4). El
     * nivel derivado se calcula de inmediato.
     *
     * @param descripcion  descripcion del riesgo; obligatoria.
     * @param probabilidad probabilidad; obligatoria.
     * @param impacto      impacto; obligatorio.
     * @param acciones     acciones para abordarlo; opcional.
     * @param actor        identificador de origen (auditoria).
     * @return el Riesgo listo para persistir, en estado {@code identificado}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static Riesgo identificar(String descripcion, Probabilidad probabilidad, Impacto impacto,
                                     String acciones, String actor) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("El Riesgo debe indicar una descripcion.");
        }
        if (probabilidad == null) {
            throw new ReglaNegocioException("El Riesgo debe indicar su probabilidad.");
        }
        if (impacto == null) {
            throw new ReglaNegocioException("El Riesgo debe indicar su impacto.");
        }

        Riesgo riesgo = new Riesgo();
        riesgo.id = UUID.randomUUID();
        riesgo.descripcion = descripcion.strip();
        riesgo.probabilidad = probabilidad;
        riesgo.impacto = impacto;
        riesgo.nivelDerivado = NivelRiesgo.derivar(probabilidad, impacto);
        riesgo.acciones = (acciones == null || acciones.isBlank()) ? null : acciones.strip();
        riesgo.estado = EstadoRiesgo.IDENTIFICADO;
        riesgo.setCreatedBy(actor);
        riesgo.setUpdatedBy(actor);
        return riesgo;
    }

    /**
     * Reevalua el riesgo actualizando la probabilidad y/o el impacto y recalculando el
     * nivel derivado (Req 70.3). Los argumentos nulos conservan el valor actual.
     *
     * @param probabilidad nueva probabilidad; {@code null} conserva la actual.
     * @param impacto      nuevo impacto; {@code null} conserva el actual.
     * @param actor        identificador de quien reevalua, para {@code updated_by}.
     */
    public void reevaluar(Probabilidad probabilidad, Impacto impacto, String actor) {
        if (probabilidad != null) {
            this.probabilidad = probabilidad;
        }
        if (impacto != null) {
            this.impacto = impacto;
        }
        this.nivelDerivado = NivelRiesgo.derivar(this.probabilidad, this.impacto);
        this.setUpdatedBy(actor);
    }

    /**
     * Cambia el estado del riesgo validando la transicion con la maquina pura (Req 70.3).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @param actor   identificador de quien cambia el estado, para {@code updated_by}.
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoRiesgo destino, String actor) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino del Riesgo es obligatorio.");
        }
        if (this.estado == destino) {
            return;
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion invalida de Riesgo: " + this.estado.valorBd()
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

    public Probabilidad getProbabilidad() {
        return probabilidad;
    }

    public Impacto getImpacto() {
        return impacto;
    }

    public NivelRiesgo getNivelDerivado() {
        return nivelDerivado;
    }

    public String getAcciones() {
        return acciones;
    }

    public EstadoRiesgo getEstado() {
        return estado;
    }
}

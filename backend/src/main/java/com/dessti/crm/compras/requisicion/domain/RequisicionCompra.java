package com.dessti.crm.compras.requisicion.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code requisicion_compra} (solicitud interna de
 * Materiales), mapeada sobre la tabla {@code requisicion_compra} de la migracion
 * V28 (Req 30, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V28.</p>
 *
 * <h2>Reglas de dominio (Req 30)</h2>
 * <ul>
 *   <li>{@link #crear} exige al menos una partida (Material + cantidad 1..999999,
 *       Req 30.1) y fija el estado inicial {@link EstadoRequisicionCompra#BORRADOR}
 *       (Req 30.1).</li>
 *   <li>{@link #cambiarEstado} aplica la maquina de estados pura (Req 30.2); toda
 *       transicion no permitida se rechaza con {@link TransicionInvalidaException}
 *       (409) conservando el estado actual.</li>
 *   <li>{@link #vincularOrdenCompra} registra la Orden_Compra generada desde una
 *       requisicion aprobada (Req 30.3); la precondicion "estado aprobada" la aplica
 *       la capa de aplicacion.</li>
 * </ul>
 */
@Entity
@Table(name = "requisicion_compra")
public class RequisicionCompra extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Estado; se persiste como etiqueta ASCII (Req 30.1, 30.2). */
    @Convert(converter = EstadoRequisicionCompraConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoRequisicionCompra estado;

    /**
     * Orden_Compra generada desde esta requisicion cuando estaba aprobada
     * (Req 30.3); {@code null} mientras no se ha generado.
     */
    @Column(name = "orden_compra_id")
    private UUID ordenCompraId;

    /**
     * Partidas de la Requisicion_Compra (relacion uno-a-muchos, hijos del
     * agregado). La FK {@code partida_requisicion.requisicion_compra_id} es NOT NULL
     * con ON DELETE CASCADE en V28; JPA persiste/elimina las partidas junto con la
     * Requisicion_Compra.
     */
    @OneToMany(mappedBy = "requisicionCompra", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartidaRequisicion> partidas = new ArrayList<>();

    protected RequisicionCompra() {
        // Requerido por JPA.
    }

    /**
     * Crea una Requisicion_Compra en estado inicial {@code borrador} con al menos
     * una partida (Material + cantidad, Req 30.1). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param partidas partidas iniciales; al menos 1 (Req 30.1).
     * @param actor    identificador de quien crea, para {@code created_by}/
     *                 {@code updated_by}.
     * @return la Requisicion_Compra lista para persistir, en {@code borrador}.
     * @throws ReglaNegocioException si no se aporta al menos una partida (422,
     *         Req 30.1).
     */
    public static RequisicionCompra crear(List<PartidaRequisicion> partidas, String actor) {
        if (partidas == null || partidas.size() < RequisicionCompraValidaciones.PARTIDAS_MINIMAS) {
            throw new ReglaNegocioException(
                    "La Requisicion_Compra debe tener al menos una Partida_Requisicion.");
        }
        RequisicionCompra requisicion = new RequisicionCompra();
        requisicion.id = UUID.randomUUID();
        requisicion.estado = EstadoRequisicionCompra.BORRADOR;
        requisicion.partidas = new ArrayList<>();
        for (PartidaRequisicion partida : partidas) {
            requisicion.enlazar(partida);
        }
        requisicion.setCreatedBy(actor);
        requisicion.setUpdatedBy(actor);
        return requisicion;
    }

    private void enlazar(PartidaRequisicion partida) {
        if (partida == null) {
            throw new ReglaNegocioException("La partida es obligatoria.");
        }
        partida.asignarRequisicion(this);
        this.partidas.add(partida);
    }

    /**
     * Cambia el estado de la Requisicion_Compra aplicando la maquina de estados pura
     * (Req 30.2). Solo permite las transiciones definidas; toda transicion no
     * permitida —incluida cualquiera que parta de un estado final— se rechaza con
     * {@link TransicionInvalidaException} (409) y el estado actual se conserva sin
     * modificarlo (Req 30.2).
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException       si {@code nuevoEstado} es nulo (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoRequisicionCompra nuevoEstado, String actor) {
        if (nuevoEstado == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + nuevoEstado.valorBd() + "'.");
        }
        this.estado = nuevoEstado;
        this.setUpdatedBy(actor);
    }

    /**
     * Registra la Orden_Compra generada desde esta requisicion (Req 30.3). La
     * precondicion "la requisicion esta aprobada" la verifica la capa de aplicacion
     * antes de invocar este metodo. Es idempotente respecto al estado: solo enlaza
     * el identificador de la Orden generada.
     *
     * @param ordenCompraId identificador de la Orden_Compra generada; obligatorio.
     * @param actor         identificador de quien genera, para {@code updated_by}.
     * @throws ReglaNegocioException si {@code ordenCompraId} es nulo (422).
     */
    public void vincularOrdenCompra(UUID ordenCompraId, String actor) {
        if (ordenCompraId == null) {
            throw new ReglaNegocioException("La Orden_Compra generada es obligatoria.");
        }
        this.ordenCompraId = ordenCompraId;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la Requisicion_Compra esta en estado {@code aprobada} (precondicion
     * para generar una Orden_Compra, Req 30.3).
     *
     * @return {@code true} si la requisicion esta aprobada.
     */
    public boolean estaAprobada() {
        return this.estado == EstadoRequisicionCompra.APROBADA;
    }

    public UUID getId() {
        return id;
    }

    public EstadoRequisicionCompra getEstado() {
        return estado;
    }

    public UUID getOrdenCompraId() {
        return ordenCompraId;
    }

    /**
     * Vista de solo lectura de las partidas de la Requisicion_Compra.
     *
     * @return lista inmutable de partidas.
     */
    public List<PartidaRequisicion> getPartidas() {
        return Collections.unmodifiableList(partidas);
    }
}

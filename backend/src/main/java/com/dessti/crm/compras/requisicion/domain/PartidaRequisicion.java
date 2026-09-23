package com.dessti.crm.compras.requisicion.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code partida_requisicion} (renglon de una
 * {@link RequisicionCompra} con Material y cantidad), mapeada sobre la tabla
 * {@code partida_requisicion} de la migracion V28 (Req 30.1, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V28.</p>
 *
 * <h2>Reglas de dominio (Req 30.1)</h2>
 * <ul>
 *   <li>{@link #crear} valida el Material y la cantidad (entero en [1, 999,999]);
 *       una entrada fuera de rango se rechaza con {@link ReglaNegocioException}
 *       (422).</li>
 * </ul>
 *
 * <p>La partida es una entidad hija del agregado {@link RequisicionCompra}: se crea
 * y se gestiona a traves de la Requisicion_Compra.</p>
 */
@Entity
@Table(name = "partida_requisicion")
public class PartidaRequisicion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Requisicion_Compra a la que pertenece la partida (relacion muchos-a-uno). La
     * FK {@code requisicion_compra_id} es NOT NULL en V28. Se gestiona desde el
     * agregado {@link RequisicionCompra} al agregar la partida.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "requisicion_compra_id", nullable = false, updatable = false)
    private RequisicionCompra requisicionCompra;

    /** Material solicitado por la partida (Req 30.1); obligatorio. */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Cantidad entera en [1, 999,999] (Req 30.1). */
    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    protected PartidaRequisicion() {
        // Requerido por JPA.
    }

    /**
     * Crea una partida validando el Material y la cantidad (Req 30.1). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     * La asociacion con la {@link RequisicionCompra} la establece el agregado al
     * agregar la partida.
     *
     * @param materialId Material solicitado; obligatorio.
     * @param cantidad   cantidad; entero en [1, 999,999].
     * @param actor      identificador de quien crea, para {@code created_by}/
     *                   {@code updated_by}.
     * @return la partida lista para agregar a la Requisicion_Compra.
     * @throws ReglaNegocioException si falta el Material, o la cantidad esta fuera
     *         de rango (422).
     */
    public static PartidaRequisicion crear(UUID materialId, int cantidad, String actor) {
        if (materialId == null) {
            throw new ReglaNegocioException(
                    "La partida de la Requisicion_Compra debe referir un Material.");
        }
        PartidaRequisicion partida = new PartidaRequisicion();
        partida.id = UUID.randomUUID();
        partida.materialId = materialId;
        partida.cantidad = RequisicionCompraValidaciones.validarCantidad(cantidad);
        partida.setCreatedBy(actor);
        partida.setUpdatedBy(actor);
        return partida;
    }

    /**
     * Vincula esta partida a su Requisicion_Compra contenedora. Uso interno del
     * agregado {@link RequisicionCompra}.
     *
     * @param requisicionCompra Requisicion_Compra contenedora; obligatoria.
     */
    void asignarRequisicion(RequisicionCompra requisicionCompra) {
        this.requisicionCompra = requisicionCompra;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public int getCantidad() {
        return cantidad;
    }
}

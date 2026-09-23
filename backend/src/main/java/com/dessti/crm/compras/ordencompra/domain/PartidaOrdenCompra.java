package com.dessti.crm.compras.ordencompra.domain;

import java.math.BigDecimal;
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
 * Entidad JPA de la {@code partida_orden_compra} (renglon de una
 * {@link OrdenCompra} con Material, cantidad, precio unitario y subtotal), mapeada
 * sobre la tabla {@code partida_orden_compra} de la migracion V28 (Req 31.2, 31.3,
 * 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V28.</p>
 *
 * <h2>Reglas de dominio (Req 31.2, 31.3)</h2>
 * <ul>
 *   <li>{@link #crear} valida la cantidad (entero en [1, 999,999]) y el precio
 *       unitario (en [0.01, 999,999,999.99], escala 2 half-up); una entrada fuera
 *       de rango se rechaza con {@link ReglaNegocioException} (422) y no se calcula
 *       subtotal.</li>
 *   <li>El subtotal se calcula como {@code round(cantidad * precio_unitario, 2)}
 *       half-up (Req 31.3).</li>
 * </ul>
 *
 * <p>La partida es una entidad hija del agregado {@link OrdenCompra}: se crea y se
 * gestiona a traves de la Orden_Compra, que mantiene coherente el total.</p>
 */
@Entity
@Table(name = "partida_orden_compra")
public class PartidaOrdenCompra extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Orden_Compra a la que pertenece la partida (relacion muchos-a-uno). La FK
     * {@code orden_compra_id} es NOT NULL en V28. Se gestiona desde el agregado
     * {@link OrdenCompra} al agregar la partida.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_compra_id", nullable = false, updatable = false)
    private OrdenCompra ordenCompra;

    /** Material solicitado por la partida (Req 31.1); obligatorio. */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Cantidad entera en [1, 999,999] (Req 31.2). */
    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** Precio unitario en [0.01, 999,999,999.99]; escala 2 (Req 31.2). */
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    /** Subtotal = round(cantidad * precio_unitario, 2) half-up (Req 31.3). */
    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    protected PartidaOrdenCompra() {
        // Requerido por JPA.
    }

    /**
     * Crea una partida validando el Material, la cantidad y el precio (Req 31.2) y
     * calculando su subtotal (Req 31.3). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). La asociacion con la
     * {@link OrdenCompra} la establece el agregado al agregar la partida.
     *
     * @param materialId     Material solicitado; obligatorio.
     * @param cantidad       cantidad; entero en [1, 999,999].
     * @param precioUnitario precio unitario; en [0.01, 999,999,999.99].
     * @param actor          identificador de quien crea, para {@code created_by}/
     *                       {@code updated_by}.
     * @return la partida lista para agregar a la Orden_Compra, con su subtotal.
     * @throws ReglaNegocioException si falta el Material, o la cantidad/precio
     *         estan fuera de rango (422).
     */
    public static PartidaOrdenCompra crear(UUID materialId, int cantidad,
                                           BigDecimal precioUnitario, String actor) {
        if (materialId == null) {
            throw new ReglaNegocioException("La partida de la Orden_Compra debe referir un Material.");
        }
        PartidaOrdenCompra partida = new PartidaOrdenCompra();
        partida.id = UUID.randomUUID();
        partida.materialId = materialId;
        partida.cantidad = OrdenCompraValidaciones.validarCantidad(cantidad);
        partida.precioUnitario = OrdenCompraValidaciones.validarPrecioUnitario(precioUnitario);
        partida.subtotal = OrdenCompraValidaciones.calcularSubtotalPartida(
                partida.cantidad, partida.precioUnitario);
        partida.setCreatedBy(actor);
        partida.setUpdatedBy(actor);
        return partida;
    }

    /**
     * Vincula esta partida a su Orden_Compra contenedora. Uso interno del agregado
     * {@link OrdenCompra}.
     *
     * @param ordenCompra Orden_Compra contenedora; obligatoria.
     */
    void asignarOrdenCompra(OrdenCompra ordenCompra) {
        this.ordenCompra = ordenCompra;
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

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}

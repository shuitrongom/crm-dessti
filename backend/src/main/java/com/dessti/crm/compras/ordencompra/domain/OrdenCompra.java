package com.dessti.crm.compras.ordencompra.domain;

import java.math.BigDecimal;
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
 * Entidad JPA y raiz del agregado {@code orden_compra} (documento de compra con
 * partidas y total), mapeada sobre la tabla {@code orden_compra} de la migracion
 * V28 (Req 31, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V28. Es analoga a
 * {@code Cotizacion} del modulo comercial-crm (partidas + total half-up + maquina
 * de estados).</p>
 *
 * <h2>Reglas de dominio (Req 31)</h2>
 * <ul>
 *   <li>{@link #crear} exige un Proveedor y entre 1 y 500 partidas (Req 31.1),
 *       fija el estado inicial {@link EstadoOrdenCompra#ABIERTA} (Req 31.4) y
 *       calcula el total como {@code round(Σ subtotales, 2)} half-up (Req 31.3).</li>
 *   <li>{@link #cambiarEstado} aplica la maquina de estados pura (Req 31.5, 31.6);
 *       toda transicion no permitida se rechaza con
 *       {@link TransicionInvalidaException} (409) conservando el estado actual.</li>
 * </ul>
 */
@Entity
@Table(name = "orden_compra")
public class OrdenCompra extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Proveedor existente al que se asocia la Orden_Compra (Req 31.1). Inmutable. */
    @Column(name = "proveedor_id", nullable = false, updatable = false)
    private UUID proveedorId;

    /**
     * Requisicion_Compra de origen cuando la Orden se genero desde una requisicion
     * aprobada (Req 30.3); {@code null} en el alta directa.
     */
    @Column(name = "requisicion_compra_id", updatable = false)
    private UUID requisicionCompraId;

    /** Estado; se persiste como etiqueta ASCII (Req 31.4, 31.5). */
    @Convert(converter = EstadoOrdenCompraConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoOrdenCompra estado;

    /** Total = round(Σ subtotales, 2) half-up (Req 31.3). */
    @Column(name = "total", nullable = false)
    private BigDecimal total;

    /**
     * Partidas de la Orden_Compra (relacion uno-a-muchos, hijos del agregado). La
     * FK {@code partida_orden_compra.orden_compra_id} es NOT NULL con ON DELETE
     * CASCADE en V28; JPA persiste/elimina las partidas junto con la Orden_Compra.
     */
    @OneToMany(mappedBy = "ordenCompra", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartidaOrdenCompra> partidas = new ArrayList<>();

    protected OrdenCompra() {
        // Requerido por JPA.
    }

    /**
     * Crea una Orden_Compra en estado inicial {@code abierta} asociada a un
     * Proveedor existente con entre 1 y 500 partidas (Req 31.1, 31.4), calculando
     * el total como {@code round(Σ subtotales, 2)} half-up (Req 31.3). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     * La existencia del Proveedor la verifica la capa de aplicacion antes de
     * invocar este metodo.
     *
     * @param proveedorId         identificador del Proveedor existente; obligatorio.
     * @param requisicionCompraId Requisicion_Compra de origen; {@code null} en alta
     *                            directa (se fija cuando se genera desde requisicion
     *                            aprobada, Req 30.3).
     * @param partidas            partidas iniciales; entre 1 y 500 (Req 31.1).
     * @param actor               identificador de quien crea, para {@code created_by}/
     *                            {@code updated_by}.
     * @return la Orden_Compra lista para persistir, en {@code abierta} y con total.
     * @throws ReglaNegocioException si falta el Proveedor, o el numero de partidas
     *         esta fuera de [1, 500] (422, Req 31.1).
     */
    public static OrdenCompra crear(UUID proveedorId, UUID requisicionCompraId,
                                    List<PartidaOrdenCompra> partidas, String actor) {
        if (proveedorId == null) {
            throw new ReglaNegocioException("La Orden_Compra debe asociarse a un Proveedor existente.");
        }
        if (partidas == null || partidas.size() < OrdenCompraValidaciones.PARTIDAS_MINIMAS) {
            throw new ReglaNegocioException(
                    "La Orden_Compra debe tener al menos una Partida_Orden_Compra.");
        }
        if (partidas.size() > OrdenCompraValidaciones.PARTIDAS_MAXIMAS) {
            throw new ReglaNegocioException(
                    "La Orden_Compra no puede tener mas de "
                            + OrdenCompraValidaciones.PARTIDAS_MAXIMAS + " partidas.");
        }
        OrdenCompra orden = new OrdenCompra();
        orden.id = UUID.randomUUID();
        orden.proveedorId = proveedorId;
        orden.requisicionCompraId = requisicionCompraId;
        orden.estado = EstadoOrdenCompra.ABIERTA;
        orden.total = BigDecimal.ZERO.setScale(OrdenCompraValidaciones.ESCALA_MONETARIA);
        orden.partidas = new ArrayList<>();
        for (PartidaOrdenCompra partida : partidas) {
            orden.enlazar(partida);
        }
        orden.recalcularTotal();
        orden.setCreatedBy(actor);
        orden.setUpdatedBy(actor);
        return orden;
    }

    private void enlazar(PartidaOrdenCompra partida) {
        if (partida == null) {
            throw new ReglaNegocioException("La partida es obligatoria.");
        }
        partida.asignarOrdenCompra(this);
        this.partidas.add(partida);
    }

    /**
     * Recalcula {@code total} como {@code round(Σ subtotales, 2)} half-up a partir
     * de los subtotales de las partidas (Req 31.3). Como cada subtotal ya esta a
     * escala 2, la suma es exacta; el redondeo final garantiza la escala monetaria
     * de forma idempotente.
     */
    private void recalcularTotal() {
        BigDecimal suma = BigDecimal.ZERO;
        for (PartidaOrdenCompra partida : this.partidas) {
            suma = suma.add(partida.getSubtotal());
        }
        this.total = OrdenCompraValidaciones.normalizarMonto(suma);
    }

    /**
     * Cambia el estado de la Orden_Compra aplicando la maquina de estados pura
     * (Req 31.5, 31.6). Solo permite las transiciones definidas; toda transicion no
     * permitida —incluida cualquiera que parta de un estado final— se rechaza con
     * {@link TransicionInvalidaException} (409) y el estado actual se conserva sin
     * modificarlo (Req 31.6).
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException       si {@code nuevoEstado} es nulo (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoOrdenCompra nuevoEstado, String actor) {
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

    public UUID getId() {
        return id;
    }

    public UUID getProveedorId() {
        return proveedorId;
    }

    public UUID getRequisicionCompraId() {
        return requisicionCompraId;
    }

    public EstadoOrdenCompra getEstado() {
        return estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    /**
     * Vista de solo lectura de las partidas de la Orden_Compra.
     *
     * @return lista inmutable de partidas.
     */
    public List<PartidaOrdenCompra> getPartidas() {
        return Collections.unmodifiableList(partidas);
    }
}

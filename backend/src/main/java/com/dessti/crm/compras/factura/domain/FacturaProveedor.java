package com.dessti.crm.compras.factura.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Entidad JPA de la {@code factura_proveedor} (Factura_Proveedor asociada a una
 * {@code Orden_Compra}, con monto, folio del Proveedor y estado), mapeada sobre la
 * tabla {@code factura_proveedor} de la migracion V29 (Req 33, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V29.</p>
 *
 * <h2>Reglas de dominio (Req 33.1, 33.2, 33.6)</h2>
 * <ul>
 *   <li>{@link #registrar} exige la Orden_Compra, el Proveedor (denormalizado para
 *       el filtro del listado, Req 33.8), un folio no vacio y un monto &gt;= 0
 *       (Req 33.2), fija el estado inicial {@link EstadoFacturaProveedor#REGISTRADA}
 *       (Req 33.1) y normaliza el monto a 2 decimales half-up.</li>
 *   <li>{@link #cambiarEstado} aplica la maquina de estados pura (Req 33.6); toda
 *       transicion no permitida se rechaza con {@link TransicionInvalidaException}
 *       (409) conservando el estado actual.</li>
 * </ul>
 */
@Entity
@Table(name = "factura_proveedor")
public class FacturaProveedor extends TenantScopedEntity {

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Orden_Compra a la que se asocia la Factura_Proveedor (Req 33.1). Inmutable. */
    @Column(name = "orden_compra_id", nullable = false, updatable = false)
    private UUID ordenCompraId;

    /** Proveedor denormalizado (derivado de la Orden_Compra) para el filtro (Req 33.8). */
    @Column(name = "proveedor_id", nullable = false, updatable = false)
    private UUID proveedorId;

    /** Folio de factura del Proveedor (no vacio, Req 33.2). Inmutable. */
    @Column(name = "folio_proveedor", nullable = false, updatable = false, length = 100)
    private String folioProveedor;

    /** Monto de la factura (&gt;= 0, escala 2, Req 33.2). Inmutable. */
    @Column(name = "monto", nullable = false, updatable = false)
    private BigDecimal monto;

    /** Estado; se persiste como etiqueta ASCII (Req 33.1, 33.6). */
    @Convert(converter = EstadoFacturaProveedorConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoFacturaProveedor estado;

    protected FacturaProveedor() {
        // Requerido por JPA.
    }

    /**
     * Registra una Factura_Proveedor en estado inicial {@code registrada} asociada a
     * una Orden_Compra y a su Proveedor, con folio y monto (Req 33.1, 33.2). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     * La existencia de la Orden_Compra (y la derivacion del Proveedor) las verifica
     * la capa de aplicacion antes de invocar este metodo.
     *
     * @param ordenCompraId  Orden_Compra existente asociada; obligatorio (Req 33.1).
     * @param proveedorId    Proveedor de la Orden_Compra (denormalizado); obligatorio.
     * @param folioProveedor folio de factura del Proveedor; no vacio (Req 33.2).
     * @param monto          monto de la factura; &gt;= 0 (Req 33.2).
     * @param actor          identificador de quien registra, para {@code created_by}/
     *                       {@code updated_by}.
     * @return la Factura_Proveedor lista para persistir, en estado {@code registrada}.
     * @throws ReglaNegocioException si falta la Orden_Compra, el Proveedor, el folio
     *         o el monto, o si el monto es negativo (422, Req 33.2).
     */
    public static FacturaProveedor registrar(UUID ordenCompraId, UUID proveedorId,
                                             String folioProveedor, BigDecimal monto, String actor) {
        if (ordenCompraId == null) {
            throw new ReglaNegocioException(
                    "La Factura_Proveedor debe asociarse a una Orden_Compra existente.");
        }
        if (proveedorId == null) {
            throw new ReglaNegocioException(
                    "La Factura_Proveedor debe referir el Proveedor de la Orden_Compra.");
        }
        if (folioProveedor == null || folioProveedor.isBlank()) {
            throw new ReglaNegocioException(
                    "La Factura_Proveedor debe indicar el folio de factura del Proveedor.");
        }
        if (monto == null) {
            throw new ReglaNegocioException("La Factura_Proveedor debe indicar el monto.");
        }
        BigDecimal montoNormalizado = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (montoNormalizado.signum() < 0) {
            throw new ReglaNegocioException("El monto de la Factura_Proveedor no puede ser negativo.");
        }
        FacturaProveedor factura = new FacturaProveedor();
        factura.id = UUID.randomUUID();
        factura.ordenCompraId = ordenCompraId;
        factura.proveedorId = proveedorId;
        factura.folioProveedor = folioProveedor.strip();
        factura.monto = montoNormalizado;
        factura.estado = EstadoFacturaProveedor.REGISTRADA;
        factura.setCreatedBy(actor);
        factura.setUpdatedBy(actor);
        return factura;
    }

    /**
     * Cambia el estado de la Factura_Proveedor aplicando la maquina de estados pura
     * (Req 33.6). Solo permite las transiciones definidas; toda transicion no
     * permitida —incluida cualquiera que parta de un estado final— se rechaza con
     * {@link TransicionInvalidaException} (409) y el estado actual se conserva sin
     * modificarlo.
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException       si {@code nuevoEstado} es nulo (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoFacturaProveedor nuevoEstado, String actor) {
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

    public UUID getOrdenCompraId() {
        return ordenCompraId;
    }

    public UUID getProveedorId() {
        return proveedorId;
    }

    public String getFolioProveedor() {
        return folioProveedor;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public EstadoFacturaProveedor getEstado() {
        return estado;
    }
}

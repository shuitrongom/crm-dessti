package com.dessti.crm.operacion.inventario.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del historial APPEND-ONLY {@code movimiento_inventario} (Req 18.2,
 * 18.4, 18.7): cada instancia registra un movimiento de inventario aplicado a un
 * {@link Material}, con la cantidad, el tipo y el snapshot de las existencias
 * resultantes tras aplicarlo (base del Kardex del Req 60). Mapeada sobre la tabla
 * {@code movimiento_inventario} de la migracion V18.
 *
 * <p><strong>Inmutable por diseno (Req 18.7):</strong> un movimiento es un registro
 * historico; la capa de aplicacion solo lo <em>agrega</em>, nunca lo actualiza ni lo
 * borra. El saldo vivo del Material lo mantiene {@link Material#getExistencias()}
 * (inventario perpetuo, Req 18.2).</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V18.</p>
 */
@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventario extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Material sobre el que se aplico el movimiento (Req 18.2). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Tipo del movimiento; se persiste como etiqueta ASCII (Req 18.2). */
    @Convert(converter = TipoMovimientoInventarioConverter.class)
    @Column(name = "tipo", nullable = false, updatable = false, length = 10)
    private TipoMovimientoInventario tipo;

    /** Cantidad del movimiento (siempre positiva; el signo lo da el {@link #tipo}). */
    @Column(name = "cantidad", nullable = false, updatable = false,
            precision = 18, scale = Material.ESCALA_CANTIDAD)
    private BigDecimal cantidad;

    /** Snapshot de las existencias del Material tras aplicar este movimiento (Req 18.2). */
    @Column(name = "existencias_resultantes", nullable = false, updatable = false,
            precision = 18, scale = Material.ESCALA_CANTIDAD)
    private BigDecimal existenciasResultantes;

    /** Orden_Fabricacion de origen cuando el movimiento es un consumo (Req 18.4); nullable. */
    @Column(name = "orden_fabricacion_id", updatable = false)
    private UUID ordenFabricacionId;

    /** Motivo/nota opcional (por ejemplo la razon de un ajuste). */
    @Column(name = "motivo", updatable = false, length = 500)
    private String motivo;

    protected MovimientoInventario() {
        // Requerido por JPA.
    }

    /**
     * Registra un movimiento de inventario ya aplicado sobre un Material (Req 18.2,
     * 18.4, 18.7). Es una fabrica de historial: recibe la cantidad del movimiento y el
     * snapshot de existencias resultantes que produjo {@link Material#aplicarMovimiento}.
     * El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param materialId              Material afectado; obligatorio.
     * @param tipo                    tipo del movimiento; obligatorio.
     * @param cantidad                cantidad del movimiento (positiva); obligatoria.
     * @param existenciasResultantes  existencias tras aplicar el movimiento; obligatorio.
     * @param ordenFabricacionId      Orden_Fabricacion de origen si es un consumo, o
     *                                {@code null} para movimientos manuales (Req 18.4).
     * @param motivo                  nota opcional del movimiento.
     * @param actor                   identificador de quien registra, para {@code created_by}.
     * @return el movimiento listo para persistir en el historial.
     * @throws ReglaNegocioException si falta el Material, el tipo, la cantidad o el snapshot (422).
     */
    public static MovimientoInventario registrar(UUID materialId, TipoMovimientoInventario tipo,
                                                 BigDecimal cantidad, BigDecimal existenciasResultantes,
                                                 UUID ordenFabricacionId, String motivo, String actor) {
        if (materialId == null) {
            throw new ReglaNegocioException("El Movimiento_Inventario debe referirse a un Material.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de Movimiento_Inventario es obligatorio.");
        }
        if (cantidad == null) {
            throw new ReglaNegocioException("La cantidad del Movimiento_Inventario es obligatoria.");
        }
        if (existenciasResultantes == null) {
            throw new ReglaNegocioException(
                    "El snapshot de existencias resultantes es obligatorio.");
        }
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.id = UUID.randomUUID();
        movimiento.materialId = materialId;
        movimiento.tipo = tipo;
        movimiento.cantidad = cantidad.abs();
        movimiento.existenciasResultantes = existenciasResultantes;
        movimiento.ordenFabricacionId = ordenFabricacionId;
        movimiento.motivo = motivo;
        movimiento.setCreatedBy(actor);
        movimiento.setUpdatedBy(actor);
        return movimiento;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public TipoMovimientoInventario getTipo() {
        return tipo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getExistenciasResultantes() {
        return existenciasResultantes;
    }

    public UUID getOrdenFabricacionId() {
        return ordenFabricacionId;
    }

    public String getMotivo() {
        return motivo;
    }
}

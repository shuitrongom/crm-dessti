package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del Kardex APPEND-ONLY POR ALMACEN {@code movimiento_almacen} (Req 60),
 * mapeada sobre la tabla de la migracion V26. Cada instancia registra un movimiento de
 * inventario en un Almacen (entrada/salida/transferencia/ajuste) con su costo unitario
 * y total, y el snapshot del saldo del (Almacen, Material) tras aplicarlo, base del
 * Kardex cronologico.
 *
 * <p><strong>Inmutable por diseno (Req 60):</strong> un movimiento del Kardex es un
 * registro historico; la capa de aplicacion solo lo <em>agrega</em>, nunca lo actualiza
 * ni lo borra. El saldo vivo por Almacen lo mantiene {@link ExistenciaAlmacen}.</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V26.</p>
 *
 * <p><strong>Alcance:</strong> la tarea 23.1 usa esta entidad en modo SOLO LECTURA para
 * proyectar el Kardex; la fabrica de registro con costeo, lotes y las transferencias
 * entre Almacenes se implementa en la tarea 23.2.</p>
 */
@Entity
@Table(name = "movimiento_almacen")
public class MovimientoAlmacen extends TenantScopedEntity {

    /** Escala decimal de las cantidades, coherente con NUMERIC(18,3) de V26. */
    public static final int ESCALA_CANTIDAD = 3;

    /** Escala decimal de los costos, coherente con NUMERIC(18,4) de V26. */
    public static final int ESCALA_COSTO = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Almacen en el que se aplico el movimiento (Req 60). */
    @Column(name = "almacen_id", nullable = false, updatable = false)
    private UUID almacenId;

    /** Material afectado por el movimiento (Req 60). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Lote afectado cuando el Material controla lotes (Req 60); opcional. */
    @Column(name = "lote_id", updatable = false)
    private UUID loteId;

    /** Tipo del movimiento; se persiste como etiqueta ASCII (Req 60). */
    @Convert(converter = TipoMovimientoAlmacenConverter.class)
    @Column(name = "tipo", nullable = false, updatable = false, length = 22)
    private TipoMovimientoAlmacen tipo;

    /** Cantidad del movimiento (siempre positiva; el sentido lo da el {@link #tipo}). */
    @Column(name = "cantidad", nullable = false, updatable = false,
            precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal cantidad;

    /** Costo unitario del movimiento (Req 60); no negativo. */
    @Column(name = "costo_unitario", nullable = false, updatable = false,
            precision = 18, scale = ESCALA_COSTO)
    private BigDecimal costoUnitario;

    /** Costo total del movimiento (Req 60); no negativo. */
    @Column(name = "costo_total", nullable = false, updatable = false,
            precision = 18, scale = ESCALA_COSTO)
    private BigDecimal costoTotal;

    /** Snapshot de la cantidad en existencia del (Almacen, Material) tras el movimiento. */
    @Column(name = "saldo_cantidad", nullable = false, updatable = false,
            precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal saldoCantidad;

    /** Snapshot del costo total en existencia del (Almacen, Material) tras el movimiento. */
    @Column(name = "saldo_costo_total", nullable = false, updatable = false,
            precision = 18, scale = ESCALA_COSTO)
    private BigDecimal saldoCostoTotal;

    /** Identificador que agrupa las dos patas de una transferencia (Req 60); opcional. */
    @Column(name = "transferencia_id", updatable = false)
    private UUID transferenciaId;

    /** Motivo/nota opcional del movimiento. */
    @Column(name = "motivo", updatable = false, length = 500)
    private String motivo;

    protected MovimientoAlmacen() {
        // Requerido por JPA.
    }

    /**
     * Registra (APPEND-ONLY) una fila del Kardex por Almacen tras aplicar el costeo del
     * movimiento (Req 60.12, tarea 23.2). Todos los valores llegan YA CALCULADOS por el
     * {@link MotorCosteo} y el servicio de aplicacion (cantidad y costeo del movimiento,
     * mas el snapshot del saldo resultante), de modo que esta fabrica solo VALIDA y
     * construye la fila inmutable del Kardex. El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param almacenId       Almacen del movimiento; obligatorio.
     * @param materialId      Material afectado; obligatorio.
     * @param loteId          Lote afectado; opcional ({@code null} si no hay control de lote).
     * @param tipo            tipo del movimiento; obligatorio (Req 60).
     * @param cantidad        cantidad del movimiento; debe ser &gt; 0 (el sentido lo da el tipo).
     * @param costoUnitario   costo unitario del movimiento; &gt;= 0.
     * @param costoTotal      costo total del movimiento; &gt;= 0.
     * @param saldoCantidad   snapshot de la cantidad en existencia tras el movimiento; &gt;= 0.
     * @param saldoCostoTotal snapshot del costo total en existencia tras el movimiento; &gt;= 0.
     * @param transferenciaId identificador de la transferencia que agrupa ambas patas; opcional.
     * @param motivo          nota opcional del movimiento.
     * @param actor           identificador de quien lo registra, para {@code created_by}/{@code updated_by}.
     * @return la fila de Kardex lista para persistir.
     * @throws ReglaNegocioException si faltan datos obligatorios o algun valor es invalido (422).
     */
    public static MovimientoAlmacen registrar(UUID almacenId, UUID materialId, UUID loteId,
                                              TipoMovimientoAlmacen tipo, BigDecimal cantidad,
                                              BigDecimal costoUnitario, BigDecimal costoTotal,
                                              BigDecimal saldoCantidad, BigDecimal saldoCostoTotal,
                                              UUID transferenciaId, String motivo, String actor) {
        if (almacenId == null) {
            throw new ReglaNegocioException("El movimiento debe referirse a un Almacen.");
        }
        if (materialId == null) {
            throw new ReglaNegocioException("El movimiento debe referirse a un Material.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo del movimiento es obligatorio.");
        }
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad del movimiento debe ser mayor que 0.");
        }
        MovimientoAlmacen movimiento = new MovimientoAlmacen();
        movimiento.id = UUID.randomUUID();
        movimiento.almacenId = almacenId;
        movimiento.materialId = materialId;
        movimiento.loteId = loteId;
        movimiento.tipo = tipo;
        movimiento.cantidad = cantidad.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        movimiento.costoUnitario = exigirNoNegativo(costoUnitario, "El costo unitario").setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        movimiento.costoTotal = exigirNoNegativo(costoTotal, "El costo total").setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        movimiento.saldoCantidad = exigirNoNegativo(saldoCantidad, "El saldo de cantidad").setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        movimiento.saldoCostoTotal = exigirNoNegativo(saldoCostoTotal, "El saldo de costo total").setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        movimiento.transferenciaId = transferenciaId;
        movimiento.motivo = (motivo == null || motivo.isBlank()) ? null : motivo.trim();
        movimiento.setCreatedBy(actor);
        movimiento.setUpdatedBy(actor);
        return movimiento;
    }

    private static BigDecimal exigirNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " del movimiento no puede ser negativo.");
        }
        return valor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlmacenId() {
        return almacenId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public UUID getLoteId() {
        return loteId;
    }

    public TipoMovimientoAlmacen getTipo() {
        return tipo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public BigDecimal getCostoTotal() {
        return costoTotal;
    }

    public BigDecimal getSaldoCantidad() {
        return saldoCantidad;
    }

    public BigDecimal getSaldoCostoTotal() {
        return saldoCostoTotal;
    }

    public UUID getTransferenciaId() {
        return transferenciaId;
    }

    public String getMotivo() {
        return motivo;
    }
}

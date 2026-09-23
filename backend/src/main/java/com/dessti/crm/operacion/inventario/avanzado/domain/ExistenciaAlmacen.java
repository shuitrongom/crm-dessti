package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del saldo vivo de existencias de un Material EN un Almacen (Req 60),
 * mapeada sobre la tabla {@code existencia_almacen} de la migracion V26. Mantiene la
 * cantidad y el costo promedio ponderado por (Almacen, Material).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V26.</p>
 *
 * <h2>Alcance (23.1 vs 23.2)</h2>
 * <p>La tarea 23.1 crea el saldo inicial con {@link #paraAlmacenMaterial(UUID, UUID, String)}
 * (cantidad 0, costo promedio 0) y lo lee/lista. La MATEMATICA del costeo (promedio
 * ponderado y consumo PEPS) la aplica el motor de la tarea 23.2, que usara el mutator
 * controlado {@link #aplicarSaldo(BigDecimal, BigDecimal, String)} para fijar el nuevo
 * saldo ya calculado. Aqui no se implementa logica de costeo.</p>
 */
@Entity
@Table(name = "existencia_almacen")
public class ExistenciaAlmacen extends TenantScopedEntity {

    /** Escala decimal de las cantidades, coherente con NUMERIC(18,3) de V26. */
    public static final int ESCALA_CANTIDAD = 3;

    /** Escala decimal de los costos, coherente con NUMERIC(18,4) de V26. */
    public static final int ESCALA_COSTO = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Almacen al que pertenece el saldo (Req 60). */
    @Column(name = "almacen_id", nullable = false, updatable = false)
    private UUID almacenId;

    /** Material al que pertenece el saldo (Req 60). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Cantidad en existencia en el Almacen (Req 60); nunca negativa. */
    @Column(name = "cantidad", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal cantidad;

    /** Costo promedio ponderado por unidad en el Almacen (Req 60); nunca negativo. */
    @Column(name = "costo_promedio", nullable = false, precision = 18, scale = ESCALA_COSTO)
    private BigDecimal costoPromedio;

    protected ExistenciaAlmacen() {
        // Requerido por JPA.
    }

    /**
     * Crea el saldo inicial de un Material en un Almacen con cantidad 0 y costo
     * promedio 0 (Req 60). El {@code tenant_id} lo fija {@link TenantScopedEntity} al
     * persistir (Req 23.4).
     *
     * @param almacenId  Almacen del saldo; obligatorio.
     * @param materialId Material del saldo; obligatorio.
     * @param actor      identificador de quien lo crea, para {@code created_by}/{@code updated_by}.
     * @return el saldo inicial listo para persistir (cantidad 0, costo 0).
     * @throws ReglaNegocioException si falta el Almacen o el Material (422).
     */
    public static ExistenciaAlmacen paraAlmacenMaterial(UUID almacenId, UUID materialId, String actor) {
        if (almacenId == null) {
            throw new ReglaNegocioException("La existencia debe referirse a un Almacen.");
        }
        if (materialId == null) {
            throw new ReglaNegocioException("La existencia debe referirse a un Material.");
        }
        ExistenciaAlmacen existencia = new ExistenciaAlmacen();
        existencia.id = UUID.randomUUID();
        existencia.almacenId = almacenId;
        existencia.materialId = materialId;
        existencia.cantidad = BigDecimal.ZERO.setScale(ESCALA_CANTIDAD);
        existencia.costoPromedio = BigDecimal.ZERO.setScale(ESCALA_COSTO);
        existencia.setCreatedBy(actor);
        existencia.setUpdatedBy(actor);
        return existencia;
    }

    /**
     * Fija el nuevo saldo (cantidad y costo promedio) YA CALCULADO por el motor de
     * costeo (Req 60). Mutator controlado destinado a la tarea 23.2: no realiza aqui
     * ninguna matematica de costeo; solo persiste valores no negativos, redondeados a
     * la escala correspondiente. Publico para que el servicio de aplicacion (paquete application) del submodulo lo invoque tras calcular el saldo con el MotorCosteo.
     *
     * @param cantidad      nueva cantidad en existencia; obligatoria y &gt;= 0.
     * @param costoPromedio nuevo costo promedio por unidad; obligatorio y &gt;= 0.
     * @param actor         identificador de quien aplica el saldo, para {@code updated_by}.
     * @throws ReglaNegocioException si algun valor falta o es negativo (422).
     */
    public void aplicarSaldo(BigDecimal cantidad, BigDecimal costoPromedio, String actor) {
        if (cantidad == null || cantidad.signum() < 0) {
            throw new ReglaNegocioException("La cantidad en existencia no puede ser negativa.");
        }
        if (costoPromedio == null || costoPromedio.signum() < 0) {
            throw new ReglaNegocioException("El costo promedio no puede ser negativo.");
        }
        this.cantidad = cantidad.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        this.costoPromedio = costoPromedio.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        this.setUpdatedBy(actor);
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

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getCostoPromedio() {
        return costoPromedio;
    }
}

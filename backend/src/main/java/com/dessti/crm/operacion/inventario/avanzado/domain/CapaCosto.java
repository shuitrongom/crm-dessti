package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de una capa de costo PEPS (FIFO) de un Material en un Almacen (Req 60),
 * mapeada sobre la tabla {@code capa_costo} de la migracion V26. Cada capa registra la
 * cantidad restante y el costo unitario de una entrada, ordenadas por {@link #getSecuencia()}
 * para consumirlas en orden (primeras entradas, primeras salidas).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V26.</p>
 *
 * <p><strong>Alcance:</strong> la tarea 23.1 define la entidad y su repositorio para que
 * la tarea 23.2 solo agregue la logica. La CREACION de capas al recibir entradas y su
 * CONSUMO ordenado al registrar salidas (matematica PEPS) los implementa la tarea 23.2;
 * aqui no se define fabrica ni mutadores de negocio.</p>
 */
@Entity
@Table(name = "capa_costo")
public class CapaCosto extends TenantScopedEntity {

    /** Escala decimal de las cantidades, coherente con NUMERIC(18,3) de V26. */
    public static final int ESCALA_CANTIDAD = 3;

    /** Escala decimal de los costos, coherente con NUMERIC(18,4) de V26. */
    public static final int ESCALA_COSTO = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Almacen al que pertenece la capa (Req 60). */
    @Column(name = "almacen_id", nullable = false, updatable = false)
    private UUID almacenId;

    /** Material al que pertenece la capa (Req 60). */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Lote de origen de la capa cuando el Material controla lotes (Req 60); opcional. */
    @Column(name = "lote_id", updatable = false)
    private UUID loteId;

    /** Cantidad aun disponible en la capa (Req 60); nunca negativa. La consume 23.2. */
    @Column(name = "cantidad_restante", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal cantidadRestante;

    /** Costo unitario de la capa (Req 60); no negativo. */
    @Column(name = "costo_unitario", nullable = false, updatable = false,
            precision = 18, scale = ESCALA_COSTO)
    private BigDecimal costoUnitario;

    /** Orden de consumo PEPS: menor secuencia se consume primero (Req 60). */
    @Column(name = "secuencia", nullable = false, updatable = false)
    private long secuencia;

    /** Instante de creacion de la capa (desempate/auditoria del costeo). */
    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    protected CapaCosto() {
        // Requerido por JPA.
    }

    /**
     * Crea una capa de costo PEPS al registrar una ENTRADA (Req 60, tarea 23.2). La capa
     * nace con toda su cantidad disponible ({@code cantidadRestante = cantidad}) y su costo
     * unitario historico, con la {@code secuencia} de consumo asignada por el servicio
     * (monotonica por (tenant, Almacen, Material), DECISION 23.2). El {@code tenant_id} lo
     * fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param almacenId     Almacen de la capa; obligatorio.
     * @param materialId    Material de la capa; obligatorio.
     * @param loteId        Lote de origen; opcional ({@code null} si no hay control de lote).
     * @param cantidad      cantidad de la entrada que forma la capa; debe ser &gt; 0.
     * @param costoUnitario costo unitario de la entrada; debe ser &gt;= 0.
     * @param secuencia     orden de consumo PEPS (menor se consume primero); monotonica.
     * @param actor         identificador de quien la crea, para {@code created_by}/{@code updated_by}.
     * @return la capa lista para persistir.
     * @throws ReglaNegocioException si faltan Almacen/Material, o la cantidad/costo son invalidos (422).
     */
    public static CapaCosto crear(UUID almacenId, UUID materialId, UUID loteId,
                                  BigDecimal cantidad, BigDecimal costoUnitario,
                                  long secuencia, String actor) {
        if (almacenId == null) {
            throw new ReglaNegocioException("La capa de costo debe referirse a un Almacen.");
        }
        if (materialId == null) {
            throw new ReglaNegocioException("La capa de costo debe referirse a un Material.");
        }
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad de una capa de costo debe ser mayor que 0.");
        }
        if (costoUnitario == null || costoUnitario.signum() < 0) {
            throw new ReglaNegocioException("El costo unitario de una capa de costo no puede ser negativo.");
        }
        CapaCosto capa = new CapaCosto();
        capa.id = UUID.randomUUID();
        capa.almacenId = almacenId;
        capa.materialId = materialId;
        capa.loteId = loteId;
        capa.cantidadRestante = cantidad.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        capa.costoUnitario = costoUnitario.setScale(ESCALA_COSTO, RoundingMode.HALF_UP);
        capa.secuencia = secuencia;
        capa.creadaEn = Instant.now();
        capa.setCreatedBy(actor);
        capa.setUpdatedBy(actor);
        return capa;
    }

    /**
     * Reduce la cantidad restante de la capa al consumirla en una SALIDA PEPS (Req 60.11,
     * tarea 23.2). Mutator controlado (publico) usado por el servicio para
     * reconciliar las filas JPA con lo que calcula {@link MotorCosteo}: fija el nuevo
     * remanente ya computado (no realiza aqui la matematica del consumo).
     *
     * @param cantidadRestante nuevo remanente de la capa; obligatorio y &gt;= 0.
     * @param actor            identificador de quien la consume, para {@code updated_by}.
     * @throws ReglaNegocioException si el remanente falta o es negativo (422).
     */
    public void reducir(BigDecimal cantidadRestante, String actor) {
        if (cantidadRestante == null || cantidadRestante.signum() < 0) {
            throw new ReglaNegocioException("El remanente de una capa de costo no puede ser negativo.");
        }
        this.cantidadRestante = cantidadRestante.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
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

    public UUID getLoteId() {
        return loteId;
    }

    public BigDecimal getCantidadRestante() {
        return cantidadRestante;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public long getSecuencia() {
        return secuencia;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}

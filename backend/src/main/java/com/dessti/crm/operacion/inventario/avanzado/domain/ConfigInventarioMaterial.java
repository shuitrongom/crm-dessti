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
 * Entidad JPA de la configuracion avanzada de inventario POR Material (Req 60),
 * mapeada sobre la tabla {@code config_inventario_material} de la migracion V26 en
 * relacion 1:1 con {@code material}.
 *
 * <p><strong>Decision de diseno (V26, DECISION 2):</strong> la configuracion avanzada
 * se mantiene en una tabla 1:1 SEPARADA para NO modificar la tabla {@code material}
 * del Req 18 (V18). Asi el inventario base permanece intacto y el avanzado se activa
 * por Material bajo demanda.</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}; el
 * mapeo de columnas coincide <em>exactamente</em> con V26.</p>
 *
 * <h2>Reglas de dominio (Req 60)</h2>
 * <ul>
 *   <li>{@link #predeterminada(UUID, String)} crea la configuracion por defecto de un
 *       Material (metodo {@code promedio}, sin stock maximo, sin control de lote, y
 *       ceros en los parametros del punto de reorden).</li>
 *   <li>{@link #actualizar} valida y aplica los nuevos valores (upsert).</li>
 *   <li>{@link #puntoReorden()} deriva el punto de reorden como funcion pura:
 *       {@code consumo_promedio * tiempo_entrega_dias + stock_seguridad}.</li>
 * </ul>
 */
@Entity
@Table(name = "config_inventario_material")
public class ConfigInventarioMaterial extends TenantScopedEntity {

    /** Escala decimal de las cantidades, coherente con NUMERIC(18,3) de V26. */
    public static final int ESCALA_CANTIDAD = 3;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Material al que aplica la configuracion (Req 60); 1:1. */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Metodo de costeo (Req 60); se persiste como etiqueta ASCII (promedio/peps). */
    @Convert(converter = MetodoCosteoConverter.class)
    @Column(name = "metodo_costeo", nullable = false, length = 10)
    private MetodoCosteo metodoCosteo;

    /** Stock maximo opcional (Req 60): {@code null} = sin tope; si se fija, &gt;= 0. */
    @Column(name = "stock_maximo", precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal stockMaximo;

    /** Control de lotes activado para este Material (Req 60). Uso efectivo en 23.2. */
    @Column(name = "control_lote", nullable = false)
    private boolean controlLote;

    /** Consumo promedio por dia usado en el punto de reorden (Req 60); &gt;= 0. */
    @Column(name = "consumo_promedio", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal consumoPromedio;

    /** Tiempo de entrega en dias usado en el punto de reorden (Req 60); &gt;= 0. */
    @Column(name = "tiempo_entrega_dias", nullable = false)
    private int tiempoEntregaDias;

    /** Stock de seguridad usado en el punto de reorden (Req 60); &gt;= 0. */
    @Column(name = "stock_seguridad", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal stockSeguridad;

    protected ConfigInventarioMaterial() {
        // Requerido por JPA.
    }

    /**
     * Crea la configuracion PREDETERMINADA de un Material (Req 60): metodo de costeo
     * {@code promedio}, sin stock maximo, sin control de lote y ceros en los parametros
     * del punto de reorden. El {@code tenant_id} lo fija {@link TenantScopedEntity} al
     * persistir (Req 23.4).
     *
     * @param materialId Material al que aplica; obligatorio.
     * @param actor      identificador de quien la crea, para {@code created_by}/{@code updated_by}.
     * @return la configuracion por defecto lista para persistir.
     * @throws ReglaNegocioException si falta el Material (422).
     */
    public static ConfigInventarioMaterial predeterminada(UUID materialId, String actor) {
        if (materialId == null) {
            throw new ReglaNegocioException("La configuracion debe referirse a un Material.");
        }
        ConfigInventarioMaterial config = new ConfigInventarioMaterial();
        config.id = UUID.randomUUID();
        config.materialId = materialId;
        config.metodoCosteo = MetodoCosteo.PROMEDIO;
        config.stockMaximo = null;
        config.controlLote = false;
        config.consumoPromedio = ceroCantidad();
        config.tiempoEntregaDias = 0;
        config.stockSeguridad = ceroCantidad();
        config.setCreatedBy(actor);
        config.setUpdatedBy(actor);
        return config;
    }

    /**
     * Valida y aplica los nuevos valores de configuracion del Material (Req 60). Usado
     * tanto en el alta (sobre {@link #predeterminada}) como en la actualizacion (upsert).
     *
     * @param metodoCosteo      metodo de costeo; obligatorio ({@code promedio}/{@code peps}).
     * @param stockMaximo       stock maximo; {@code null} (sin tope) o &gt;= 0.
     * @param controlLote       {@code true} si el Material controla lotes.
     * @param consumoPromedio   consumo promedio por dia; obligatorio y &gt;= 0.
     * @param tiempoEntregaDias tiempo de entrega en dias; &gt;= 0.
     * @param stockSeguridad    stock de seguridad; obligatorio y &gt;= 0.
     * @param actor             identificador de quien edita, para {@code updated_by}.
     * @throws ReglaNegocioException si algun valor falta o es invalido (422).
     */
    public void actualizar(MetodoCosteo metodoCosteo, BigDecimal stockMaximo, boolean controlLote,
                           BigDecimal consumoPromedio, int tiempoEntregaDias,
                           BigDecimal stockSeguridad, String actor) {
        if (metodoCosteo == null) {
            throw new ReglaNegocioException("El metodo de costeo es obligatorio.");
        }
        this.metodoCosteo = metodoCosteo;
        this.stockMaximo = normalizarStockMaximo(stockMaximo);
        this.controlLote = controlLote;
        this.consumoPromedio = normalizarNoNegativo(consumoPromedio, "El consumo promedio");
        this.tiempoEntregaDias = normalizarDias(tiempoEntregaDias);
        this.stockSeguridad = normalizarNoNegativo(stockSeguridad, "El stock de seguridad");
        this.setUpdatedBy(actor);
    }

    /**
     * Deriva el punto de reorden del Material (Req 60) como funcion PURA:
     * {@code consumo_promedio * tiempo_entrega_dias + stock_seguridad}, redondeado a
     * la escala de cantidades (3 decimales, HALF_UP). No muta estado.
     *
     * @return el punto de reorden calculado (escala 3).
     */
    public BigDecimal puntoReorden() {
        BigDecimal demandaEnTransito = this.consumoPromedio.multiply(BigDecimal.valueOf(tiempoEntregaDias));
        return demandaEnTransito.add(this.stockSeguridad)
                .setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private static BigDecimal normalizarStockMaximo(BigDecimal stockMaximo) {
        if (stockMaximo == null) {
            return null;
        }
        if (stockMaximo.signum() < 0) {
            throw new ReglaNegocioException("El stock maximo debe ser nulo o mayor o igual a 0.");
        }
        return stockMaximo.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizarNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new ReglaNegocioException(etiqueta + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " debe ser mayor o igual a 0.");
        }
        return valor.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    }

    private static int normalizarDias(int tiempoEntregaDias) {
        if (tiempoEntregaDias < 0) {
            throw new ReglaNegocioException(
                    "El tiempo de entrega en dias debe ser mayor o igual a 0.");
        }
        return tiempoEntregaDias;
    }

    private static BigDecimal ceroCantidad() {
        return BigDecimal.ZERO.setScale(ESCALA_CANTIDAD);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public MetodoCosteo getMetodoCosteo() {
        return metodoCosteo;
    }

    public BigDecimal getStockMaximo() {
        return stockMaximo;
    }

    public boolean isControlLote() {
        return controlLote;
    }

    public BigDecimal getConsumoPromedio() {
        return consumoPromedio;
    }

    public int getTiempoEntregaDias() {
        return tiempoEntregaDias;
    }

    public BigDecimal getStockSeguridad() {
        return stockSeguridad;
    }
}

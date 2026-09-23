package com.dessti.crm.presupuestos.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code presupuesto}: un Presupuesto por
 * {@code area} y {@code periodo} con los montos ESTIMADOS de ingresos y/o egresos,
 * mapeado sobre la tabla {@code presupuesto} de la migracion V40 (Req 62, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde la
 * peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las marcas
 * de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V40.</p>
 *
 * <h2>Reglas de dominio (Req 62)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, BigDecimal, BigDecimal, String)} da de alta un
 *       Presupuesto validando que {@code area} y {@code periodo} no esten en blanco
 *       y que los montos no sean negativos, normalizando los montos a escala 2
 *       (Req 62.1). El estimado permite 0 en el lado no presupuestado ("ingresos y/o
 *       egresos").</li>
 *   <li>{@link #actualizar(BigDecimal, BigDecimal, String)} modifica los montos
 *       estimados con las mismas validaciones (Req 62.5). El {@code area}/
 *       {@code periodo} son la identidad de negocio y no cambian tras el alta.</li>
 * </ul>
 *
 * <p>El Presupuesto persiste UNICAMENTE lo estimado; el ejercicio REAL y su variacion
 * se calculan aparte como agregacion de solo lectura (Req 62.2) mediante
 * {@link CalculoVariacionPresupuesto} y el puerto {@code RealEjercidoPort}.</p>
 */
@Entity
@Table(name = "presupuesto")
public class Presupuesto extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Area funcional del presupuesto; identidad de negocio, inmutable (Req 62.1). */
    @Column(name = "area", nullable = false, updatable = false, length = 40)
    private String area;

    /** Periodo 'AAAA-MM' o codigo equivalente; identidad de negocio, inmutable (Req 62.1). */
    @Column(name = "periodo", nullable = false, updatable = false, length = 7)
    private String periodo;

    /** Ingresos estimados (no negativos, escala 2); 0 si no se presupuestan (Req 62.1). */
    @Column(name = "ingresos_estimados", nullable = false)
    private BigDecimal ingresosEstimados;

    /** Egresos estimados (no negativos, escala 2); 0 si no se presupuestan (Req 62.1). */
    @Column(name = "egresos_estimados", nullable = false)
    private BigDecimal egresosEstimados;

    protected Presupuesto() {
        // Requerido por JPA.
    }

    /**
     * Da de alta un Presupuesto para un {@code area} y {@code periodo} con los montos
     * estimados de ingresos y/o egresos (Req 62.1). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). La unicidad
     * {@code (tenant_id, area, periodo)} la garantiza V40 y la pre-verifica la capa
     * de aplicacion.
     *
     * @param area              area funcional; obligatoria (no en blanco).
     * @param periodo           periodo 'AAAA-MM' o codigo; obligatorio (no en blanco).
     * @param ingresosEstimados ingresos estimados; obligatorio y no negativo (0 valido).
     * @param egresosEstimados  egresos estimados; obligatorio y no negativo (0 valido).
     * @param actor             identificador de quien crea, para {@code created_by}/
     *                          {@code updated_by}.
     * @return el Presupuesto listo para persistir.
     * @throws ReglaNegocioException si el area/periodo estan en blanco o algun monto
     *         es nulo o negativo (422).
     */
    public static Presupuesto crear(String area, String periodo,
                                    BigDecimal ingresosEstimados, BigDecimal egresosEstimados,
                                    String actor) {
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.id = UUID.randomUUID();
        presupuesto.area = normalizarTexto(area, "area");
        presupuesto.periodo = normalizarTexto(periodo, "periodo");
        presupuesto.ingresosEstimados = validarMonto(ingresosEstimados, "ingresos estimados");
        presupuesto.egresosEstimados = validarMonto(egresosEstimados, "egresos estimados");
        presupuesto.setCreatedBy(actor);
        presupuesto.setUpdatedBy(actor);
        return presupuesto;
    }

    /**
     * Modifica los montos estimados del Presupuesto (Req 62.5). El {@code area} y el
     * {@code periodo} son la identidad de negocio y no se modifican.
     *
     * @param ingresosEstimados nuevos ingresos estimados; obligatorio y no negativo.
     * @param egresosEstimados  nuevos egresos estimados; obligatorio y no negativo.
     * @param actor             identificador de quien modifica, para {@code updated_by}.
     * @throws ReglaNegocioException si algun monto es nulo o negativo (422).
     */
    public void actualizar(BigDecimal ingresosEstimados, BigDecimal egresosEstimados, String actor) {
        this.ingresosEstimados = validarMonto(ingresosEstimados, "ingresos estimados");
        this.egresosEstimados = validarMonto(egresosEstimados, "egresos estimados");
        this.setUpdatedBy(actor);
    }

    private static String normalizarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El campo '" + campo + "' del Presupuesto es obligatorio.");
        }
        return valor.trim();
    }

    private static BigDecimal validarMonto(BigDecimal monto, String campo) {
        if (monto == null) {
            throw new ReglaNegocioException("El monto de '" + campo + "' del Presupuesto es obligatorio.");
        }
        if (monto.signum() < 0) {
            throw new ReglaNegocioException("El monto de '" + campo + "' del Presupuesto no puede ser negativo.");
        }
        return CalculoVariacionPresupuesto.normalizar(monto);
    }

    public UUID getId() {
        return id;
    }

    public String getArea() {
        return area;
    }

    public String getPeriodo() {
        return periodo;
    }

    public BigDecimal getIngresosEstimados() {
        return ingresosEstimados;
    }

    public BigDecimal getEgresosEstimados() {
        return egresosEstimados;
    }
}

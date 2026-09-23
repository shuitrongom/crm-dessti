package com.dessti.crm.activosfijos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code depreciacion}: el registro de la
 * depreciacion de un periodo mensual por {@link ActivoFijo}, mapeado sobre la tabla
 * {@code depreciacion} de la migracion V37 (Req 44.3, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V37.</p>
 *
 * <h2>Reglas de dominio (Req 44.3)</h2>
 * <ul>
 *   <li>{@link #registrar} valida el Activo_Fijo de origen, el formato del periodo
 *       ({@code 'AAAA-MM'}) y que los montos sean no negativos, y enlaza
 *       opcionalmente la Poliza_Contable generada.</li>
 * </ul>
 *
 * <p>La unicidad de una depreciacion por {@code (activo_fijo, periodo)} la impone la
 * BD (UNIQUE de V37); la aplicacion la pre-verifica y captura la violacion del
 * indice como segunda capa de defensa.</p>
 */
@Entity
@Table(name = "depreciacion")
public class Depreciacion extends TenantScopedEntity {

    /** Formato admitido del periodo mensual: {@code 'AAAA-MM'} (coincide con V37). */
    private static final Pattern PATRON_PERIODO = Pattern.compile("^[0-9]{4}-[0-9]{2}$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Activo_Fijo depreciado; obligatorio (Req 44.3). Inmutable. */
    @Column(name = "activo_fijo_id", nullable = false, updatable = false)
    private UUID activoFijoId;

    /** Periodo mensual {@code 'AAAA-MM'}; obligatorio (Req 44.3). Inmutable. */
    @Column(name = "periodo", nullable = false, length = 7, updatable = false)
    private String periodo;

    /** Monto de depreciacion aplicado en el periodo; no negativo (Req 44.3). Inmutable. */
    @Column(name = "monto", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal monto;

    /** Depreciacion acumulada del Activo_Fijo tras aplicar este periodo. Inmutable. */
    @Column(name = "depreciacion_acumulada_resultante", nullable = false,
            precision = 18, scale = 2, updatable = false)
    private BigDecimal depreciacionAcumuladaResultante;

    /** Poliza_Contable generada por esta depreciacion (Req 38.2); opcional. */
    @Column(name = "poliza_contable_id")
    private UUID polizaContableId;

    /** Instante de registro (UTC). Inmutable. */
    @Column(name = "registrada_en", nullable = false, updatable = false)
    private Instant registradaEn;

    protected Depreciacion() {
        // Requerido por JPA.
    }

    /**
     * Registra la depreciacion de un periodo por Activo_Fijo (Req 44.3). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param activoFijoId                    Activo_Fijo depreciado; obligatorio.
     * @param periodo                         periodo mensual {@code 'AAAA-MM'}; obligatorio.
     * @param monto                           monto aplicado en el periodo; no negativo.
     * @param depreciacionAcumuladaResultante acumulada tras aplicar este periodo; no negativa.
     * @param polizaContableId                Poliza_Contable generada; {@code null} si no se genero.
     * @param actor                           identificador de quien registra (auditoria).
     * @return la Depreciacion lista para persistir.
     * @throws ReglaNegocioException si algun dato falta o es invalido (422).
     */
    public static Depreciacion registrar(UUID activoFijoId, String periodo, BigDecimal monto,
                                         BigDecimal depreciacionAcumuladaResultante,
                                         UUID polizaContableId, String actor) {
        if (activoFijoId == null) {
            throw new ReglaNegocioException("La depreciacion debe asociarse a un Activo_Fijo.");
        }
        if (periodo == null || periodo.isBlank()) {
            throw new ReglaNegocioException("La depreciacion debe indicar el periodo.");
        }
        String periodoNorm = periodo.strip();
        if (!PATRON_PERIODO.matcher(periodoNorm).matches()) {
            throw new ReglaNegocioException(
                    "El periodo de la depreciacion debe tener el formato 'AAAA-MM'.");
        }
        if (monto == null || monto.signum() < 0) {
            throw new ReglaNegocioException(
                    "El monto de la depreciacion no puede ser negativo.");
        }
        if (depreciacionAcumuladaResultante == null
                || depreciacionAcumuladaResultante.signum() < 0) {
            throw new ReglaNegocioException(
                    "La depreciacion acumulada resultante no puede ser negativa.");
        }

        Depreciacion depreciacion = new Depreciacion();
        depreciacion.id = UUID.randomUUID();
        depreciacion.activoFijoId = activoFijoId;
        depreciacion.periodo = periodoNorm;
        depreciacion.monto = monto.setScale(2, RoundingMode.HALF_UP);
        depreciacion.depreciacionAcumuladaResultante =
                depreciacionAcumuladaResultante.setScale(2, RoundingMode.HALF_UP);
        depreciacion.polizaContableId = polizaContableId;
        depreciacion.registradaEn = Instant.now();
        depreciacion.setCreatedBy(actor);
        depreciacion.setUpdatedBy(actor);
        return depreciacion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActivoFijoId() {
        return activoFijoId;
    }

    public String getPeriodo() {
        return periodo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public BigDecimal getDepreciacionAcumuladaResultante() {
        return depreciacionAcumuladaResultante;
    }

    public UUID getPolizaContableId() {
        return polizaContableId;
    }

    public Instant getRegistradaEn() {
        return registradaEn;
    }
}

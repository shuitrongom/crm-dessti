package com.dessti.crm.contabilidad.polizas.domain;

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
 * Entidad JPA de un renglon de una {@link PolizaContable}: un cargo <strong>o</strong>
 * un abono a una {@link CuentaContable} (Req 38.2, 38.3), mapeada sobre la tabla
 * {@code movimiento_poliza} de la migracion V33. Un renglon nunca lleva cargo y
 * abono a la vez ni ambos en cero (regla cargo XOR abono, CHECK en V33).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V33.</p>
 *
 * <p><strong>Inmutabilidad (Req 38.5):</strong> los importes de un renglon no se
 * modifican una vez creado; una correccion se registra como una poliza de reverso.</p>
 */
@Entity
@Table(name = "movimiento_poliza")
public class MovimientoPoliza extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V33). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Poliza a la que pertenece el renglon (Req 38.2). Inmutable. */
    @Column(name = "poliza_contable_id", nullable = false, updatable = false)
    private UUID polizaContableId;

    /** Cuenta_Contable afectada por el renglon (Req 38.2). Inmutable. */
    @Column(name = "cuenta_contable_id", nullable = false, updatable = false)
    private UUID cuentaContableId;

    /** Importe del cargo; {@code >= 0}. Es {@code 0} si el renglon es un abono. */
    @Column(name = "cargo", nullable = false, updatable = false)
    private BigDecimal cargo;

    /** Importe del abono; {@code >= 0}. Es {@code 0} si el renglon es un cargo. */
    @Column(name = "abono", nullable = false, updatable = false)
    private BigDecimal abono;

    protected MovimientoPoliza() {
        // Requerido por JPA.
    }

    /**
     * Crea un renglon de cargo a una Cuenta_Contable (Req 38.2).
     *
     * @param cuentaContableId Cuenta_Contable afectada; obligatoria.
     * @param importe          importe del cargo; obligatorio y estrictamente positivo.
     * @param actor            identificador de quien registra (auditoria).
     * @return el renglon de cargo (abono en cero).
     * @throws ReglaNegocioException si falta la cuenta o el importe no es positivo (422).
     */
    public static MovimientoPoliza cargo(UUID cuentaContableId, BigDecimal importe, String actor) {
        return crear(cuentaContableId, importe, true, actor);
    }

    /**
     * Crea un renglon de abono a una Cuenta_Contable (Req 38.2).
     *
     * @param cuentaContableId Cuenta_Contable afectada; obligatoria.
     * @param importe          importe del abono; obligatorio y estrictamente positivo.
     * @param actor            identificador de quien registra (auditoria).
     * @return el renglon de abono (cargo en cero).
     * @throws ReglaNegocioException si falta la cuenta o el importe no es positivo (422).
     */
    public static MovimientoPoliza abono(UUID cuentaContableId, BigDecimal importe, String actor) {
        return crear(cuentaContableId, importe, false, actor);
    }

    private static MovimientoPoliza crear(UUID cuentaContableId, BigDecimal importe,
                                          boolean esCargo, String actor) {
        if (cuentaContableId == null) {
            throw new ReglaNegocioException(
                    "El renglon de la Poliza_Contable debe referenciar una Cuenta_Contable.");
        }
        if (importe == null || importe.signum() <= 0) {
            throw new ReglaNegocioException(
                    "El importe de un renglon de la Poliza_Contable debe ser positivo.");
        }
        BigDecimal escalado = importe.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        MovimientoPoliza movimiento = new MovimientoPoliza();
        movimiento.id = UUID.randomUUID();
        movimiento.cuentaContableId = cuentaContableId;
        movimiento.cargo = esCargo ? escalado : cero;
        movimiento.abono = esCargo ? cero : escalado;
        movimiento.setCreatedBy(actor);
        movimiento.setUpdatedBy(actor);
        return movimiento;
    }

    /**
     * Vincula el renglon a su poliza; lo invoca {@link PolizaContable} al agregarlo.
     *
     * @param polizaContableId identificador de la poliza contenedora.
     */
    void asignarPoliza(UUID polizaContableId) {
        this.polizaContableId = polizaContableId;
    }

    /**
     * Indica si el renglon es un cargo (cargo {@code > 0}).
     *
     * @return {@code true} si es un cargo.
     */
    public boolean esCargo() {
        return cargo != null && cargo.signum() > 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPolizaContableId() {
        return polizaContableId;
    }

    public UUID getCuentaContableId() {
        return cuentaContableId;
    }

    public BigDecimal getCargo() {
        return cargo;
    }

    public BigDecimal getAbono() {
        return abono;
    }
}

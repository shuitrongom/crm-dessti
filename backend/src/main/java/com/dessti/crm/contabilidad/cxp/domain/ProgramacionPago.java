package com.dessti.crm.contabilidad.cxp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code programacion_pago}: el calendario de pago
 * (fecha programada y monto) de una {@link CuentaPorPagar} (Req 42.2), mapeada sobre
 * la tabla {@code programacion_pago} de la migracion V33.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V33.</p>
 *
 * <p>La bandera {@link #aplicada} indica si el pago programado ya se ejecuto y se
 * aplico a la CxP (Req 42.3). La aplicacion del pago (que disminuye el saldo de la
 * CxP acotado por el saldo) la ejecuta el servicio sobre la {@link CuentaPorPagar};
 * aqui solo se marca la programacion como aplicada.</p>
 */
@Entity
@Table(name = "programacion_pago")
public class ProgramacionPago extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V33). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta_Por_Pagar a la que corresponde la programacion (Req 42.2). Inmutable. */
    @Column(name = "cuenta_por_pagar_id", nullable = false, updatable = false)
    private UUID cuentaPorPagarId;

    /** Fecha programada del pago (Req 42.2). Inmutable. */
    @Column(name = "fecha_programada", nullable = false, updatable = false)
    private LocalDate fechaProgramada;

    /** Monto programado del pago; estrictamente positivo (Req 42.2). Inmutable. */
    @Column(name = "monto", nullable = false, updatable = false)
    private BigDecimal monto;

    /** Indica si el pago programado ya se aplico a la CxP (Req 42.3). */
    @Column(name = "aplicada", nullable = false)
    private boolean aplicada;

    protected ProgramacionPago() {
        // Requerido por JPA.
    }

    /**
     * Crea una Programacion_Pago para una Cuenta_Por_Pagar con fecha y monto
     * (Req 42.2), inicialmente sin aplicar.
     *
     * @param cuentaPorPagarId Cuenta_Por_Pagar de origen; obligatoria.
     * @param fechaProgramada  fecha programada del pago; obligatoria.
     * @param monto            monto programado; obligatorio y estrictamente positivo.
     * @param actor            identificador de quien crea (auditoria).
     * @return la Programacion_Pago lista para persistir, sin aplicar.
     * @throws ReglaNegocioException si faltan datos o el monto no es positivo (422).
     */
    public static ProgramacionPago crear(UUID cuentaPorPagarId, LocalDate fechaProgramada,
                                         BigDecimal monto, String actor) {
        if (cuentaPorPagarId == null) {
            throw new ReglaNegocioException(
                    "La Programacion_Pago debe referenciar una Cuenta_Por_Pagar.");
        }
        if (fechaProgramada == null) {
            throw new ReglaNegocioException("La Programacion_Pago debe indicar la fecha programada.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto de la Programacion_Pago debe ser positivo.");
        }
        ProgramacionPago programacion = new ProgramacionPago();
        programacion.id = UUID.randomUUID();
        programacion.cuentaPorPagarId = cuentaPorPagarId;
        programacion.fechaProgramada = fechaProgramada;
        programacion.monto = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        programacion.aplicada = false;
        programacion.setCreatedBy(actor);
        programacion.setUpdatedBy(actor);
        return programacion;
    }

    /**
     * Marca la programacion como aplicada tras ejecutarse el pago sobre la CxP
     * (Req 42.3).
     *
     * @param actor identificador de quien aplica (auditoria).
     */
    public void marcarAplicada(String actor) {
        this.aplicada = true;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaPorPagarId() {
        return cuentaPorPagarId;
    }

    public LocalDate getFechaProgramada() {
        return fechaProgramada;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public boolean isAplicada() {
        return aplicada;
    }
}

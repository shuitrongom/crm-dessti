package com.dessti.crm.contabilidad.cxc.domain;

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
 * Entidad JPA que vincula un {@link PagoCliente} con la {@link CuentaPorCobrar}
 * (y su Factura) a la que se aplico, registrando el monto aplicado (Req 36.2). Un
 * Pago_Cliente puede tener varias aplicaciones (una por Factura). Se mapea sobre la
 * tabla {@code aplicacion_pago} de la migracion V31.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V31.</p>
 */
@Entity
@Table(name = "aplicacion_pago")
public class AplicacionPago extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V31). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Pago_Cliente del que proviene la aplicacion (Req 36.2). */
    @Column(name = "pago_cliente_id", nullable = false, updatable = false)
    private UUID pagoClienteId;

    /** Cuenta_Por_Cobrar a la que se aplico el monto (Req 36.2). */
    @Column(name = "cuenta_por_cobrar_id", nullable = false, updatable = false)
    private UUID cuentaPorCobrarId;

    /** Factura asociada a la CxC, denormalizada para trazabilidad (Req 36.2). */
    @Column(name = "factura_id", nullable = false, updatable = false)
    private UUID facturaId;

    /** Monto aplicado a esta Factura; estrictamente positivo (Req 36.2). */
    @Column(name = "monto_aplicado", nullable = false, updatable = false)
    private BigDecimal montoAplicado;

    protected AplicacionPago() {
        // Requerido por JPA.
    }

    /**
     * Crea una aplicacion de un Pago_Cliente a una Cuenta_Por_Cobrar por el monto
     * indicado (Req 36.2).
     *
     * @param pagoClienteId     Pago_Cliente de origen; obligatorio.
     * @param cuentaPorCobrarId CxC a la que se aplica; obligatoria.
     * @param facturaId         Factura asociada a la CxC; obligatoria.
     * @param montoAplicado     monto aplicado; obligatorio y positivo.
     * @param actor             identificador de quien aplica (auditoria).
     * @return la aplicacion lista para persistir.
     * @throws ReglaNegocioException si faltan datos o el monto no es positivo (422).
     */
    public static AplicacionPago de(UUID pagoClienteId, UUID cuentaPorCobrarId, UUID facturaId,
                                    BigDecimal montoAplicado, String actor) {
        if (pagoClienteId == null || cuentaPorCobrarId == null || facturaId == null) {
            throw new ReglaNegocioException(
                    "La aplicacion de pago debe referenciar el Pago_Cliente, la CxC y la Factura.");
        }
        if (montoAplicado == null || montoAplicado.signum() <= 0) {
            throw new ReglaNegocioException("El monto aplicado debe ser positivo.");
        }
        AplicacionPago aplicacion = new AplicacionPago();
        aplicacion.id = UUID.randomUUID();
        aplicacion.pagoClienteId = pagoClienteId;
        aplicacion.cuentaPorCobrarId = cuentaPorCobrarId;
        aplicacion.facturaId = facturaId;
        aplicacion.montoAplicado = montoAplicado.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        aplicacion.setCreatedBy(actor);
        aplicacion.setUpdatedBy(actor);
        return aplicacion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPagoClienteId() {
        return pagoClienteId;
    }

    public UUID getCuentaPorCobrarId() {
        return cuentaPorCobrarId;
    }

    public UUID getFacturaId() {
        return facturaId;
    }

    public BigDecimal getMontoAplicado() {
        return montoAplicado;
    }
}

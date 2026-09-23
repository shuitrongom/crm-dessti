package com.dessti.crm.contabilidad.cxc.domain;

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
 * Entidad JPA y raiz del agregado {@code pago_cliente}: un pago recibido de un
 * Cliente que se aplica a una o varias Facturas via {@link AplicacionPago}
 * (Req 36.2). Se mapea sobre la tabla {@code pago_cliente} de la migracion V31.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V31.</p>
 *
 * <h2>Complemento de Pago (Req 36.4)</h2>
 * <p>Cuando el pago es una parcialidad o diferido, se genera y timbra un
 * Complemento_Pago (CFDI tipo pago) via el PAC (Req 35). El folio fiscal, el sello
 * y la fecha de timbrado del complemento se registran en esta entidad mediante
 * {@link #registrarComplemento(UUID, String, Instant, String)}.</p>
 */
@Entity
@Table(name = "pago_cliente")
public class PagoCliente extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V31). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cliente que realiza el pago, para el filtro del listado (Req 36.6). */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Monto total del pago recibido; estrictamente positivo (Req 36.2). */
    @Column(name = "monto", nullable = false, updatable = false)
    private BigDecimal monto;

    /** Fecha/hora del pago en UTC (Req 36.2). */
    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private Instant fechaPago;

    /** Forma de pago (clave del catalogo del SAT); opcional. */
    @Column(name = "forma_pago")
    private String formaPago;

    /** Indica si el pago es una parcialidad/diferido que exige Complemento_Pago (Req 36.4). */
    @Column(name = "es_parcialidad", nullable = false)
    private boolean esParcialidad;

    /** Folio_Fiscal (UUID del SAT) del Complemento_Pago al timbrar (Req 36.4). */
    @Column(name = "complemento_folio_fiscal")
    private UUID complementoFolioFiscal;

    /** Sello digital del SAT del Complemento_Pago devuelto por el PAC (Req 36.4). */
    @Column(name = "complemento_sello")
    private String complementoSello;

    /** Fecha/hora del Timbrado del Complemento_Pago en UTC (Req 36.4). */
    @Column(name = "complemento_fecha_timbrado")
    private Instant complementoFechaTimbrado;

    protected PagoCliente() {
        // Requerido por JPA.
    }

    /**
     * Registra un pago de Cliente (Req 36.2). El monto debe ser estrictamente
     * positivo; la aplicacion del pago a las Facturas se modela con
     * {@link AplicacionPago}.
     *
     * @param clienteId     Cliente que paga; obligatorio.
     * @param monto         monto total del pago; obligatorio y positivo.
     * @param formaPago     forma de pago (clave SAT); opcional.
     * @param esParcialidad {@code true} si es parcialidad/diferido (Req 36.4).
     * @param actor         identificador de quien registra (auditoria).
     * @return el Pago_Cliente listo para persistir.
     * @throws ReglaNegocioException si faltan datos o el monto no es positivo (422).
     */
    public static PagoCliente registrar(UUID clienteId, BigDecimal monto, String formaPago,
                                        boolean esParcialidad, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException("El Pago_Cliente debe registrar el Cliente.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto del Pago_Cliente debe ser positivo.");
        }
        PagoCliente pago = new PagoCliente();
        pago.id = UUID.randomUUID();
        pago.clienteId = clienteId;
        pago.monto = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        pago.fechaPago = Instant.now();
        pago.formaPago = (formaPago == null || formaPago.isBlank()) ? null : formaPago.strip();
        pago.esParcialidad = esParcialidad;
        pago.setCreatedBy(actor);
        pago.setUpdatedBy(actor);
        return pago;
    }

    /**
     * Registra los datos del Complemento_Pago (CFDI tipo pago) timbrado por el PAC
     * (Req 36.4).
     *
     * @param folioFiscal   Folio_Fiscal (UUID del SAT); obligatorio.
     * @param sello         sello digital del SAT; obligatorio.
     * @param fechaTimbrado fecha/hora del Timbrado (UTC); obligatoria.
     * @param actor         identificador de quien timbra (auditoria).
     * @throws ReglaNegocioException si faltan datos del Timbrado (422).
     */
    public void registrarComplemento(UUID folioFiscal, String sello, Instant fechaTimbrado,
                                     String actor) {
        if (folioFiscal == null || sello == null || sello.isBlank() || fechaTimbrado == null) {
            throw new ReglaNegocioException(
                    "El Complemento_Pago requiere Folio_Fiscal, sello y fecha del PAC.");
        }
        this.complementoFolioFiscal = folioFiscal;
        this.complementoSello = sello;
        this.complementoFechaTimbrado = fechaTimbrado;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Instant getFechaPago() {
        return fechaPago;
    }

    public String getFormaPago() {
        return formaPago;
    }

    public boolean isEsParcialidad() {
        return esParcialidad;
    }

    public UUID getComplementoFolioFiscal() {
        return complementoFolioFiscal;
    }

    public String getComplementoSello() {
        return complementoSello;
    }

    public Instant getComplementoFechaTimbrado() {
        return complementoFechaTimbrado;
    }
}

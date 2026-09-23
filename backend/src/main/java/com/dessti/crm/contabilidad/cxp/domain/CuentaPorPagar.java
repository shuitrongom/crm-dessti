package com.dessti.crm.contabilidad.cxp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code cuenta_por_pagar} (CxP): el saldo pendiente
 * de pago a un Proveedor derivado de una Factura_Proveedor conciliada, mapeada sobre
 * la tabla {@code cuenta_por_pagar} de la migracion V33 (Req 42, 23). Es la imagen
 * espejo de {@code CuentaPorCobrar}.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el {@code TenantContext} al persistir,
 * nunca desde la peticion, Req 23.4), {@code version} (concurrencia optimista,
 * Req 49) y las marcas de auditoria. El mapeo de columnas coincide
 * <em>exactamente</em> con V33.</p>
 *
 * <h2>Reglas de dominio (Req 42)</h2>
 * <ul>
 *   <li>{@link #paraFacturaProveedor} crea la CxP en
 *       {@link EstadoCuentaPorPagar#PENDIENTE} con {@code saldo == total} cuando la
 *       Factura_Proveedor queda conciliada (Req 42.1).</li>
 *   <li>{@link #aplicarPago(BigDecimal, String)} es la <strong>regla de negocio
 *       PURA</strong> que aplica un pago acotado por el saldo
 *       (<strong>Property 15</strong>, Req 42.3, 42.4): si el monto no es positivo,
 *       o excede el saldo pendiente, se rechaza con {@link ReglaNegocioException}
 *       <em>sin mutar</em> el saldo (se conserva, informando el excedente); en otro
 *       caso disminuye el saldo exactamente por el monto (escala 2, HALF_UP) y
 *       deriva el estado.</li>
 *   <li>{@link #cancelar(String)} transita la CxP a
 *       {@link EstadoCuentaPorPagar#CANCELADA} (estado final).</li>
 * </ul>
 *
 * <p>El estado se <strong>deriva del saldo</strong> tras cada aplicacion y la
 * transicion se valida por la maquina de estados pura {@link EstadoCuentaPorPagar}
 * (409 si es invalida), preservando la coherencia estado&harr;saldo. El indicador
 * {@link #quedoLiquidada()} permite a la aplicacion detectar cuando el saldo llego a
 * 0 para marcar la Factura_Proveedor como pagada (Req 42.3).</p>
 */
@Entity
@Table(name = "cuenta_por_pagar")
public class CuentaPorPagar extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V33). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Factura_Proveedor conciliada de origen; una CxP por factura (UNIQUE en V33) (Req 42.1). */
    @Column(name = "factura_proveedor_id", nullable = false, updatable = false)
    private UUID facturaProveedorId;

    /** Proveedor al que se le paga, para el filtro del listado y el aging (Req 42.5, 42.6). */
    @Column(name = "proveedor_id", nullable = false, updatable = false)
    private UUID proveedorId;

    /** Total original de la Factura_Proveedor (base del saldo inicial); no negativo (Req 42.1). */
    @Column(name = "total", nullable = false, updatable = false)
    private BigDecimal total;

    /** Saldo pendiente de pago; nunca negativo, acotado a [0, total] (Req 42.3, 42.4). */
    @Column(name = "saldo", nullable = false)
    private BigDecimal saldo;

    /** Estado; se persiste como etiqueta ASCII (Req 42). */
    @Convert(converter = EstadoCuentaPorPagarConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoCuentaPorPagar estado;

    /** Fecha/hora de registro de la CxP en UTC (Req 42.1). */
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    /** Fecha de vencimiento para el aging (Req 42.5); {@code null} si no aplica. */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    protected CuentaPorPagar() {
        // Requerido por JPA.
    }

    /**
     * Registra una Cuenta_Por_Pagar para una Factura_Proveedor conciliada (Req 42.1).
     * El saldo inicial es el total de la factura y el estado es
     * {@link EstadoCuentaPorPagar#PENDIENTE}.
     *
     * @param facturaProveedorId Factura_Proveedor conciliada de origen; obligatoria.
     * @param proveedorId        Proveedor al que se le paga; obligatorio.
     * @param total              total de la factura; obligatorio y no negativo.
     * @param actor              identificador de quien registra (auditoria).
     * @return la CxP lista para persistir, en {@code pendiente} con {@code saldo == total}.
     * @throws ReglaNegocioException si faltan datos o el total es negativo (422).
     */
    public static CuentaPorPagar paraFacturaProveedor(UUID facturaProveedorId, UUID proveedorId,
                                                      BigDecimal total, String actor) {
        if (facturaProveedorId == null) {
            throw new ReglaNegocioException(
                    "La Cuenta_Por_Pagar debe referenciar una Factura_Proveedor.");
        }
        if (proveedorId == null) {
            throw new ReglaNegocioException("La Cuenta_Por_Pagar debe registrar el Proveedor.");
        }
        if (total == null || total.signum() < 0) {
            throw new ReglaNegocioException(
                    "El total de la Cuenta_Por_Pagar no puede ser negativo.");
        }
        BigDecimal totalEscalado = total.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        CuentaPorPagar cxp = new CuentaPorPagar();
        cxp.id = UUID.randomUUID();
        cxp.facturaProveedorId = facturaProveedorId;
        cxp.proveedorId = proveedorId;
        cxp.total = totalEscalado;
        cxp.saldo = totalEscalado;
        cxp.estado = EstadoCuentaPorPagar.PENDIENTE;
        cxp.fechaRegistro = Instant.now();
        cxp.setCreatedBy(actor);
        cxp.setUpdatedBy(actor);
        return cxp;
    }

    /**
     * Aplica un pago a esta CxP, <strong>acotado por el saldo</strong>
     * (<strong>Property 15</strong>, Req 42.3, 42.4). Regla de negocio PURA:
     *
     * <ul>
     *   <li>Si {@code monto} es nulo o no es positivo ({@code <= 0}), se rechaza con
     *       {@link ReglaNegocioException} y el saldo se conserva.</li>
     *   <li>Si {@code monto} <strong>excede</strong> el saldo pendiente, se rechaza
     *       con {@link ReglaNegocioException} cuyo mensaje contiene
     *       "excede el saldo" e informa el excedente, y el saldo se conserva
     *       <em>sin mutar</em> (Req 42.4).</li>
     *   <li>En otro caso, el saldo disminuye exactamente por el monto (escala 2,
     *       HALF_UP), nunca queda negativo, y el estado se deriva del nuevo saldo
     *       (Req 42.3).</li>
     * </ul>
     *
     * @param monto monto a aplicar; positivo y {@code <= saldo}.
     * @param actor identificador de quien aplica (auditoria).
     * @throws ReglaNegocioException       si el monto no es positivo o excede el
     *                                     saldo (422; el saldo se conserva).
     * @throws TransicionInvalidaException si la CxP esta en un estado final (409).
     */
    public void aplicarPago(BigDecimal monto, String actor) {
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException(
                    "El monto a aplicar a la Cuenta_Por_Pagar debe ser positivo.");
        }
        BigDecimal montoAplicado = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (montoAplicado.compareTo(this.saldo) > 0) {
            BigDecimal excedente = montoAplicado.subtract(this.saldo)
                    .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
            throw new ReglaNegocioException(
                    "el monto aplicado (" + montoAplicado.toPlainString()
                            + ") excede el saldo pendiente (" + this.saldo.toPlainString()
                            + ") de la Cuenta_Por_Pagar en " + excedente.toPlainString() + ".");
        }
        BigDecimal nuevoSaldo = this.saldo.subtract(montoAplicado)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (nuevoSaldo.signum() < 0) {
            // Defensa: no debe ocurrir porque el monto se acota por el saldo.
            nuevoSaldo = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        }
        EstadoCuentaPorPagar destino = derivarEstado(nuevoSaldo);
        if (destino != this.estado) {
            transitar(destino);
        }
        this.saldo = nuevoSaldo;
        this.setUpdatedBy(actor);
    }

    /**
     * Cancela la Cuenta_Por_Pagar y transita a
     * {@link EstadoCuentaPorPagar#CANCELADA} (estado final).
     *
     * @param actor identificador de quien cancela (auditoria).
     * @throws TransicionInvalidaException si la CxP ya esta en un estado final (409).
     */
    public void cancelar(String actor) {
        transitar(EstadoCuentaPorPagar.CANCELADA);
        this.setUpdatedBy(actor);
    }

    /**
     * Fija la fecha de vencimiento usada por el aging (Req 42.5).
     *
     * @param fechaVencimiento fecha de vencimiento; puede ser {@code null}.
     * @param actor            identificador de quien la establece (auditoria).
     */
    public void asignarVencimiento(LocalDate fechaVencimiento, String actor) {
        this.fechaVencimiento = fechaVencimiento;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la CxP quedo liquidada, es decir, el saldo llego a 0 y el estado es
     * {@link EstadoCuentaPorPagar#PAGADA} (Req 42.3). La aplicacion usa este
     * indicador para marcar la Factura_Proveedor asociada como pagada.
     *
     * @return {@code true} si la CxP esta liquidada.
     */
    public boolean quedoLiquidada() {
        return this.estado == EstadoCuentaPorPagar.PAGADA;
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Deriva el estado a partir del saldo (Req 42.3): {@code saldo == 0 -> pagada};
     * {@code 0 < saldo < total -> parcial}; {@code saldo == total -> pendiente}.
     */
    private EstadoCuentaPorPagar derivarEstado(BigDecimal nuevoSaldo) {
        if (nuevoSaldo.signum() == 0) {
            return EstadoCuentaPorPagar.PAGADA;
        }
        if (nuevoSaldo.compareTo(this.total) < 0) {
            return EstadoCuentaPorPagar.PARCIAL;
        }
        return EstadoCuentaPorPagar.PENDIENTE;
    }

    private void transitar(EstadoCuentaPorPagar destino) {
        if (destino == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + destino.valorBd() + "'.");
        }
        this.estado = destino;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFacturaProveedorId() {
        return facturaProveedorId;
    }

    public UUID getProveedorId() {
        return proveedorId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public EstadoCuentaPorPagar getEstado() {
        return estado;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }
}

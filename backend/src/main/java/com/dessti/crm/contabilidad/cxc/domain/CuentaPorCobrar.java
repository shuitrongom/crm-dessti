package com.dessti.crm.contabilidad.cxc.domain;

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
 * Entidad JPA y raiz del agregado {@code cuenta_por_cobrar} (CxC): el saldo
 * pendiente de cobro de una Factura timbrada, mapeada sobre la tabla
 * {@code cuenta_por_cobrar} de la migracion V31 (Req 36, 37, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el {@code TenantContext} al persistir,
 * nunca desde la peticion, Req 23.4), {@code version} (concurrencia optimista,
 * Req 49) y las marcas de auditoria. El mapeo de columnas coincide
 * <em>exactamente</em> con V31.</p>
 *
 * <h2>Reglas de dominio (Req 36, 37)</h2>
 * <ul>
 *   <li>{@link #paraFactura} crea la CxC en {@link EstadoCuentaPorCobrar#PENDIENTE}
 *       con {@code saldo == total} cuando la Factura queda timbrada (Req 36.1).</li>
 *   <li>{@link #aplicarPago(BigDecimal, String)} es la <strong>regla de negocio
 *       PURA</strong> que aplica un pago de Cliente acotado por el saldo
 *       (<strong>Property 13</strong>, Req 36.2, 36.3): si el monto no es positivo,
 *       o excede el saldo pendiente, se rechaza con {@link ReglaNegocioException}
 *       <em>sin mutar</em> el saldo (se conserva); en otro caso disminuye el saldo
 *       exactamente por el monto (escala 2, HALF_UP) y deriva el estado.</li>
 *   <li>{@link #disminuir(BigDecimal, String)} reduce el saldo por una Nota de
 *       Credito emitida (Req 37.1); el tope ya lo aplica el submodulo de notas de
 *       credito, pero aqui se acota defensivamente a {@code [0, saldo]}.</li>
 *   <li>{@link #cancelar(String)} transita la CxC a
 *       {@link EstadoCuentaPorCobrar#CANCELADA} (estado final).</li>
 * </ul>
 *
 * <p>El estado se <strong>deriva del saldo</strong> tras cada aplicacion y la
 * transicion se valida por la maquina de estados pura {@link EstadoCuentaPorCobrar}
 * (409 si es invalida), preservando la coherencia estado&harr;saldo.</p>
 */
@Entity
@Table(name = "cuenta_por_cobrar")
public class CuentaPorCobrar extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V31). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Factura timbrada de origen; una CxC por Factura (UNIQUE en V31) (Req 36.1). */
    @Column(name = "factura_id", nullable = false, updatable = false)
    private UUID facturaId;

    /** Cliente al que se le cobra, para el filtro del listado y el aging (Req 36.5, 36.6). */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Total original de la Factura (base del saldo inicial); no negativo (Req 36.1). */
    @Column(name = "total", nullable = false, updatable = false)
    private BigDecimal total;

    /** Saldo pendiente de cobro; nunca negativo, acotado a [0, total] (Req 36.2, 36.3). */
    @Column(name = "saldo", nullable = false)
    private BigDecimal saldo;

    /** Estado; se persiste como etiqueta ASCII (Req 36). */
    @Convert(converter = EstadoCuentaPorCobrarConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoCuentaPorCobrar estado;

    /** Fecha/hora de emision (registro) de la CxC en UTC (Req 36.1). */
    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private Instant fechaEmision;

    /** Fecha de vencimiento para el aging (Req 36.5); {@code null} si no aplica. */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    protected CuentaPorCobrar() {
        // Requerido por JPA.
    }

    /**
     * Registra una Cuenta_Por_Cobrar para una Factura timbrada (Req 36.1). El saldo
     * inicial es el total de la Factura y el estado es
     * {@link EstadoCuentaPorCobrar#PENDIENTE}.
     *
     * @param facturaId Factura timbrada de origen; obligatoria.
     * @param clienteId Cliente al que se le cobra; obligatorio.
     * @param total     total de la Factura; obligatorio y no negativo.
     * @param actor     identificador de quien registra (auditoria).
     * @return la CxC lista para persistir, en {@code pendiente} con {@code saldo == total}.
     * @throws ReglaNegocioException si faltan datos o el total es negativo (422).
     */
    public static CuentaPorCobrar paraFactura(UUID facturaId, UUID clienteId,
                                              BigDecimal total, String actor) {
        if (facturaId == null) {
            throw new ReglaNegocioException("La Cuenta_Por_Cobrar debe referenciar una Factura.");
        }
        if (clienteId == null) {
            throw new ReglaNegocioException("La Cuenta_Por_Cobrar debe registrar el Cliente.");
        }
        if (total == null || total.signum() < 0) {
            throw new ReglaNegocioException(
                    "El total de la Cuenta_Por_Cobrar no puede ser negativo.");
        }
        BigDecimal totalEscalado = total.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        CuentaPorCobrar cxc = new CuentaPorCobrar();
        cxc.id = UUID.randomUUID();
        cxc.facturaId = facturaId;
        cxc.clienteId = clienteId;
        cxc.total = totalEscalado;
        cxc.saldo = totalEscalado;
        cxc.estado = EstadoCuentaPorCobrar.PENDIENTE;
        cxc.fechaEmision = Instant.now();
        cxc.setCreatedBy(actor);
        cxc.setUpdatedBy(actor);
        return cxc;
    }

    /**
     * Aplica un pago de Cliente a esta CxC, <strong>acotado por el saldo</strong>
     * (<strong>Property 13</strong>, Req 36.2, 36.3). Regla de negocio PURA:
     *
     * <ul>
     *   <li>Si {@code monto} es nulo o no es positivo ({@code <= 0}), se rechaza con
     *       {@link ReglaNegocioException} y el saldo se conserva.</li>
     *   <li>Si {@code monto} <strong>excede</strong> el saldo pendiente, se rechaza
     *       con {@link ReglaNegocioException} cuyo mensaje contiene
     *       "excede el saldo" y el saldo se conserva <em>sin mutar</em> (Req 36.3).</li>
     *   <li>En otro caso, el saldo disminuye exactamente por el monto (escala 2,
     *       HALF_UP), nunca queda negativo, y el estado se deriva del nuevo saldo
     *       (Req 36.2).</li>
     * </ul>
     *
     * @param monto monto a aplicar; positivo y {@code <= saldo}.
     * @param actor identificador de quien aplica (auditoria).
     * @throws ReglaNegocioException       si el monto no es positivo o excede el
     *                                     saldo (422; el saldo se conserva).
     * @throws TransicionInvalidaException si la CxC esta en un estado final (409).
     */
    public void aplicarPago(BigDecimal monto, String actor) {
        BigDecimal montoAplicado = normalizarPositivo(monto);
        if (montoAplicado.compareTo(this.saldo) > 0) {
            throw new ReglaNegocioException(
                    "el monto aplicado (" + montoAplicado.toPlainString()
                            + ") excede el saldo pendiente (" + this.saldo.toPlainString()
                            + ") de la Cuenta_Por_Cobrar.");
        }
        aplicarDisminucion(montoAplicado, actor);
    }

    /**
     * Disminuye el saldo por el monto de una Nota de Credito emitida sobre la
     * Factura (Req 37.1). El tope contra el saldo lo aplica el submodulo de notas de
     * credito; aqui se acota defensivamente a {@code [0, saldo]} de modo que el
     * saldo nunca quede negativo (si el monto supera el saldo, se descuenta el saldo
     * restante completo). El estado se deriva del nuevo saldo.
     *
     * @param monto monto de la Nota de Credito; positivo.
     * @param actor identificador de quien emite la nota (auditoria).
     * @throws ReglaNegocioException       si el monto no es positivo (422).
     * @throws TransicionInvalidaException si la CxC esta en un estado final (409).
     */
    public void disminuir(BigDecimal monto, String actor) {
        BigDecimal montoNota = normalizarPositivo(monto);
        BigDecimal descuento = montoNota.min(this.saldo);
        aplicarDisminucion(descuento, actor);
    }

    /**
     * Cancela la Cuenta_Por_Cobrar y transita a
     * {@link EstadoCuentaPorCobrar#CANCELADA} (estado final).
     *
     * @param actor identificador de quien cancela (auditoria).
     * @throws TransicionInvalidaException si la CxC ya esta en un estado final (409).
     */
    public void cancelar(String actor) {
        transitar(EstadoCuentaPorCobrar.CANCELADA);
        this.setUpdatedBy(actor);
    }

    /**
     * Fija la fecha de vencimiento usada por el aging (Req 36.5).
     *
     * @param fechaVencimiento fecha de vencimiento; puede ser {@code null}.
     * @param actor            identificador de quien la establece (auditoria).
     */
    public void asignarVencimiento(LocalDate fechaVencimiento, String actor) {
        this.fechaVencimiento = fechaVencimiento;
        this.setUpdatedBy(actor);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private BigDecimal normalizarPositivo(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException(
                    "El monto a aplicar a la Cuenta_Por_Cobrar debe ser positivo.");
        }
        return monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Resta {@code monto} del saldo (asumido ya validado en {@code [0, saldo]}),
     * deriva el estado destino del nuevo saldo y lo transita por la maquina pura.
     */
    private void aplicarDisminucion(BigDecimal monto, String actor) {
        BigDecimal nuevoSaldo = this.saldo.subtract(monto)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (nuevoSaldo.signum() < 0) {
            // Defensa: no debe ocurrir porque los llamadores acotan el monto por el saldo.
            nuevoSaldo = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        }
        EstadoCuentaPorCobrar destino = derivarEstado(nuevoSaldo);
        if (destino != this.estado) {
            transitar(destino);
        }
        this.saldo = nuevoSaldo;
        this.setUpdatedBy(actor);
    }

    /**
     * Deriva el estado a partir del saldo (Req 36.2): {@code saldo == 0 -> pagada};
     * {@code 0 < saldo < total -> parcial}; {@code saldo == total -> pendiente}.
     */
    private EstadoCuentaPorCobrar derivarEstado(BigDecimal nuevoSaldo) {
        if (nuevoSaldo.signum() == 0) {
            return EstadoCuentaPorCobrar.PAGADA;
        }
        if (nuevoSaldo.compareTo(this.total) < 0) {
            return EstadoCuentaPorCobrar.PARCIAL;
        }
        return EstadoCuentaPorCobrar.PENDIENTE;
    }

    private void transitar(EstadoCuentaPorCobrar destino) {
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

    public UUID getFacturaId() {
        return facturaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public EstadoCuentaPorCobrar getEstado() {
        return estado;
    }

    public Instant getFechaEmision() {
        return fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }
}

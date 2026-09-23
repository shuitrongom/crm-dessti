package com.dessti.crm.tesoreria.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code movimiento_bancario}: una linea de un
 * {@link EstadoCuentaBancario} (deposito o retiro) que se empareja con una
 * Poliza_Contable o un Pago durante la conciliacion (Req 43.2, 43.3), mapeada sobre
 * la tabla {@code movimiento_bancario} de la migracion V35.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V35.</p>
 *
 * <h2>Monto con signo (decision de diseno)</h2>
 * <p>El {@code monto} se persiste <strong>con signo</strong>: positivo para un
 * deposito y negativo para un retiro. El emparejamiento por monto (Req 43.3) compara
 * su valor absoluto contra el monto de la partida contable candidata
 * ({@link ReglasConciliacion#montoCoincide(BigDecimal, BigDecimal)}).</p>
 *
 * <h2>Estado de conciliacion (Req 43.3, 43.4)</h2>
 * <p>Nace {@link EstadoConciliacionMovimiento#PENDIENTE} al importar. Al conciliar,
 * pasa a {@link EstadoConciliacionMovimiento#CONCILIADO} enlazando la partida
 * emparejada ({@link #polizaContableId} o {@link #pagoId}), o a
 * {@link EstadoConciliacionMovimiento#EXCEPCION} si no encontro coincidencia
 * (revision manual, Req 43.4).</p>
 */
@Entity
@Table(name = "movimiento_bancario")
public class MovimientoBancario extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V35). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Estado_Cuenta_Bancario al que pertenece el movimiento (Req 43.2). Inmutable. */
    @Column(name = "estado_cuenta_bancario_id", nullable = false, updatable = false)
    private UUID estadoCuentaBancarioId;

    /** Cuenta_Bancaria del movimiento (desnormalizada para el listado, Req 43.6). Inmutable. */
    @Column(name = "cuenta_bancaria_id", nullable = false, updatable = false)
    private UUID cuentaBancariaId;

    /** Fecha del movimiento bancario (Req 43.2). Inmutable. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    /** Monto con signo: + deposito / - retiro (Req 43.2). Inmutable. */
    @Column(name = "monto", nullable = false, updatable = false)
    private BigDecimal monto;

    /** Referencia del movimiento (folio, clave de rastreo); opcional. Inmutable. */
    @Column(name = "referencia", updatable = false, length = 120)
    private String referencia;

    /** Descripcion/concepto del movimiento; opcional. Inmutable. */
    @Column(name = "descripcion", updatable = false, length = 300)
    private String descripcion;

    /** Estado de conciliacion del movimiento (Req 43.3, 43.4). */
    @Column(name = "estado_conciliacion", nullable = false, length = 12)
    @Convert(converter = EstadoConciliacionMovimientoConverter.class)
    private EstadoConciliacionMovimiento estadoConciliacion;

    /** Poliza_Contable emparejada al conciliar (Req 43.3); {@code null} si no aplica. */
    @Column(name = "poliza_contable_id")
    private UUID polizaContableId;

    /** Pago emparejado al conciliar (Req 43.3); {@code null} si no aplica. */
    @Column(name = "pago_id")
    private UUID pagoId;

    protected MovimientoBancario() {
        // Requerido por JPA.
    }

    /**
     * Crea un Movimiento_Bancario importado, en estado
     * {@link EstadoConciliacionMovimiento#PENDIENTE} (Req 43.2).
     *
     * @param estadoCuentaBancarioId Estado_Cuenta_Bancario contenedor; obligatorio.
     * @param cuentaBancariaId       Cuenta_Bancaria del movimiento; obligatorio.
     * @param fecha                  fecha del movimiento; obligatoria.
     * @param monto                  monto con signo (+ deposito / - retiro); obligatorio
     *                               y distinto de cero.
     * @param referencia             referencia del movimiento; opcional.
     * @param descripcion            descripcion/concepto; opcional.
     * @param actor                  identificador de quien importa (auditoria).
     * @return el Movimiento_Bancario listo para persistir, pendiente de conciliar.
     * @throws ReglaNegocioException si faltan datos o el monto es cero/nulo (422).
     */
    public static MovimientoBancario importado(UUID estadoCuentaBancarioId, UUID cuentaBancariaId,
                                               LocalDate fecha, BigDecimal monto, String referencia,
                                               String descripcion, String actor) {
        if (estadoCuentaBancarioId == null) {
            throw new ReglaNegocioException(
                    "El Movimiento_Bancario debe pertenecer a un Estado_Cuenta_Bancario.");
        }
        if (cuentaBancariaId == null) {
            throw new ReglaNegocioException(
                    "El Movimiento_Bancario debe referenciar una Cuenta_Bancaria.");
        }
        if (fecha == null) {
            throw new ReglaNegocioException("El Movimiento_Bancario debe indicar la fecha.");
        }
        if (monto == null || monto.signum() == 0) {
            throw new ReglaNegocioException(
                    "El monto del Movimiento_Bancario debe ser distinto de cero.");
        }
        MovimientoBancario movimiento = new MovimientoBancario();
        movimiento.id = UUID.randomUUID();
        movimiento.estadoCuentaBancarioId = estadoCuentaBancarioId;
        movimiento.cuentaBancariaId = cuentaBancariaId;
        movimiento.fecha = fecha;
        movimiento.monto = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        movimiento.referencia = (referencia == null || referencia.isBlank())
                ? null : referencia.strip();
        movimiento.descripcion = (descripcion == null || descripcion.isBlank())
                ? null : descripcion.strip();
        movimiento.estadoConciliacion = EstadoConciliacionMovimiento.PENDIENTE;
        movimiento.setCreatedBy(actor);
        movimiento.setUpdatedBy(actor);
        return movimiento;
    }

    /**
     * Marca el movimiento como conciliado con una Poliza_Contable (Req 43.3).
     *
     * @param polizaContableId Poliza_Contable emparejada; obligatoria.
     * @param actor            identificador de quien concilia (auditoria).
     * @throws ReglaNegocioException si falta la poliza (422).
     */
    public void conciliarConPoliza(UUID polizaContableId, String actor) {
        if (polizaContableId == null) {
            throw new ReglaNegocioException(
                    "La conciliacion con Poliza_Contable requiere el identificador de la poliza.");
        }
        this.polizaContableId = polizaContableId;
        this.pagoId = null;
        this.estadoConciliacion = EstadoConciliacionMovimiento.CONCILIADO;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca el movimiento como conciliado con un Pago (Req 43.3).
     *
     * @param pagoId Pago emparejado; obligatorio.
     * @param actor  identificador de quien concilia (auditoria).
     * @throws ReglaNegocioException si falta el pago (422).
     */
    public void conciliarConPago(UUID pagoId, String actor) {
        if (pagoId == null) {
            throw new ReglaNegocioException(
                    "La conciliacion con Pago requiere el identificador del pago.");
        }
        this.pagoId = pagoId;
        this.polizaContableId = null;
        this.estadoConciliacion = EstadoConciliacionMovimiento.CONCILIADO;
        this.setUpdatedBy(actor);
    }

    /**
     * Marca el movimiento como excepcion, sin coincidencia, para revision manual
     * (Req 43.4).
     *
     * @param actor identificador de quien concilia (auditoria).
     */
    public void marcarExcepcion(String actor) {
        this.polizaContableId = null;
        this.pagoId = null;
        this.estadoConciliacion = EstadoConciliacionMovimiento.EXCEPCION;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEstadoCuentaBancarioId() {
        return estadoCuentaBancarioId;
    }

    public UUID getCuentaBancariaId() {
        return cuentaBancariaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public EstadoConciliacionMovimiento getEstadoConciliacion() {
        return estadoConciliacion;
    }

    public UUID getPolizaContableId() {
        return polizaContableId;
    }

    public UUID getPagoId() {
        return pagoId;
    }
}

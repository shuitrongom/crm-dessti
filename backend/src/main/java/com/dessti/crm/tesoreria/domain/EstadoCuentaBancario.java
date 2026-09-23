package com.dessti.crm.tesoreria.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code estado_cuenta_bancario}: un estado de
 * cuenta importado de una {@link CuentaBancaria} para un periodo, con su saldo
 * inicial/final y sus {@link MovimientoBancario} (Req 43.2), mapeada sobre la tabla
 * {@code estado_cuenta_bancario} de la migracion V35. Contiene sus movimientos como
 * parte del agregado.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V35.</p>
 *
 * <h2>Importacion (Req 43.2)</h2>
 * <p>{@link #importar(UUID, LocalDate, LocalDate, BigDecimal, BigDecimal, String)}
 * crea el estado de cuenta con su periodo y saldos; los movimientos se agregan con
 * {@link #agregarMovimiento(LocalDate, BigDecimal, String, String, String)}, que los
 * crea en estado {@link EstadoConciliacionMovimiento#PENDIENTE}. El
 * {@code saldo_final} es el saldo bancario que la conciliacion compara contra el
 * saldo contable (Req 43.5).</p>
 */
@Entity
@Table(name = "estado_cuenta_bancario")
public class EstadoCuentaBancario extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V35). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta_Bancaria del estado de cuenta (Req 43.2). Inmutable. */
    @Column(name = "cuenta_bancaria_id", nullable = false, updatable = false)
    private UUID cuentaBancariaId;

    /** Inicio del periodo del estado de cuenta (Req 43.2). Inmutable. */
    @Column(name = "periodo_inicio", nullable = false, updatable = false)
    private LocalDate periodoInicio;

    /** Fin del periodo del estado de cuenta (Req 43.2). Inmutable. */
    @Column(name = "periodo_fin", nullable = false, updatable = false)
    private LocalDate periodoFin;

    /** Saldo inicial del periodo (Req 43.2). Inmutable. */
    @Column(name = "saldo_inicial", nullable = false, updatable = false)
    private BigDecimal saldoInicial;

    /** Saldo final del periodo; saldo bancario de la conciliacion (Req 43.5). Inmutable. */
    @Column(name = "saldo_final", nullable = false, updatable = false)
    private BigDecimal saldoFinal;

    /** Movimientos del estado de cuenta; parte del agregado (Req 43.2). */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "estado_cuenta_bancario_id", nullable = false, insertable = false, updatable = false)
    private List<MovimientoBancario> movimientos = new ArrayList<>();

    protected EstadoCuentaBancario() {
        // Requerido por JPA.
    }

    /**
     * Crea un Estado_Cuenta_Bancario para un periodo, sin movimientos aun (Req 43.2).
     * Los movimientos se agregan luego con
     * {@link #agregarMovimiento(LocalDate, BigDecimal, String, String, String)}.
     *
     * @param cuentaBancariaId Cuenta_Bancaria del estado de cuenta; obligatorio.
     * @param periodoInicio    inicio del periodo; obligatorio.
     * @param periodoFin       fin del periodo; obligatorio y no anterior al inicio.
     * @param saldoInicial     saldo inicial del periodo; obligatorio.
     * @param saldoFinal       saldo final del periodo (saldo bancario); obligatorio.
     * @param actor            identificador de quien importa (auditoria).
     * @return el Estado_Cuenta_Bancario listo para agregar movimientos y persistir.
     * @throws ReglaNegocioException si faltan datos o el periodo es invalido (422).
     */
    public static EstadoCuentaBancario importar(UUID cuentaBancariaId, LocalDate periodoInicio,
                                                LocalDate periodoFin, BigDecimal saldoInicial,
                                                BigDecimal saldoFinal, String actor) {
        if (cuentaBancariaId == null) {
            throw new ReglaNegocioException(
                    "El Estado_Cuenta_Bancario debe referenciar una Cuenta_Bancaria.");
        }
        if (periodoInicio == null || periodoFin == null) {
            throw new ReglaNegocioException(
                    "El Estado_Cuenta_Bancario debe indicar el periodo (inicio y fin).");
        }
        if (periodoFin.isBefore(periodoInicio)) {
            throw new ReglaNegocioException(
                    "El fin del periodo del Estado_Cuenta_Bancario no puede ser anterior al inicio.");
        }
        if (saldoInicial == null || saldoFinal == null) {
            throw new ReglaNegocioException(
                    "El Estado_Cuenta_Bancario debe indicar el saldo inicial y el final.");
        }
        EstadoCuentaBancario estado = new EstadoCuentaBancario();
        estado.id = UUID.randomUUID();
        estado.cuentaBancariaId = cuentaBancariaId;
        estado.periodoInicio = periodoInicio;
        estado.periodoFin = periodoFin;
        estado.saldoInicial = saldoInicial.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        estado.saldoFinal = saldoFinal.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        estado.setCreatedBy(actor);
        estado.setUpdatedBy(actor);
        return estado;
    }

    /**
     * Agrega un Movimiento_Bancario importado (estado
     * {@link EstadoConciliacionMovimiento#PENDIENTE}) al estado de cuenta (Req 43.2).
     *
     * @param fecha       fecha del movimiento; obligatoria.
     * @param monto       monto con signo (+ deposito / - retiro); obligatorio.
     * @param referencia  referencia del movimiento; opcional.
     * @param descripcion descripcion/concepto; opcional.
     * @param actor       identificador de quien importa (auditoria).
     * @return el movimiento agregado.
     */
    public MovimientoBancario agregarMovimiento(LocalDate fecha, BigDecimal monto,
                                               String referencia, String descripcion,
                                               String actor) {
        MovimientoBancario movimiento = MovimientoBancario.importado(
                this.id, this.cuentaBancariaId, fecha, monto, referencia, descripcion, actor);
        this.movimientos.add(movimiento);
        return movimiento;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaBancariaId() {
        return cuentaBancariaId;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public LocalDate getPeriodoFin() {
        return periodoFin;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public BigDecimal getSaldoFinal() {
        return saldoFinal;
    }

    /**
     * Movimientos del estado de cuenta, como vista de solo lectura (Req 43.2).
     *
     * @return la lista inmutable de movimientos.
     */
    public List<MovimientoBancario> getMovimientos() {
        return Collections.unmodifiableList(movimientos);
    }
}

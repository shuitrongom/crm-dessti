package com.dessti.crm.tesoreria.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code conciliacion_bancaria}: el resultado de
 * conciliar un {@link EstadoCuentaBancario} contra la contabilidad (Req 43.5),
 * mapeada sobre la tabla {@code conciliacion_bancaria} de la migracion V35.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V35.</p>
 *
 * <h2>Completitud (Req 43.5; Property 18)</h2>
 * <p>La conciliacion registra el saldo bancario, el saldo contable y su
 * {@link #diferencia}. Se marca {@link EstadoConciliacionBancaria#COMPLETA}
 * <strong>solo</strong> cuando la diferencia es cero y no quedan movimientos en
 * excepcion; en otro caso queda {@link EstadoConciliacionBancaria#EN_PROCESO}. La
 * decision la toma la funcion PURA {@link ReglasConciliacion#esCompleta(BigDecimal,
 * int)}, que {@link #registrar} usa al construir la conciliacion.</p>
 */
@Entity
@Table(name = "conciliacion_bancaria")
public class ConciliacionBancaria extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V35). */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta_Bancaria conciliada (Req 43.5). Inmutable. */
    @Column(name = "cuenta_bancaria_id", nullable = false, updatable = false)
    private UUID cuentaBancariaId;

    /** Estado_Cuenta_Bancario conciliado (Req 43.5). Inmutable. */
    @Column(name = "estado_cuenta_bancario_id", nullable = false, updatable = false)
    private UUID estadoCuentaBancarioId;

    /** Saldo bancario (saldo final del estado de cuenta) (Req 43.5). Inmutable. */
    @Column(name = "saldo_bancario", nullable = false, updatable = false)
    private BigDecimal saldoBancario;

    /** Saldo contable derivado de las partidas conciliadas (Req 43.5). Inmutable. */
    @Column(name = "saldo_contable", nullable = false, updatable = false)
    private BigDecimal saldoContable;

    /** Diferencia {@code saldo_bancario - saldo_contable} (Req 43.5). Inmutable. */
    @Column(name = "diferencia", nullable = false, updatable = false)
    private BigDecimal diferencia;

    /** Estado de la conciliacion (en_proceso / completa) (Req 43.5). */
    @Column(name = "estado", nullable = false, length = 12)
    @Convert(converter = EstadoConciliacionBancariaConverter.class)
    private EstadoConciliacionBancaria estado;

    /** Fecha/hora de la conciliacion en UTC (Req 43.7). Inmutable. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    protected ConciliacionBancaria() {
        // Requerido por JPA.
    }

    /**
     * Registra el resultado de una conciliacion bancaria (Req 43.5;
     * <strong>Property 18</strong>). Calcula la diferencia PURA
     * ({@link ReglasConciliacion#calcularDiferencia(BigDecimal, BigDecimal)}) y marca
     * la conciliacion {@link EstadoConciliacionBancaria#COMPLETA} solo cuando la
     * diferencia es cero y no quedan movimientos en excepcion
     * ({@link ReglasConciliacion#esCompleta(BigDecimal, int)}); en otro caso
     * {@link EstadoConciliacionBancaria#EN_PROCESO}.
     *
     * @param cuentaBancariaId       Cuenta_Bancaria conciliada; obligatorio.
     * @param estadoCuentaBancarioId Estado_Cuenta_Bancario conciliado; obligatorio.
     * @param saldoBancario          saldo bancario (saldo final del estado de cuenta);
     *                               obligatorio.
     * @param saldoContable          saldo contable derivado de las partidas
     *                               conciliadas; obligatorio.
     * @param movimientosEnExcepcion numero de movimientos en excepcion (>= 0).
     * @param fecha                  instante de la conciliacion (UTC); obligatorio.
     * @param actor                  identificador de quien concilia (auditoria).
     * @return la Conciliacion_Bancaria lista para persistir.
     * @throws ReglaNegocioException si faltan datos obligatorios (422).
     */
    public static ConciliacionBancaria registrar(UUID cuentaBancariaId,
                                                 UUID estadoCuentaBancarioId,
                                                 BigDecimal saldoBancario,
                                                 BigDecimal saldoContable,
                                                 int movimientosEnExcepcion,
                                                 Instant fecha,
                                                 String actor) {
        if (cuentaBancariaId == null || estadoCuentaBancarioId == null) {
            throw new ReglaNegocioException(
                    "La Conciliacion_Bancaria debe referenciar la Cuenta_Bancaria y el "
                            + "Estado_Cuenta_Bancario.");
        }
        if (saldoBancario == null || saldoContable == null) {
            throw new ReglaNegocioException(
                    "La Conciliacion_Bancaria debe indicar el saldo bancario y el contable.");
        }
        if (fecha == null) {
            throw new ReglaNegocioException("La Conciliacion_Bancaria debe indicar la fecha.");
        }
        BigDecimal diferencia = ReglasConciliacion.calcularDiferencia(saldoBancario, saldoContable);
        boolean completa = ReglasConciliacion.esCompleta(diferencia, movimientosEnExcepcion);

        ConciliacionBancaria conciliacion = new ConciliacionBancaria();
        conciliacion.id = UUID.randomUUID();
        conciliacion.cuentaBancariaId = cuentaBancariaId;
        conciliacion.estadoCuentaBancarioId = estadoCuentaBancarioId;
        conciliacion.saldoBancario = saldoBancario.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        conciliacion.saldoContable = saldoContable.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        conciliacion.diferencia = diferencia;
        conciliacion.estado = completa
                ? EstadoConciliacionBancaria.COMPLETA
                : EstadoConciliacionBancaria.EN_PROCESO;
        conciliacion.fecha = fecha;
        conciliacion.setCreatedBy(actor);
        conciliacion.setUpdatedBy(actor);
        return conciliacion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaBancariaId() {
        return cuentaBancariaId;
    }

    public UUID getEstadoCuentaBancarioId() {
        return estadoCuentaBancarioId;
    }

    public BigDecimal getSaldoBancario() {
        return saldoBancario;
    }

    public BigDecimal getSaldoContable() {
        return saldoContable;
    }

    public BigDecimal getDiferencia() {
        return diferencia;
    }

    public EstadoConciliacionBancaria getEstado() {
        return estado;
    }

    public Instant getFecha() {
        return fecha;
    }
}

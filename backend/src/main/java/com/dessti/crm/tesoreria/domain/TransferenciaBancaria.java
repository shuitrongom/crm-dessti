package com.dessti.crm.tesoreria.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code transferencia_bancaria}: el traspaso de
 * fondos entre dos Cuentas_Bancarias de la misma Empresa (Req 43), mapeada sobre la
 * tabla {@code transferencia_bancaria} de la migracion V76.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V76.</p>
 *
 * <h2>Reglas de dominio (regla de negocio PURA)</h2>
 * <ul>
 *   <li>La cuenta de origen y la de destino deben ser <strong>distintas</strong>
 *       (no se admite una transferencia a la misma cuenta).</li>
 *   <li>El monto debe ser <strong>estrictamente positivo</strong> (escala 2,
 *       HALF_UP).</li>
 *   <li>La fecha del traspaso es obligatoria; el concepto es opcional.</li>
 * </ul>
 *
 * <p>La comprobacion de que ambas cuentas existen, son accesibles y comparten moneda
 * la realiza el servicio de aplicacion; el dominio garantiza las invariantes puras
 * (cuentas distintas y monto positivo).</p>
 */
@Entity
@Table(name = "transferencia_bancaria")
public class TransferenciaBancaria extends TenantScopedEntity {

    /** Escala monetaria coherente con NUMERIC(18,2) de V76. */
    public static final int ESCALA_MONETARIA = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta_Bancaria de origen (de donde salen los fondos); inmutable. */
    @Column(name = "cuenta_origen_id", nullable = false, updatable = false)
    private UUID cuentaOrigenId;

    /** Cuenta_Bancaria de destino (a donde entran los fondos); inmutable. */
    @Column(name = "cuenta_destino_id", nullable = false, updatable = false)
    private UUID cuentaDestinoId;

    /** Monto transferido; estrictamente positivo (escala 2). Inmutable. */
    @Column(name = "monto", nullable = false, updatable = false)
    private BigDecimal monto;

    /** Fecha del traspaso; obligatoria. Inmutable. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    /** Concepto descriptivo del traspaso; opcional. Inmutable. */
    @Column(name = "concepto", length = 300, updatable = false)
    private String concepto;

    /** Fecha/hora de registro en UTC. Inmutable. */
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    protected TransferenciaBancaria() {
        // Requerido por JPA.
    }

    /**
     * Registra una Transferencia_Bancaria entre dos Cuentas_Bancarias distintas de la
     * Empresa (regla de negocio PURA).
     *
     * @param cuentaOrigenId  Cuenta_Bancaria de origen; obligatoria.
     * @param cuentaDestinoId Cuenta_Bancaria de destino; obligatoria y distinta del origen.
     * @param monto           monto a transferir; obligatorio y estrictamente positivo.
     * @param fecha           fecha del traspaso; obligatoria.
     * @param concepto        concepto descriptivo; opcional.
     * @param actor           identificador de quien registra (auditoria).
     * @return la Transferencia_Bancaria lista para persistir.
     * @throws ReglaNegocioException si faltan datos, las cuentas coinciden o el monto
     *         no es positivo (422).
     */
    public static TransferenciaBancaria registrar(UUID cuentaOrigenId, UUID cuentaDestinoId,
                                                  BigDecimal monto, LocalDate fecha,
                                                  String concepto, String actor) {
        if (cuentaOrigenId == null || cuentaDestinoId == null) {
            throw new ReglaNegocioException(
                    "La transferencia debe indicar la cuenta de origen y la de destino.");
        }
        if (cuentaOrigenId.equals(cuentaDestinoId)) {
            throw new ReglaNegocioException(
                    "La cuenta de origen y la de destino deben ser distintas.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto de la transferencia debe ser positivo.");
        }
        if (fecha == null) {
            throw new ReglaNegocioException("La transferencia debe indicar la fecha.");
        }
        TransferenciaBancaria transferencia = new TransferenciaBancaria();
        transferencia.id = UUID.randomUUID();
        transferencia.cuentaOrigenId = cuentaOrigenId;
        transferencia.cuentaDestinoId = cuentaDestinoId;
        transferencia.monto = monto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        transferencia.fecha = fecha;
        transferencia.concepto = (concepto == null || concepto.isBlank()) ? null : concepto.strip();
        transferencia.fechaRegistro = Instant.now();
        transferencia.setCreatedBy(actor);
        transferencia.setUpdatedBy(actor);
        return transferencia;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCuentaOrigenId() {
        return cuentaOrigenId;
    }

    public UUID getCuentaDestinoId() {
        return cuentaDestinoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getConcepto() {
        return concepto;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }
}

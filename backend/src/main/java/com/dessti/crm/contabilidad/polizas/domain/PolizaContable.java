package com.dessti.crm.contabilidad.polizas.domain;

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
 * Entidad JPA y raiz del agregado {@code poliza_contable}: un asiento contable
 * (conjunto de cargos y abonos balanceados) que registra el efecto contable de una
 * operacion (Req 38.2, 38.3), mapeada sobre la tabla {@code poliza_contable} de la
 * migracion V33. Contiene sus renglones ({@link MovimientoPoliza}) como parte del
 * agregado.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id}, {@code version} y las marcas de auditoria. El mapeo de
 * columnas coincide <em>exactamente</em> con V33.</p>
 *
 * <h2>Balance PURO (Req 38.3, 38.4; Property 16)</h2>
 * <p>{@link #crear(LocalDate, TipoPoliza, String, String, UUID, List, String)} es la
 * regla de negocio <strong>PURA</strong> que construye una Poliza_Contable a partir
 * de sus renglones: calcula {@code total_cargos} y {@code total_abonos} y
 * <strong>rechaza</strong> con {@link ReglaNegocioException} —informando la
 * diferencia— cuando la suma de cargos no es igual a la suma de abonos, ANTES de
 * construir el objeto (Req 38.4). Asi la propiedad de balance se verifica
 * directamente sobre la fabrica, sin base de datos ni framework.</p>
 *
 * <h2>Inmutabilidad via reverso (Req 38.5)</h2>
 * <p>La Poliza_Contable no expone setters de datos financieros: una vez creada, sus
 * totales y renglones no se modifican. Una correccion se registra como una poliza de
 * reverso mediante {@link #reversar(LocalDate, String)}, que crea una nueva poliza
 * espejo (intercambiando cargos y abonos de cada renglon) referenciando la poliza
 * revertida en {@link #getPolizaRevertidaId()}. El reverso de una poliza balanceada
 * es, por construccion, tambien balanceado.</p>
 */
@Entity
@Table(name = "poliza_contable")
public class PolizaContable extends TenantScopedEntity {

    /** Escala monetaria (NUMERIC(18,2) en V33). */
    public static final int ESCALA_MONETARIA = 2;

    /** Origen de una poliza generada por un reverso (Req 38.5). */
    public static final String ORIGEN_REVERSO = "reverso";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Fecha contable de la poliza (Req 38.2). Inmutable. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    /** Tipo de poliza (ingreso, egreso, diario). Inmutable. */
    @Column(name = "tipo", nullable = false, updatable = false, length = 12)
    private String tipo;

    /** Concepto descriptivo; no vacio (Req 38.2). Inmutable. */
    @Column(name = "concepto", nullable = false, updatable = false, length = 300)
    private String concepto;

    /** Evento contable de origen (Req 38.2); {@code null} si no aplica. Inmutable. */
    @Column(name = "origen", updatable = false, length = 40)
    private String origen;

    /** Identificador del recurso de origen (factura, pago, etc.); opcional. Inmutable. */
    @Column(name = "origen_id", updatable = false)
    private UUID origenId;

    /** Poliza revertida por esta poliza de reverso (Req 38.5); {@code null} si no lo es. */
    @Column(name = "poliza_revertida_id", updatable = false)
    private UUID polizaRevertidaId;

    /** Suma de los cargos de los renglones (Req 38.3). Inmutable. */
    @Column(name = "total_cargos", nullable = false, updatable = false)
    private BigDecimal totalCargos;

    /** Suma de los abonos de los renglones; igual a {@link #totalCargos} (Req 38.3). Inmutable. */
    @Column(name = "total_abonos", nullable = false, updatable = false)
    private BigDecimal totalAbonos;

    /** Renglones (cargos/abonos) de la poliza; parte del agregado (Req 38.2). */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "poliza_contable_id", nullable = false, insertable = false, updatable = false)
    private List<MovimientoPoliza> renglones = new ArrayList<>();

    protected PolizaContable() {
        // Requerido por JPA.
    }

    /**
     * Crea una Poliza_Contable balanceada a partir de sus renglones (Req 38.2, 38.3,
     * 38.4; <strong>Property 16</strong>). Regla de negocio PURA:
     *
     * <ul>
     *   <li>Exige al menos dos renglones (un cargo y un abono) y cada renglon debe
     *       ser un cargo XOR un abono.</li>
     *   <li>Calcula {@code total_cargos} y {@code total_abonos} (escala 2, HALF_UP).</li>
     *   <li>Si la suma de cargos <strong>no</strong> es igual a la suma de abonos,
     *       <strong>rechaza</strong> con {@link ReglaNegocioException} informando la
     *       diferencia y NO construye la poliza (Req 38.4).</li>
     * </ul>
     *
     * @param fecha    fecha contable; obligatoria.
     * @param tipo     tipo de poliza; obligatorio.
     * @param concepto concepto descriptivo; obligatorio y no vacio.
     * @param origen   evento contable de origen (Req 38.2); opcional.
     * @param origenId identificador del recurso de origen; opcional.
     * @param renglones renglones de cargo/abono; obligatorio, con al menos un cargo y
     *                  un abono.
     * @param actor    identificador de quien registra (auditoria).
     * @return la Poliza_Contable balanceada lista para persistir.
     * @throws ReglaNegocioException si faltan datos, un renglon es invalido o la
     *         poliza no esta balanceada (422, con la diferencia).
     */
    public static PolizaContable crear(LocalDate fecha, TipoPoliza tipo, String concepto,
                                       String origen, UUID origenId,
                                       List<MovimientoPoliza> renglones, String actor) {
        if (fecha == null) {
            throw new ReglaNegocioException("La Poliza_Contable debe indicar la fecha.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("La Poliza_Contable debe indicar el tipo.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new ReglaNegocioException("La Poliza_Contable debe indicar el concepto.");
        }
        if (renglones == null || renglones.size() < 2) {
            throw new ReglaNegocioException(
                    "La Poliza_Contable debe tener al menos un cargo y un abono.");
        }

        BigDecimal sumaCargos = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal sumaAbonos = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        for (MovimientoPoliza renglon : renglones) {
            if (renglon == null) {
                throw new ReglaNegocioException(
                        "La Poliza_Contable no admite renglones nulos.");
            }
            sumaCargos = sumaCargos.add(renglon.getCargo());
            sumaAbonos = sumaAbonos.add(renglon.getAbono());
        }
        sumaCargos = sumaCargos.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        sumaAbonos = sumaAbonos.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        if (sumaCargos.compareTo(sumaAbonos) != 0) {
            BigDecimal diferencia = sumaCargos.subtract(sumaAbonos)
                    .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
            throw new ReglaNegocioException(
                    "La Poliza_Contable no esta balanceada: la suma de cargos ("
                            + sumaCargos.toPlainString() + ") difiere de la suma de abonos ("
                            + sumaAbonos.toPlainString() + ") en "
                            + diferencia.toPlainString() + ".");
        }

        PolizaContable poliza = new PolizaContable();
        poliza.id = UUID.randomUUID();
        poliza.fecha = fecha;
        poliza.tipo = tipo.valorBd();
        poliza.concepto = concepto.strip();
        poliza.origen = (origen == null || origen.isBlank()) ? null : origen.strip();
        poliza.origenId = origenId;
        poliza.totalCargos = sumaCargos;
        poliza.totalAbonos = sumaAbonos;
        for (MovimientoPoliza renglon : renglones) {
            renglon.asignarPoliza(poliza.id);
            poliza.renglones.add(renglon);
        }
        poliza.setCreatedBy(actor);
        poliza.setUpdatedBy(actor);
        return poliza;
    }

    /**
     * Crea la poliza de reverso de esta poliza (Req 38.5): una nueva Poliza_Contable
     * espejo que intercambia el cargo y el abono de cada renglon, con origen
     * {@link #ORIGEN_REVERSO} y referencia a esta poliza en
     * {@link #getPolizaRevertidaId()}. Como esta poliza esta balanceada, su reverso
     * lo esta por construccion (los totales se intercambian, siguen siendo iguales).
     *
     * @param fecha fecha contable del reverso; obligatoria.
     * @param actor identificador de quien reversa (auditoria).
     * @return la poliza de reverso balanceada lista para persistir.
     * @throws ReglaNegocioException si falta la fecha (422).
     */
    public PolizaContable reversar(LocalDate fecha, String actor) {
        if (fecha == null) {
            throw new ReglaNegocioException("La poliza de reverso debe indicar la fecha.");
        }
        List<MovimientoPoliza> renglonesReverso = new ArrayList<>();
        for (MovimientoPoliza renglon : this.renglones) {
            if (renglon.esCargo()) {
                renglonesReverso.add(MovimientoPoliza.abono(
                        renglon.getCuentaContableId(), renglon.getCargo(), actor));
            } else {
                renglonesReverso.add(MovimientoPoliza.cargo(
                        renglon.getCuentaContableId(), renglon.getAbono(), actor));
            }
        }
        PolizaContable reverso = crear(fecha, TipoPoliza.desdeValorBd(this.tipo),
                "Reverso de la poliza " + this.id + ": " + this.concepto,
                ORIGEN_REVERSO, this.id, renglonesReverso, actor);
        reverso.polizaRevertidaId = this.id;
        return reverso;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public TipoPoliza getTipo() {
        return TipoPoliza.desdeValorBd(tipo);
    }

    public String getConcepto() {
        return concepto;
    }

    public String getOrigen() {
        return origen;
    }

    public UUID getOrigenId() {
        return origenId;
    }

    public UUID getPolizaRevertidaId() {
        return polizaRevertidaId;
    }

    public BigDecimal getTotalCargos() {
        return totalCargos;
    }

    public BigDecimal getTotalAbonos() {
        return totalAbonos;
    }

    /**
     * Renglones (cargos/abonos) de la poliza, como vista de solo lectura (Req 38.5).
     *
     * @return la lista inmutable de renglones.
     */
    public List<MovimientoPoliza> getRenglones() {
        return Collections.unmodifiableList(renglones);
    }
}

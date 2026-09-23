package com.dessti.crm.activosfijos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Entidad JPA y raiz del agregado {@code activo_fijo}: un bien depreciable de la
 * Empresa, mapeado sobre la tabla {@code activo_fijo} de la migracion V37
 * (Req 44, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V37.</p>
 *
 * <h2>Reglas de dominio (Req 44)</h2>
 * <ul>
 *   <li>{@link #crear} valida los datos obligatorios (costo &gt; 0, fecha de
 *       adquisicion presente, vida util &gt; 0, metodo presente, valor residual en
 *       {@code [0, costo]}); si algo falta o es invalido lanza
 *       {@link ReglaNegocioException} (422) nombrando el campo (Req 44.1, 44.2).</li>
 *   <li>{@link #calcularDepreciacionPeriodo()} es una funcion pura que devuelve el
 *       monto candidato del proximo periodo segun el metodo (Req 44.3).</li>
 *   <li>{@link #aplicarDepreciacion(String)} acota (clamp) ese monto para que la
 *       depreciacion acumulada nunca exceda la base depreciable
 *       {@code (costo - valorResidual)} (invariante de acumulacion), lo suma a la
 *       acumulada y devuelve el monto realmente aplicado (Req 44.3).</li>
 *   <li>{@link #darDeBaja(String)} transita el estado a {@link EstadoActivoFijo#BAJA}
 *       conservando el historico (Req 44.4).</li>
 * </ul>
 *
 * <h2>Invariante de acumulacion (documentado)</h2>
 * <p>La depreciacion acumulada NUNCA excede la base depreciable
 * {@code (costo - valorResidual)}. El monto aplicado de cada periodo es
 * {@code min(candidatoCalculado, costo - valorResidual - depreciacionAcumulada)}.
 * Cuando el activo ya esta totalmente depreciado, el monto aplicado es cero. Este
 * clamp evita sobre-depreciar por redondeo o por el metodo de saldos decrecientes,
 * y es coherente con el CHECK {@code ck_activo_fijo_acumulada_no_excede_base} de
 * V37.</p>
 */
@Entity
@Table(name = "activo_fijo")
public class ActivoFijo extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nombre descriptivo del bien; no vacio (Req 44.1). */
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    /** Costo de adquisicion; estrictamente positivo (Req 44.1, 44.2). Inmutable. */
    @Column(name = "costo", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal costo;

    /** Fecha de adquisicion; obligatoria (Req 44.1). Inmutable. */
    @Column(name = "fecha_adquisicion", nullable = false, updatable = false)
    private LocalDate fechaAdquisicion;

    /** Vida util en meses; estrictamente positiva (Req 44.1, 44.2). Inmutable. */
    @Column(name = "vida_util_meses", nullable = false, updatable = false)
    private int vidaUtilMeses;

    /** Metodo de depreciacion; obligatorio (Req 44.1). Inmutable. */
    @Convert(converter = MetodoDepreciacionConverter.class)
    @Column(name = "metodo_depreciacion", nullable = false, length = 20, updatable = false)
    private MetodoDepreciacion metodoDepreciacion;

    /** Valor residual estimado; en {@code [0, costo]} (Req 44.2). Inmutable. */
    @Column(name = "valor_residual", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal valorResidual;

    /** Depreciacion acumulada; nunca excede {@code (costo - valorResidual)} (invariante). */
    @Column(name = "depreciacion_acumulada", nullable = false, precision = 18, scale = 2)
    private BigDecimal depreciacionAcumulada;

    /** Estado; se persiste como etiqueta ASCII (Req 44.4). */
    @Convert(converter = EstadoActivoFijoConverter.class)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoActivoFijo estado;

    protected ActivoFijo() {
        // Requerido por JPA.
    }

    /**
     * Da de alta un Activo_Fijo validando los datos obligatorios (Req 44.1, 44.2).
     * El {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir
     * (Req 23.4). El estado inicial es {@link EstadoActivoFijo#ACTIVO} y la
     * depreciacion acumulada arranca en cero.
     *
     * @param nombre           nombre del bien; obligatorio (1..200).
     * @param costo            costo de adquisicion; obligatorio y &gt; 0.
     * @param fechaAdquisicion fecha de adquisicion; obligatoria.
     * @param vidaUtilMeses    vida util en meses; obligatoria y &gt; 0.
     * @param metodo           metodo de depreciacion; obligatorio.
     * @param valorResidual    valor residual; {@code null} se asume 0; debe quedar
     *                         en {@code [0, costo]}.
     * @param actor            identificador de quien crea (auditoria).
     * @return el Activo_Fijo listo para persistir, en estado {@code activo}.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido,
     *         nombrando el campo (422, Req 44.2).
     */
    public static ActivoFijo crear(String nombre, BigDecimal costo, LocalDate fechaAdquisicion,
                                   Integer vidaUtilMeses, MetodoDepreciacion metodo,
                                   BigDecimal valorResidual, String actor) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El Activo_Fijo debe indicar el nombre.");
        }
        if (costo == null) {
            throw new ReglaNegocioException("El Activo_Fijo debe indicar el costo.");
        }
        if (costo.signum() <= 0) {
            throw new ReglaNegocioException("El costo del Activo_Fijo debe ser mayor que cero.");
        }
        if (fechaAdquisicion == null) {
            throw new ReglaNegocioException("El Activo_Fijo debe indicar la fecha de adquisicion.");
        }
        if (vidaUtilMeses == null) {
            throw new ReglaNegocioException("El Activo_Fijo debe indicar la vida util en meses.");
        }
        if (vidaUtilMeses <= 0) {
            throw new ReglaNegocioException(
                    "La vida util en meses del Activo_Fijo debe ser mayor que cero.");
        }
        if (metodo == null) {
            throw new ReglaNegocioException("El Activo_Fijo debe indicar el metodo de depreciacion.");
        }
        BigDecimal residual = (valorResidual == null)
                ? BigDecimal.ZERO.setScale(2)
                : valorResidual.setScale(2, RoundingMode.HALF_UP);
        if (residual.signum() < 0) {
            throw new ReglaNegocioException(
                    "El valor residual del Activo_Fijo no puede ser negativo.");
        }
        BigDecimal costoNorm = costo.setScale(2, RoundingMode.HALF_UP);
        if (residual.compareTo(costoNorm) > 0) {
            throw new ReglaNegocioException(
                    "El valor residual del Activo_Fijo no puede exceder el costo.");
        }

        ActivoFijo activo = new ActivoFijo();
        activo.id = UUID.randomUUID();
        activo.nombre = nombre.strip();
        activo.costo = costoNorm;
        activo.fechaAdquisicion = fechaAdquisicion;
        activo.vidaUtilMeses = vidaUtilMeses;
        activo.metodoDepreciacion = metodo;
        activo.valorResidual = residual;
        activo.depreciacionAcumulada = BigDecimal.ZERO.setScale(2);
        activo.estado = EstadoActivoFijo.ACTIVO;
        activo.setCreatedBy(actor);
        activo.setUpdatedBy(actor);
        return activo;
    }

    /**
     * Base depreciable del bien: {@code costo - valorResidual}. Es el techo de la
     * depreciacion acumulada (invariante de acumulacion).
     *
     * @return la base depreciable, con escala 2.
     */
    public BigDecimal baseDepreciable() {
        return costo.subtract(valorResidual);
    }

    /**
     * Depreciacion acumulada aun disponible antes de alcanzar la base depreciable:
     * {@code baseDepreciable - depreciacionAcumulada} (nunca negativa).
     *
     * @return el remanente depreciable, con escala 2.
     */
    public BigDecimal remanenteDepreciable() {
        BigDecimal remanente = baseDepreciable().subtract(depreciacionAcumulada);
        return (remanente.signum() < 0) ? BigDecimal.ZERO.setScale(2) : remanente;
    }

    /**
     * Indica si el Activo_Fijo ya esta totalmente depreciado (la depreciacion
     * acumulada alcanzo la base depreciable, remanente cero).
     *
     * @return {@code true} si no queda base depreciable por aplicar.
     */
    public boolean estaTotalmenteDepreciado() {
        return remanenteDepreciable().signum() == 0;
    }

    /**
     * Funcion pura: calcula el monto <strong>candidato</strong> de depreciacion del
     * proximo periodo segun el metodo del bien (Req 44.3), sin modificar el estado.
     * Este candidato aun no esta acotado por el remanente depreciable; el clamp lo
     * aplica {@link #aplicarDepreciacion(String)}.
     *
     * @return el monto candidato del periodo, con escala 2.
     */
    public BigDecimal calcularDepreciacionPeriodo() {
        return metodoDepreciacion.calcularMontoPeriodo(
                costo, valorResidual, depreciacionAcumulada, vidaUtilMeses);
    }

    /**
     * Aplica la depreciacion del periodo (Req 44.3): calcula el candidato del
     * metodo, lo <strong>acota</strong> al remanente depreciable (invariante de
     * acumulacion), lo suma a la depreciacion acumulada y devuelve el monto
     * realmente aplicado. Cuando el bien ya esta totalmente depreciado, el monto
     * aplicado es cero y la acumulada no cambia.
     *
     * @param actor identificador de quien deprecia, para {@code updated_by}.
     * @return el monto de depreciacion realmente aplicado en el periodo (&ge; 0).
     * @throws ReglaNegocioException si el Activo_Fijo esta dado de baja (422).
     */
    public BigDecimal aplicarDepreciacion(String actor) {
        if (estado.esFinal()) {
            throw new ReglaNegocioException(
                    "No se puede depreciar un Activo_Fijo dado de baja.");
        }
        BigDecimal candidato = calcularDepreciacionPeriodo();
        BigDecimal remanente = remanenteDepreciable();
        // Clamp: el monto aplicado nunca excede el remanente depreciable.
        BigDecimal aplicado = candidato.min(remanente);
        if (aplicado.signum() < 0) {
            aplicado = BigDecimal.ZERO.setScale(2);
        }
        this.depreciacionAcumulada = this.depreciacionAcumulada.add(aplicado);
        this.setUpdatedBy(actor);
        return aplicado;
    }

    /**
     * Da de baja el Activo_Fijo (baja o venta): transita el estado a
     * {@link EstadoActivoFijo#BAJA} conservando el historico (Req 44.4). Aplica la
     * maquina de estados pura; volver a dar de baja un bien ya en {@code baja} es
     * una transicion invalida (409).
     *
     * @param actor identificador de quien da de baja, para {@code updated_by}.
     * @throws TransicionInvalidaException si el Activo_Fijo ya esta dado de baja (409).
     */
    public void darDeBaja(String actor) {
        if (!this.estado.puedeTransicionarA(EstadoActivoFijo.BAJA)) {
            throw new TransicionInvalidaException(
                    "El Activo_Fijo ya esta dado de baja; no admite una nueva baja.");
        }
        this.estado = EstadoActivoFijo.BAJA;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Activo_Fijo esta en estado {@link EstadoActivoFijo#ACTIVO}.
     *
     * @return {@code true} si el bien esta activo (no dado de baja).
     */
    public boolean estaActivo() {
        return estado == EstadoActivoFijo.ACTIVO;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getCosto() {
        return costo;
    }

    public LocalDate getFechaAdquisicion() {
        return fechaAdquisicion;
    }

    public int getVidaUtilMeses() {
        return vidaUtilMeses;
    }

    public MetodoDepreciacion getMetodoDepreciacion() {
        return metodoDepreciacion;
    }

    public BigDecimal getValorResidual() {
        return valorResidual;
    }

    public BigDecimal getDepreciacionAcumulada() {
        return depreciacionAcumulada;
    }

    public EstadoActivoFijo getEstado() {
        return estado;
    }
}

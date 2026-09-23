package com.dessti.crm.rhnomina.empleado.domain;

import java.math.BigDecimal;
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
 * Entidad JPA del {@code contrato_laboral} asociado a un {@link Empleado}
 * (Req 40.1), mapeada sobre la tabla {@code contrato_laboral} de la migracion
 * V32. El alta de un Empleado crea atomicamente su primer Contrato_Laboral
 * (Req 40.1).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity};
 * el mapeo de columnas coincide <em>exactamente</em> con V32. El
 * {@code tenant_id}, la {@code version} y las marcas de auditoria se heredan y no
 * se redeclaran aqui.</p>
 *
 * <h2>Reglas de dominio (Req 40.1, 40.2)</h2>
 * <ul>
 *   <li>{@link #crear(UUID, TipoContrato, BigDecimal, Periodicidad, LocalDate, String)}
 *       exige el Empleado, el tipo, la periodicidad, un salario diario
 *       estrictamente positivo y la fecha de inicio.</li>
 *   <li>Se conserva como historico (Req 40.4): la baja del Empleado no elimina
 *       sus contratos.</li>
 * </ul>
 */
@Entity
@Table(name = "contrato_laboral")
public class ContratoLaboral extends TenantScopedEntity {

    /** Escala decimal del salario diario (coincide con NUMERIC(18,2) de V32). */
    public static final int ESCALA_SALARIO = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empleado titular del contrato (Req 40.1). */
    @Column(name = "empleado_id", nullable = false, updatable = false)
    private UUID empleadoId;

    /** Tipo de contrato; se persiste como etiqueta ASCII (Req 40.1). */
    @Convert(converter = TipoContratoConverter.class)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoContrato tipo;

    /** Salario diario del contrato; estrictamente positivo (Req 40.1). */
    @Column(name = "salario_diario", nullable = false, precision = 18, scale = ESCALA_SALARIO)
    private BigDecimal salarioDiario;

    /** Periodicidad de pago; se persiste como etiqueta ASCII (Req 40.1). */
    @Convert(converter = PeriodicidadConverter.class)
    @Column(name = "periodicidad", nullable = false, length = 12)
    private Periodicidad periodicidad;

    /** Fecha de inicio del contrato (Req 40.1). */
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** Fecha de fin del contrato; {@code null} si es indefinido/vigente. */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /** Bandera de borrado logico; {@code true} mientras el contrato esta vigente. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected ContratoLaboral() {
        // Requerido por JPA.
    }

    /**
     * Crea un Contrato_Laboral nuevo y activo para un Empleado validando los
     * datos obligatorios (Req 40.1, 40.2). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param empleadoId    Empleado titular; obligatorio.
     * @param tipo          tipo de contrato; obligatorio.
     * @param salarioDiario salario diario; obligatorio y estrictamente positivo.
     * @param periodicidad  periodicidad de pago; obligatoria.
     * @param fechaInicio   fecha de inicio; obligatoria.
     * @param actor         identificador de quien crea, para {@code created_by}/{@code updated_by}.
     * @return el contrato listo para persistir.
     * @throws ReglaNegocioException si falta el Empleado, el tipo, la periodicidad,
     *         la fecha de inicio, o el salario diario no es estrictamente positivo (422).
     */
    public static ContratoLaboral crear(UUID empleadoId, TipoContrato tipo, BigDecimal salarioDiario,
                                        Periodicidad periodicidad, LocalDate fechaInicio, String actor) {
        if (empleadoId == null) {
            throw new ReglaNegocioException("El Contrato_Laboral debe referirse a un Empleado.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo del Contrato_Laboral es obligatorio.");
        }
        if (periodicidad == null) {
            throw new ReglaNegocioException("La periodicidad del Contrato_Laboral es obligatoria.");
        }
        if (fechaInicio == null) {
            throw new ReglaNegocioException("La fecha de inicio del Contrato_Laboral es obligatoria.");
        }
        if (salarioDiario == null || salarioDiario.signum() <= 0) {
            throw new ReglaNegocioException(
                    "El salario diario del Contrato_Laboral debe ser mayor que cero.");
        }
        ContratoLaboral contrato = new ContratoLaboral();
        contrato.id = UUID.randomUUID();
        contrato.empleadoId = empleadoId;
        contrato.tipo = tipo;
        contrato.salarioDiario = salarioDiario;
        contrato.periodicidad = periodicidad;
        contrato.fechaInicio = fechaInicio;
        contrato.activo = true;
        contrato.setCreatedBy(actor);
        contrato.setUpdatedBy(actor);
        return contrato;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpleadoId() {
        return empleadoId;
    }

    public TipoContrato getTipo() {
        return tipo;
    }

    public BigDecimal getSalarioDiario() {
        return salarioDiario;
    }

    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public boolean isActivo() {
        return activo;
    }
}

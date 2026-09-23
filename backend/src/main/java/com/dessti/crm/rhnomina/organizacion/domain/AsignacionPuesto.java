package com.dessti.crm.rhnomina.organizacion.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code asignacion_puesto}: el vinculo de un {@link Empleado}
 * a un {@link Puesto} (Req 61.2), mapeada sobre la tabla {@code asignacion_puesto}
 * de la migracion V36.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity};
 * el mapeo de columnas coincide <em>exactamente</em> con V36. El {@code tenant_id},
 * la {@code version} y las marcas de auditoria se heredan y no se redeclaran.</p>
 *
 * <h2>Reglas de dominio (Req 61.2)</h2>
 * <ul>
 *   <li>{@link #asignar(UUID, UUID, LocalDate, String)} exige el Empleado, el
 *       Puesto y la fecha de inicio; la asignacion queda vigente ({@code activa}).</li>
 *   <li>{@link #terminar(LocalDate, String)} cierra la asignacion fijando la fecha
 *       de fin ({@code activa=false}) conservando el historico; la fecha de fin no
 *       puede ser anterior a la de inicio.</li>
 * </ul>
 *
 * <p>El {@code Empleado} se referencia por {@code com.dessti.crm.rhnomina.empleado.domain.Empleado}
 * a nivel de FK ({@code empleado_id} -&gt; {@code empleado(id)}, Req 61.6); aqui se
 * guarda solo su identificador para mantener el submodulo desacoplado.</p>
 */
@Entity
@Table(name = "asignacion_puesto")
public class AsignacionPuesto extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empleado asignado (FK a empleado(id) de V32, Req 61.6). */
    @Column(name = "empleado_id", nullable = false, updatable = false)
    private UUID empleadoId;

    /** Puesto al que se asigna el Empleado (Req 61.2). */
    @Column(name = "puesto_id", nullable = false, updatable = false)
    private UUID puestoId;

    /** Fecha de inicio de la asignacion; obligatoria. */
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** Fecha de fin de la asignacion; {@code null} mientras esta vigente. */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /** Bandera de vigencia; {@code true} mientras la asignacion esta activa. */
    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected AsignacionPuesto() {
        // Requerido por JPA.
    }

    /**
     * Asigna un Empleado a un Puesto (Req 61.2). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param empleadoId  Empleado a asignar; obligatorio.
     * @param puestoId    Puesto destino; obligatorio.
     * @param fechaInicio fecha de inicio de la asignacion; obligatoria.
     * @param actor       identificador de quien crea la asignacion, para auditoria.
     * @return la asignacion lista para persistir.
     * @throws ReglaNegocioException si falta el Empleado, el Puesto o la fecha de
     *         inicio (422).
     */
    public static AsignacionPuesto asignar(UUID empleadoId, UUID puestoId,
                                           LocalDate fechaInicio, String actor) {
        if (empleadoId == null) {
            throw new ReglaNegocioException("La asignacion debe referirse a un Empleado.");
        }
        if (puestoId == null) {
            throw new ReglaNegocioException("La asignacion debe referirse a un Puesto.");
        }
        if (fechaInicio == null) {
            throw new ReglaNegocioException("La fecha de inicio de la asignacion es obligatoria.");
        }
        AsignacionPuesto asignacion = new AsignacionPuesto();
        asignacion.id = UUID.randomUUID();
        asignacion.empleadoId = empleadoId;
        asignacion.puestoId = puestoId;
        asignacion.fechaInicio = fechaInicio;
        asignacion.fechaFin = null;
        asignacion.activa = true;
        asignacion.setCreatedBy(actor);
        asignacion.setUpdatedBy(actor);
        return asignacion;
    }

    /**
     * Cierra la asignacion fijando la fecha de fin ({@code activa=false}),
     * conservando el historico (Req 61.2).
     *
     * @param fechaFin fecha de fin; obligatoria y no anterior a la de inicio.
     * @param actor    identificador de quien cierra la asignacion, para {@code updated_by}.
     * @throws ReglaNegocioException si la fecha de fin falta o es anterior a la de
     *         inicio (422).
     */
    public void terminar(LocalDate fechaFin, String actor) {
        if (fechaFin == null) {
            throw new ReglaNegocioException("La fecha de fin de la asignacion es obligatoria.");
        }
        if (fechaFin.isBefore(this.fechaInicio)) {
            throw new ReglaNegocioException(
                    "La fecha de fin de la asignacion no puede ser anterior a la de inicio.");
        }
        this.fechaFin = fechaFin;
        this.activa = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si la asignacion esta vigente (Req 61.2).
     *
     * @return {@code true} si la asignacion esta activa.
     */
    public boolean estaActiva() {
        return activa;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpleadoId() {
        return empleadoId;
    }

    public UUID getPuestoId() {
        return puestoId;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public boolean isActiva() {
        return activa;
    }
}

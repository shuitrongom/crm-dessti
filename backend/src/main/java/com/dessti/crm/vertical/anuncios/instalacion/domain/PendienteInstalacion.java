package com.dessti.crm.vertical.anuncios.instalacion.domain;

import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de una entrada de la <em>Lista_Pendientes</em> asociada a una
 * {@link OrdenTrabajoInstalacion} (Req 19.4), mapeada sobre la tabla
 * {@code pendiente_instalacion} de la migracion V24. Registra una tarea pendiente
 * detectada durante el avance de la instalacion.
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignado automaticamente al persistir), {@code version}
 * y las marcas de auditoria; no se redeclaran aqui. El
 * {@code orden_trabajo_instalacion_id} referencia a la OTI propietaria dentro del
 * mismo tenant.</p>
 *
 * <p>Sigue el patron de {@code LevantamientoFoto} respecto de su Levantamiento: es
 * una entidad hija independiente (una fila por pendiente), no una coleccion
 * embebida, lo que mantiene la limpieza relacional y el aislamiento por tenant +
 * RLS de forma uniforme.</p>
 *
 * <h2>Guarda de cierre (Req 19.6)</h2>
 * <p>Un pendiente "sin resolver" es aquel con {@link #isResuelto()} en
 * {@code false}. La capa de aplicacion impide pasar la OTI a {@code completada}
 * mientras exista al menos un pendiente sin resolver.</p>
 */
@Entity
@Table(name = "pendiente_instalacion")
public class PendienteInstalacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Orden_Trabajo_Instalacion propietaria (mismo tenant). No modificable tras crear. */
    @Column(name = "orden_trabajo_instalacion_id", nullable = false, updatable = false)
    private UUID ordenTrabajoInstalacionId;

    /** Descripcion de la tarea pendiente; obligatoria (Req 19.4). */
    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Indica si el pendiente esta resuelto; inicialmente {@code false} (Req 19.6). */
    @Column(name = "resuelto", nullable = false)
    private boolean resuelto;

    protected PendienteInstalacion() {
        // Requerido por JPA.
    }

    /**
     * Crea una entrada de Lista_Pendientes asociada a una
     * Orden_Trabajo_Instalacion (Req 19.4), validando la descripcion. El
     * {@code orden_trabajo_instalacion_id} se deriva de la OTI indicada y el
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     * El pendiente nace sin resolver ({@code resuelto = false}).
     *
     * @param orden       Orden_Trabajo_Instalacion propietaria; obligatoria.
     * @param descripcion descripcion de la tarea pendiente; obligatoria (no vacia).
     * @param actor       identificador de quien registra, para auditoria.
     * @return el pendiente listo para persistir, sin resolver.
     * @throws ReglaNegocioException si la OTI es {@code null} o la descripcion esta
     *         vacia (422, Req 19.4).
     */
    public static PendienteInstalacion paraOrden(OrdenTrabajoInstalacion orden,
                                                 String descripcion, String actor) {
        if (orden == null) {
            throw new ReglaNegocioException(
                    "El pendiente debe asociarse a una Orden_Trabajo_Instalacion.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("La descripcion del pendiente no puede estar vacia.");
        }
        PendienteInstalacion pendiente = new PendienteInstalacion();
        pendiente.id = UUID.randomUUID();
        pendiente.ordenTrabajoInstalacionId = orden.getId();
        pendiente.descripcion = descripcion.strip();
        pendiente.resuelto = false;
        pendiente.setCreatedBy(actor);
        pendiente.setUpdatedBy(actor);
        return pendiente;
    }

    /**
     * Marca el pendiente como resuelto (Req 19.4, 19.6). Operacion idempotente:
     * marcar como resuelto un pendiente ya resuelto no altera el resultado.
     *
     * @param actor identificador de quien resuelve, para {@code updated_by}.
     */
    public void resolver(String actor) {
        this.resuelto = true;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrdenTrabajoInstalacionId() {
        return ordenTrabajoInstalacionId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isResuelto() {
        return resuelto;
    }
}

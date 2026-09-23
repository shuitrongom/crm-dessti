package com.dessti.crm.vertical.anuncios.instalacion.domain;

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
 * Entidad JPA y raiz del agregado {@code orden_trabajo_instalacion}: una Orden de
 * Trabajo de Instalacion (OTI) creada a partir de una {@code Orden_Fabricacion} en
 * estado {@code terminada}, asignando una Cuadrilla y una fecha programada, mapeada
 * sobre la tabla {@code orden_trabajo_instalacion} de la migracion V24 (Req 19, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V24.</p>
 *
 * <h2>Reglas de dominio (Req 19)</h2>
 * <ul>
 *   <li>{@link #programar(UUID, UUID, UUID, UUID, LocalDate, String)} crea la OTI
 *       vinculada a la Orden_Fabricacion, al Sitio, a la Cuadrilla y al Cliente
 *       (denormalizado para el filtro del Req 19.7), con estado inicial
 *       {@link EstadoOrdenTrabajoInstalacion#PROGRAMADA} (Req 19.1). Las
 *       <em>precondiciones</em> de programacion (Orden_Fabricacion terminada
 *       Req 19.2, Levantamiento_Sitio completado y Permiso_Instalacion aprobado
 *       Req 19.3) las aplica la capa de aplicacion antes de invocar esta fabrica,
 *       pues requieren consultar otros agregados.</li>
 *   <li>{@link #cambiarEstado(EstadoOrdenTrabajoInstalacion, String)} aplica la
 *       maquina de estados pura (Req 19.5); toda transicion no permitida —incluida
 *       cualquiera que parta de un estado final— se rechaza con
 *       {@link TransicionInvalidaException} (409) conservando el estado actual. La
 *       guarda del Req 19.6 (no completar mientras haya pendientes sin resolver)
 *       vive en la capa de aplicacion, pues requiere consultar la
 *       Lista_Pendientes; el dominio no la comprueba aqui.</li>
 * </ul>
 */
@Entity
@Table(name = "orden_trabajo_instalacion")
public class OrdenTrabajoInstalacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Orden_Fabricacion (terminada) de origen a la que se vincula la OTI (Req 19.1). */
    @Column(name = "orden_fabricacion_id", nullable = false, updatable = false)
    private UUID ordenFabricacionId;

    /**
     * Sitio en el que se realiza la instalacion (Req 19.3). Referencia debil: la
     * tabla {@code sitio} aun no existe (se crea en la tarea 22.2), por lo que la
     * columna no declara FK (ver V24). Inmutable; se fija al programar la OTI.
     */
    @Column(name = "sitio_id", nullable = false, updatable = false)
    private UUID sitioId;

    /**
     * Cuadrilla asignada a la instalacion (Req 19.1, 19.7). Referencia debil: la
     * tabla {@code cuadrilla} aun no existe, por lo que la columna no declara FK
     * (ver V24).
     */
    @Column(name = "cuadrilla_id", nullable = false)
    private UUID cuadrillaId;

    /**
     * Cliente de la Orden_Fabricacion, denormalizado para el filtro del listado por
     * Cliente (Req 19.7). Inmutable; se fija al programar la OTI.
     */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Fecha programada de la instalacion (Req 19.1). */
    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    /** Estado; se persiste como etiqueta ASCII (Req 19.1, 19.5). */
    @Convert(converter = EstadoOrdenTrabajoInstalacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoOrdenTrabajoInstalacion estado;

    protected OrdenTrabajoInstalacion() {
        // Requerido por JPA.
    }

    /**
     * Programa una Orden_Trabajo_Instalacion vinculada a una Orden_Fabricacion
     * terminada, a un Sitio, a una Cuadrilla y a su Cliente, en estado inicial
     * {@link EstadoOrdenTrabajoInstalacion#PROGRAMADA} (Req 19.1). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * <p><strong>Precondiciones:</strong> esta fabrica NO comprueba que la
     * Orden_Fabricacion este terminada (Req 19.2), que el Sitio tenga un
     * Levantamiento_Sitio completado ni un Permiso_Instalacion aprobado (Req 19.3).
     * Esas precondiciones las verifica la capa de aplicacion
     * ({@code ServicioOrdenesTrabajoInstalacion}) antes de invocar este metodo, pues
     * requieren consultar otros agregados.</p>
     *
     * @param ordenFabricacionId identificador de la Orden_Fabricacion terminada;
     *                           obligatorio.
     * @param sitioId            Sitio de la instalacion (Req 19.3); obligatorio.
     * @param cuadrillaId        Cuadrilla asignada (Req 19.1, 19.7); obligatoria.
     * @param clienteId          Cliente de la Orden_Fabricacion (Req 19.7);
     *                           obligatorio.
     * @param fechaProgramada    fecha programada de la instalacion (Req 19.1);
     *                           obligatoria.
     * @param actor              identificador de quien programa, para
     *                           {@code created_by}/{@code updated_by}.
     * @return la Orden_Trabajo_Instalacion lista para persistir, en {@code programada}.
     * @throws ReglaNegocioException si falta algun dato obligatorio (422).
     */
    public static OrdenTrabajoInstalacion programar(UUID ordenFabricacionId, UUID sitioId,
                                                    UUID cuadrillaId, UUID clienteId,
                                                    LocalDate fechaProgramada, String actor) {
        if (ordenFabricacionId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe asociarse a una Orden_Fabricacion existente.");
        }
        if (sitioId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe asociarse a un Sitio.");
        }
        if (cuadrillaId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe asignar una Cuadrilla.");
        }
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe registrar el Cliente de la Orden_Fabricacion.");
        }
        if (fechaProgramada == null) {
            throw new ReglaNegocioException(
                    "La Orden_Trabajo_Instalacion debe indicar una fecha programada.");
        }
        OrdenTrabajoInstalacion orden = new OrdenTrabajoInstalacion();
        orden.id = UUID.randomUUID();
        orden.ordenFabricacionId = ordenFabricacionId;
        orden.sitioId = sitioId;
        orden.cuadrillaId = cuadrillaId;
        orden.clienteId = clienteId;
        orden.fechaProgramada = fechaProgramada;
        orden.estado = EstadoOrdenTrabajoInstalacion.PROGRAMADA;
        orden.setCreatedBy(actor);
        orden.setUpdatedBy(actor);
        return orden;
    }

    /**
     * Cambia el estado de la Orden_Trabajo_Instalacion aplicando la maquina de
     * estados pura (Req 19.5). Solo permite las transiciones definidas; toda
     * transicion no permitida —incluida cualquiera que parta de un estado final— se
     * rechaza con {@link TransicionInvalidaException} (409) y el estado actual se
     * conserva sin modificarlo.
     *
     * <p><strong>Nota (Req 19.6):</strong> la guarda que impide pasar a
     * {@code completada} mientras existan pendientes sin resolver NO se aplica aqui;
     * la aplica la capa de aplicacion antes de invocar este metodo, pues requiere
     * consultar la Lista_Pendientes de la OTI.</p>
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException       si {@code nuevoEstado} es nulo (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoOrdenTrabajoInstalacion nuevoEstado, String actor) {
        if (nuevoEstado == null) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        if (!this.estado.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionInvalidaException(
                    "Transicion de estado invalida: de '" + this.estado.valorBd()
                            + "' a '" + nuevoEstado.valorBd() + "'.");
        }
        this.estado = nuevoEstado;
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrdenFabricacionId() {
        return ordenFabricacionId;
    }

    public UUID getSitioId() {
        return sitioId;
    }

    public UUID getCuadrillaId() {
        return cuadrillaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public LocalDate getFechaProgramada() {
        return fechaProgramada;
    }

    public EstadoOrdenTrabajoInstalacion getEstado() {
        return estado;
    }
}

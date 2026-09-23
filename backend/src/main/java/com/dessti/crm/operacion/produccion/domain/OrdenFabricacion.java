package com.dessti.crm.operacion.produccion.domain;

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
 * Entidad JPA y raiz del agregado {@code orden_fabricacion}: una Orden de
 * Fabricacion generada a partir de una {@code Cotizacion} aprobada, mapeada sobre
 * la tabla {@code orden_fabricacion} de la migracion V17 (Req 7, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V17.</p>
 *
 * <h2>Reglas de dominio (Req 7)</h2>
 * <ul>
 *   <li>{@link #generar(UUID, UUID, String)} crea la Orden_Fabricacion vinculada a
 *       la Cotizacion y a su Cliente (denormalizado para el filtro del Req 7.9),
 *       con estado inicial {@link EstadoOrdenFabricacion#PENDIENTE} (Req 7.4). Las
 *       <em>precondiciones</em> de generacion (Cotizacion aprobada, sin OF previa,
 *       con Prueba_Diseno aprobada; Property 7) las aplica la capa de aplicacion
 *       antes de invocar esta fabrica.</li>
 *   <li>{@link #cambiarEstado(EstadoOrdenFabricacion, String)} aplica la maquina de
 *       estados pura (Req 7.5, 7.6); toda transicion no permitida —incluida
 *       cualquiera que parta de un estado final— se rechaza con
 *       {@link TransicionInvalidaException} (409) conservando el estado actual.</li>
 * </ul>
 */
@Entity
@Table(name = "orden_fabricacion")
public class OrdenFabricacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Cotizacion (aprobada) de origen a la que se vincula la OF en la genesis desde
     * Cotizacion del giro anuncios (Req 7.1, 7.3). Es <strong>opcional</strong>: en
     * el origen generico (Req 1.1) la OF se crea sin Cotizacion y esta columna queda
     * {@code null} (migracion V66 la vuelve NULLABLE y hace parcial el indice unico
     * {@code (tenant_id, cotizacion_id) WHERE cotizacion_id IS NOT NULL}).
     *
     * <p>Se retira {@code nullable=false}/{@code updatable=false} a nivel de columna
     * para permitir el valor nulo; el campo sigue siendo <strong>inmutable tras la
     * creacion</strong>: solo lo fijan las factorias {@link #generar(UUID, UUID, String)}
     * y {@link #crearDirecta(UUID, String)}, y no existe mutador (Req 1.2, §A1).</p>
     */
    @Column(name = "cotizacion_id")
    private UUID cotizacionId;

    /**
     * Cliente de la Cotizacion, denormalizado para el filtro del listado por
     * Cliente (Req 7.9). Inmutable; se fija al generar la OF.
     */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    /** Estado; se persiste como etiqueta ASCII (Req 7.4, 7.5). */
    @Convert(converter = EstadoOrdenFabricacionConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoOrdenFabricacion estado;

    protected OrdenFabricacion() {
        // Requerido por JPA.
    }

    /**
     * Genera una Orden_Fabricacion vinculada a una Cotizacion aprobada y a su
     * Cliente, en estado inicial {@link EstadoOrdenFabricacion#PENDIENTE}
     * (Req 7.1, 7.4). El {@code tenant_id} lo fija {@link TenantScopedEntity} al
     * persistir (Req 23.4).
     *
     * <p><strong>Precondiciones (Property 7):</strong> esta fabrica NO comprueba
     * que la Cotizacion este aprobada, que no exista ya una OF para la Cotizacion,
     * ni que haya una Prueba_Diseno aprobada. Esas tres precondiciones (Req 7.2,
     * 7.3, 15.5) las verifica la capa de aplicacion
     * ({@code ServicioOrdenesFabricacion}) antes de invocar este metodo, pues
     * requieren consultar otros agregados.</p>
     *
     * @param cotizacionId identificador de la Cotizacion aprobada; obligatorio.
     * @param clienteId    Cliente de la Cotizacion (para el filtro del Req 7.9);
     *                     obligatorio.
     * @param actor        identificador de quien genera, para {@code created_by}/
     *                     {@code updated_by}.
     * @return la Orden_Fabricacion lista para persistir, en {@code pendiente}.
     * @throws ReglaNegocioException si falta la Cotizacion o el Cliente (422).
     */
    public static OrdenFabricacion generar(UUID cotizacionId, UUID clienteId, String actor) {
        if (cotizacionId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Fabricacion debe asociarse a una Cotizacion existente.");
        }
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Fabricacion debe registrar el Cliente de la Cotizacion.");
        }
        OrdenFabricacion orden = new OrdenFabricacion();
        orden.id = UUID.randomUUID();
        orden.cotizacionId = cotizacionId;
        orden.clienteId = clienteId;
        orden.estado = EstadoOrdenFabricacion.PENDIENTE;
        orden.setCreatedBy(actor);
        orden.setUpdatedBy(actor);
        return orden;
    }

    /**
     * Crea una Orden_Fabricacion por el <strong>origen generico</strong> (Req 1.1,
     * 1.2), asociada directamente a un Cliente y <em>sin</em> Cotizacion de origen
     * ({@code cotizacionId = null}). Se crea en estado inicial
     * {@link EstadoOrdenFabricacion#PENDIENTE} (Req 1.2). El {@code tenant_id} lo
     * fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * <p>A diferencia de {@link #generar(UUID, UUID, String)}, esta factoria no
     * exige Cotizacion ni sus tres precondiciones: es el camino de creacion para
     * tenants de cualquier giro (§A1). La verificacion de existencia del Cliente
     * (404) la realiza la capa de aplicacion ({@code ServicioOrdenesFabricacion})
     * antes de invocar esta factoria; aqui solo se valida que el {@code clienteId}
     * este presente (Req 1.4).</p>
     *
     * @param clienteId Cliente al que se asocia la OF; obligatorio (Req 1.4).
     * @param actor     identificador de quien crea, para {@code created_by}/
     *                  {@code updated_by}.
     * @return la Orden_Fabricacion lista para persistir, en {@code pendiente} y con
     *         {@code cotizacionId = null}.
     * @throws ReglaNegocioException si {@code clienteId} es nulo (422, Req 1.4).
     */
    public static OrdenFabricacion crearDirecta(UUID clienteId, String actor) {
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Fabricacion directa debe asociarse a un Cliente.");
        }
        OrdenFabricacion orden = new OrdenFabricacion();
        orden.id = UUID.randomUUID();
        orden.cotizacionId = null;
        orden.clienteId = clienteId;
        orden.estado = EstadoOrdenFabricacion.PENDIENTE;
        orden.setCreatedBy(actor);
        orden.setUpdatedBy(actor);
        return orden;
    }

    /**
     * Cambia el estado de la Orden_Fabricacion aplicando la maquina de estados pura
     * (Req 7.5, 7.6). Solo permite las transiciones definidas; toda transicion no
     * permitida —incluida cualquiera que parta de un estado final— se rechaza con
     * {@link TransicionInvalidaException} (409) y el estado actual se conserva
     * sin modificarlo (Req 7.6).
     *
     * @param nuevoEstado estado destino; obligatorio.
     * @param actor       identificador de quien realiza el cambio, para
     *                    {@code updated_by}.
     * @throws ReglaNegocioException       si {@code nuevoEstado} es nulo (422).
     * @throws TransicionInvalidaException si la transicion no esta permitida (409).
     */
    public void cambiarEstado(EstadoOrdenFabricacion nuevoEstado, String actor) {
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

    public UUID getCotizacionId() {
        return cotizacionId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public EstadoOrdenFabricacion getEstado() {
        return estado;
    }
}

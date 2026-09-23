package com.dessti.crm.compras.recepcion.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code partida_recepcion} (renglon de una
 * {@link RecepcionMercancia}: partida de Orden_Compra + Material + cantidad
 * recibida), mapeada sobre la tabla {@code partida_recepcion} de la migracion V29
 * (Req 32.1, 32.3, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V29.</p>
 *
 * <h2>Reglas de dominio (Req 32.1, 32.3)</h2>
 * <ul>
 *   <li>{@link #crear} exige la Partida_Orden_Compra, el Material y una cantidad
 *       recibida estrictamente positiva (una entrada &lt;= 0 se rechaza con
 *       {@link ReglaNegocioException}, 422).</li>
 *   <li>El tope de la cantidad recibida ACUMULADA por partida (no exceder lo
 *       ordenado, Req 32.3) NO se comprueba aqui (una partida no conoce el
 *       acumulado de otras recepciones): lo aplica
 *       {@link ReglasRecepcion#validarNoExcederOrdenado} desde la capa de
 *       aplicacion (Property 10).</li>
 * </ul>
 *
 * <p>La partida es una entidad hija del agregado {@link RecepcionMercancia}: se
 * crea y se gestiona a traves de la recepcion.</p>
 */
@Entity
@Table(name = "partida_recepcion")
public class PartidaRecepcion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Recepcion_Mercancia a la que pertenece la partida (relacion muchos-a-uno). La
     * FK {@code recepcion_mercancia_id} es NOT NULL con ON DELETE CASCADE en V29;
     * se gestiona desde el agregado {@link RecepcionMercancia}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "recepcion_mercancia_id", nullable = false, updatable = false)
    private RecepcionMercancia recepcion;

    /** Partida_Orden_Compra contra la que se recibe (Req 32.1); obligatoria. */
    @Column(name = "partida_orden_compra_id", nullable = false, updatable = false)
    private UUID partidaOrdenCompraId;

    /** Material recibido (denormalizado desde la partida de OC, Req 32.4); obligatorio. */
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    /** Cantidad recibida (estrictamente positiva, Req 32.1); escala 3. */
    @Column(name = "cantidad_recibida", nullable = false, updatable = false)
    private BigDecimal cantidadRecibida;

    protected PartidaRecepcion() {
        // Requerido por JPA.
    }

    /**
     * Crea un renglon de recepcion validando la Partida_Orden_Compra, el Material y
     * la cantidad recibida (Req 32.1). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). La asociacion con la
     * {@link RecepcionMercancia} la establece el agregado al agregar la partida.
     *
     * @param partidaOrdenCompraId Partida_Orden_Compra contra la que se recibe;
     *                             obligatoria.
     * @param materialId           Material recibido; obligatorio.
     * @param cantidadRecibida     cantidad recibida; estrictamente positiva.
     * @param actor                identificador de quien crea, para {@code created_by}/
     *                             {@code updated_by}.
     * @return la partida lista para agregar a la recepcion.
     * @throws ReglaNegocioException si falta la partida de OC, el Material o la
     *         cantidad no es positiva (422).
     */
    public static PartidaRecepcion crear(UUID partidaOrdenCompraId, UUID materialId,
                                         BigDecimal cantidadRecibida, String actor) {
        if (partidaOrdenCompraId == null) {
            throw new ReglaNegocioException(
                    "El renglon de recepcion debe referir una Partida_Orden_Compra.");
        }
        if (materialId == null) {
            throw new ReglaNegocioException("El renglon de recepcion debe referir un Material.");
        }
        if (cantidadRecibida == null || cantidadRecibida.signum() <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad recibida del renglon debe ser estrictamente positiva.");
        }
        PartidaRecepcion partida = new PartidaRecepcion();
        partida.id = UUID.randomUUID();
        partida.partidaOrdenCompraId = partidaOrdenCompraId;
        partida.materialId = materialId;
        partida.cantidadRecibida = cantidadRecibida;
        partida.setCreatedBy(actor);
        partida.setUpdatedBy(actor);
        return partida;
    }

    /**
     * Vincula esta partida a su Recepcion_Mercancia contenedora. Uso interno del
     * agregado {@link RecepcionMercancia}.
     *
     * @param recepcion Recepcion_Mercancia contenedora; obligatoria.
     */
    void asignarRecepcion(RecepcionMercancia recepcion) {
        this.recepcion = recepcion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPartidaOrdenCompraId() {
        return partidaOrdenCompraId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public BigDecimal getCantidadRecibida() {
        return cantidadRecibida;
    }
}

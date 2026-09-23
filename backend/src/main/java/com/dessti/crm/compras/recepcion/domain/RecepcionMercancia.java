package com.dessti.crm.compras.recepcion.domain;

import java.time.Instant;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code recepcion_mercancia} (cabecera de una
 * recepcion de mercancia registrada contra una {@code Orden_Compra}, con sus
 * renglones recibidos), mapeada sobre la tabla {@code recepcion_mercancia} de la
 * migracion V29 (Req 32, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y hereda {@code tenant_id} (asignada automaticamente al persistir, Req 23.4),
 * {@code version} (concurrencia optimista, Req 49) y las marcas de auditoria. El
 * mapeo de columnas coincide <em>exactamente</em> con V29. Es analoga a la
 * {@code OrdenCompra} del mismo modulo compras (cabecera + lineas).</p>
 *
 * <h2>Reglas de dominio (Req 32.1)</h2>
 * <ul>
 *   <li>{@link #crear} exige la Orden_Compra de origen y al menos un renglon de
 *       recepcion (Req 32.1); una recepcion sin renglones se rechaza con
 *       {@link ReglaNegocioException} (422).</li>
 *   <li>La precondicion de estado de la Orden_Compra (solo {@code abierta}/
 *       {@code recibida_parcial} admiten recepciones, Req 32.2), el tope acumulado
 *       por partida (no exceder lo ordenado, Req 32.3) y la derivacion del estado
 *       de la Orden_Compra (Req 32.5, 32.6) se aplican en la capa de aplicacion
 *       (ServicioRecepciones) con {@link ReglasRecepcion} (Property 10 y 11).</li>
 * </ul>
 */
@Entity
@Table(name = "recepcion_mercancia")
public class RecepcionMercancia extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Orden_Compra contra la que se registra la recepcion (Req 32.1). Inmutable. */
    @Column(name = "orden_compra_id", nullable = false, updatable = false)
    private UUID ordenCompraId;

    /** Marca UTC de la recepcion (Req 32.1, 32.8). */
    @Column(name = "recibida_en", nullable = false, updatable = false)
    private Instant recibidaEn;

    /**
     * Renglones de la recepcion (relacion uno-a-muchos, hijos del agregado). La FK
     * {@code partida_recepcion.recepcion_mercancia_id} es NOT NULL con ON DELETE
     * CASCADE en V29; JPA persiste/elimina los renglones junto con la recepcion.
     */
    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartidaRecepcion> partidas = new ArrayList<>();

    protected RecepcionMercancia() {
        // Requerido por JPA.
    }

    /**
     * Crea una Recepcion_Mercancia contra una Orden_Compra con al menos un renglon
     * (Req 32.1), fijando la marca temporal de recepcion. El {@code tenant_id} lo
     * fija {@link TenantScopedEntity} al persistir (Req 23.4). La verificacion de la
     * Orden_Compra (existencia y estado admisible, Req 32.2) y del tope acumulado
     * por partida (Req 32.3) las realiza la capa de aplicacion antes/despues de
     * invocar este metodo.
     *
     * @param ordenCompraId identificador de la Orden_Compra de origen; obligatorio.
     * @param recibidaEn    instante UTC de la recepcion; obligatorio.
     * @param partidas      renglones recibidos; al menos uno (Req 32.1).
     * @param actor         identificador de quien registra, para {@code created_by}/
     *                      {@code updated_by}.
     * @return la Recepcion_Mercancia lista para persistir con sus renglones.
     * @throws ReglaNegocioException si falta la Orden_Compra, el instante o no hay
     *         al menos un renglon (422, Req 32.1).
     */
    public static RecepcionMercancia crear(UUID ordenCompraId, Instant recibidaEn,
                                           List<PartidaRecepcion> partidas, String actor) {
        if (ordenCompraId == null) {
            throw new ReglaNegocioException(
                    "La Recepcion_Mercancia debe asociarse a una Orden_Compra existente.");
        }
        if (recibidaEn == null) {
            throw new ReglaNegocioException(
                    "El instante de la Recepcion_Mercancia es obligatorio.");
        }
        if (partidas == null || partidas.isEmpty()) {
            throw new ReglaNegocioException(
                    "La Recepcion_Mercancia debe incluir al menos un renglon recibido.");
        }
        RecepcionMercancia recepcion = new RecepcionMercancia();
        recepcion.id = UUID.randomUUID();
        recepcion.ordenCompraId = ordenCompraId;
        recepcion.recibidaEn = recibidaEn;
        recepcion.partidas = new ArrayList<>();
        for (PartidaRecepcion partida : partidas) {
            recepcion.enlazar(partida);
        }
        recepcion.setCreatedBy(actor);
        recepcion.setUpdatedBy(actor);
        return recepcion;
    }

    private void enlazar(PartidaRecepcion partida) {
        if (partida == null) {
            throw new ReglaNegocioException("El renglon de recepcion es obligatorio.");
        }
        partida.asignarRecepcion(this);
        this.partidas.add(partida);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrdenCompraId() {
        return ordenCompraId;
    }

    public Instant getRecibidaEn() {
        return recibidaEn;
    }

    /**
     * Vista de solo lectura de los renglones de la recepcion.
     *
     * @return lista inmutable de renglones.
     */
    public List<PartidaRecepcion> getPartidas() {
        return Collections.unmodifiableList(partidas);
    }
}

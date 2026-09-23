package com.dessti.crm.comercial.producto.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del {@code precio_producto}: el precio de un {@link Producto}
 * dentro de una {@link ListaPrecios}, mapeada sobre la tabla
 * {@code precio_producto} de la migracion V12 (Req 59.3, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * (hereda {@code tenant_id}, {@code version} y las marcas de auditoria; no se
 * redeclaran). El mapeo coincide <em>exactamente</em> con V12.</p>
 *
 * <h2>Rango de precios (Req 59.3, 59.10; Property 30)</h2>
 * <p>{@link #crear} valida que el precio quede dentro de
 * {@code [0.01, 999,999,999.99]} y lo normaliza a 2 decimales (redondeo al valor
 * mas cercano) mediante {@link CatalogoValidaciones#validarPrecio(BigDecimal)}.
 * Un precio fuera de rango se rechaza con {@link ReglaNegocioException} (422) y
 * no se persiste; el CHECK homonimo en V12 refuerza la invariante en la base de
 * datos.</p>
 *
 * <p>La relacion con {@link Producto} y {@link ListaPrecios} se modela por sus
 * identificadores ({@code producto_id}, {@code lista_precios_id}) siguiendo el
 * estilo del submodulo (agregados enlazados por id, sin navegacion perezosa),
 * de forma coherente con el filtro por tenant y la RLS.</p>
 */
@Entity
@Table(name = "precio_producto")
public class PrecioProducto extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Lista_Precios a la que pertenece este precio (Req 59.3). */
    @Column(name = "lista_precios_id", nullable = false)
    private UUID listaPreciosId;

    /** Producto al que aplica este precio (Req 59.3). */
    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    /** Precio unitario en la moneda unica del sistema, escala 2 (Req 59.3). */
    @Column(name = "precio", nullable = false, precision = 18, scale = 2)
    private BigDecimal precio;

    protected PrecioProducto() {
        // Requerido por JPA.
    }

    /**
     * Crea el precio de un Producto dentro de una Lista_Precios validando el
     * rango 0.01..999,999,999.99 (Req 59.3, 59.10; Property 30). El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir (Req 23.4).
     *
     * @param listaPreciosId identificador de la Lista_Precios; obligatorio.
     * @param productoId     identificador del Producto; obligatorio.
     * @param precio         precio unitario; obligatorio y en rango.
     * @param actor          identificador de quien crea, para la auditoria.
     * @return el precio listo para persistir.
     * @throws ReglaNegocioException si falta la lista/el producto o el precio
     *         esta fuera de rango (422).
     */
    public static PrecioProducto crear(UUID listaPreciosId, UUID productoId,
                                       BigDecimal precio, String actor) {
        if (listaPreciosId == null) {
            throw new ReglaNegocioException("La Lista_Precios del precio es obligatoria.");
        }
        if (productoId == null) {
            throw new ReglaNegocioException("El Producto del precio es obligatorio.");
        }
        PrecioProducto pp = new PrecioProducto();
        pp.id = UUID.randomUUID();
        pp.listaPreciosId = listaPreciosId;
        pp.productoId = productoId;
        pp.precio = CatalogoValidaciones.validarPrecio(precio);
        pp.setCreatedBy(actor);
        pp.setUpdatedBy(actor);
        return pp;
    }

    /**
     * Actualiza el precio revalidando el rango (Req 59.3, 59.10).
     *
     * @param precio nuevo precio; obligatorio y en rango.
     * @param actor  identificador de quien actualiza, para {@code updated_by}.
     * @throws ReglaNegocioException si el precio esta fuera de rango (422).
     */
    public void actualizarPrecio(BigDecimal precio, String actor) {
        this.precio = CatalogoValidaciones.validarPrecio(precio);
        this.setUpdatedBy(actor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getListaPreciosId() {
        return listaPreciosId;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public BigDecimal getPrecio() {
        return precio;
    }
}

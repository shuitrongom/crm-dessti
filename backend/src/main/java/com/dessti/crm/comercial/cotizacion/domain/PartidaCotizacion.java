package com.dessti.crm.comercial.cotizacion.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la {@code partida_cotizacion} (renglon de una {@link Cotizacion}
 * con cantidad, precio unitario y subtotal), mapeada sobre la tabla
 * {@code partida_cotizacion} de la migracion V14 (Req 6.3, 6.4, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity}
 * y por tanto hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde
 * la peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las
 * marcas de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V14.</p>
 *
 * <h2>Reglas de dominio (Req 6.3, 6.4)</h2>
 * <ul>
 *   <li>{@link #crear} valida la cantidad (entero en [1, 999,999]) y el precio
 *       unitario (en [0.01, 999,999,999.99], escala 2 half-up); una entrada fuera
 *       de rango se rechaza con {@link com.dessti.crm.platform.error.ReglaNegocioException}
 *       (422, Property 3) y no se calcula subtotal.</li>
 *   <li>El subtotal se calcula como {@code round(cantidad * precio_unitario, 2)}
 *       half-up (Req 6.3; Property 2).</li>
 * </ul>
 *
 * <p>La partida es una entidad hija del agregado {@link Cotizacion}: se crea y se
 * gestiona a traves de la Cotizacion, que mantiene coherentes los totales.</p>
 */
@Entity
@Table(name = "partida_cotizacion")
public class PartidaCotizacion extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Cotizacion a la que pertenece la partida (relacion muchos-a-uno). La FK
     * {@code cotizacion_id} es NOT NULL en V14. Se gestiona desde el agregado
     * {@link Cotizacion} al agregar la partida.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "cotizacion_id", nullable = false, updatable = false)
    private Cotizacion cotizacion;

    /**
     * Producto referido por la partida para sugerir su precio (Req 59.4);
     * {@code null} si la partida es de texto libre.
     */
    @Column(name = "producto_id")
    private UUID productoId;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    /** Cantidad entera en [1, 999,999] (Req 6.3, 6.4). */
    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** Precio unitario en [0.01, 999,999,999.99]; escala 2 (Req 6.3, 6.4). */
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    /**
     * Descuento (monto) aplicado a la partida antes de calcular su IVA; escala 2,
     * no negativo y acotado al importe bruto (V80). Por defecto 0.
     */
    @Column(name = "descuento", nullable = false)
    private BigDecimal descuento;

    /** Tasa de IVA de la partida; se persiste como etiqueta ASCII (V80). */
    @Convert(converter = TasaIvaConverter.class)
    @Column(name = "tasa_iva", nullable = false)
    private TasaIva tasaIva;

    /**
     * Importe bruto de la partida = round(cantidad * precio_unitario, 2), antes de
     * descuento (V80). Se persiste para el desglose fiscal del PDF y los reportes.
     */
    @Column(name = "importe_base", nullable = false)
    private BigDecimal importeBase;

    /** IVA de la partida = round(baseNeta * tasa, 2); escala 2 (V80). */
    @Column(name = "iva", nullable = false)
    private BigDecimal iva;

    /**
     * Base neta de la partida = importe bruto - descuento; escala 2 half-up. Es la
     * base sobre la que se calcula el IVA y la que suma al subtotal de la
     * Cotizacion. Se conserva el nombre de columna {@code subtotal} de V14 con su
     * significado enriquecido (base neta) para no romper el esquema existente.
     */
    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    protected PartidaCotizacion() {
        // Requerido por JPA.
    }

    /**
     * Crea una partida validando la cantidad y el precio (Req 6.3, 6.4) y
     * calculando su subtotal (Req 6.3; Property 2). El {@code tenant_id} lo fija
     * {@link TenantScopedEntity} al persistir (Req 23.4). La asociacion con la
     * {@link Cotizacion} la establece el agregado al agregar la partida.
     *
     * @param productoId     Producto referido; puede ser {@code null} (texto libre).
     * @param descripcion    descripcion de la partida; obligatoria (1..500).
     * @param cantidad       cantidad; entero en [1, 999,999].
     * @param precioUnitario precio unitario; en [0.01, 999,999,999.99].
     * @param actor          identificador de quien crea, para {@code created_by}/
     *                       {@code updated_by}.
     * @return la partida lista para agregar a la Cotizacion, con su subtotal.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si la cantidad
     *         o el precio estan fuera de rango, o la descripcion es invalida (422,
     *         Property 3).
     */
    public static PartidaCotizacion crear(UUID productoId, String descripcion, int cantidad,
                                          BigDecimal precioUnitario, String actor) {
        return crear(productoId, descripcion, cantidad, precioUnitario,
                BigDecimal.ZERO, TasaIva.POR_DEFECTO, actor);
    }

    /**
     * Crea una partida con desglose fiscal (V80): valida cantidad y precio
     * (Req 6.3, 6.4), aplica el descuento de partida (acotado al importe bruto) y
     * la tasa de IVA, y calcula el importe bruto, la base neta y el IVA. El
     * {@code tenant_id} lo fija {@link TenantScopedEntity} al persistir.
     *
     * @param productoId     Producto referido; puede ser {@code null} (texto libre).
     * @param descripcion    descripcion de la partida; obligatoria (1..500).
     * @param cantidad       cantidad; entero en [1, 999,999].
     * @param precioUnitario precio unitario; en [0.01, 999,999,999.99].
     * @param descuento      descuento (monto) de la partida; {@code null}=0, no
     *                       negativo, no mayor que el importe bruto.
     * @param tasaIva        tasa de IVA de la partida; {@code null} usa 16%.
     * @param actor          identificador de quien crea.
     * @return la partida lista para agregar a la Cotizacion, con su desglose fiscal.
     * @throws com.dessti.crm.platform.error.ReglaNegocioException si algun valor es
     *         invalido (422).
     */
    public static PartidaCotizacion crear(UUID productoId, String descripcion, int cantidad,
                                          BigDecimal precioUnitario, BigDecimal descuento,
                                          TasaIva tasaIva, String actor) {
        PartidaCotizacion partida = new PartidaCotizacion();
        partida.id = UUID.randomUUID();
        partida.productoId = productoId;
        partida.descripcion = CotizacionValidaciones.normalizarDescripcion(descripcion);
        partida.cantidad = CotizacionValidaciones.validarCantidad(cantidad);
        partida.precioUnitario = CotizacionValidaciones.validarPrecioUnitario(precioUnitario);
        partida.tasaIva = (tasaIva == null) ? TasaIva.POR_DEFECTO : tasaIva;
        partida.importeBase = CotizacionValidaciones.calcularImporteBruto(
                partida.cantidad, partida.precioUnitario);
        partida.descuento = CotizacionValidaciones.validarDescuentoPartida(
                descuento, partida.importeBase);
        partida.subtotal = CotizacionValidaciones.calcularBaseNetaPartida(
                partida.importeBase, partida.descuento);
        partida.iva = CotizacionValidaciones.calcularIvaPartida(partida.subtotal, partida.tasaIva);
        partida.setCreatedBy(actor);
        partida.setUpdatedBy(actor);
        return partida;
    }

    /**
     * Vincula esta partida a su Cotizacion contenedora. Uso interno del agregado
     * {@link Cotizacion}.
     *
     * @param cotizacion Cotizacion contenedora; obligatoria.
     */
    void asignarCotizacion(Cotizacion cotizacion) {
        this.cotizacion = cotizacion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public TasaIva getTasaIva() {
        return tasaIva;
    }

    /** Importe bruto de la partida (cantidad * precio) antes de descuento (V80). */
    public BigDecimal getImporteBase() {
        return importeBase;
    }

    /** IVA de la partida sobre su base neta (V80). */
    public BigDecimal getIva() {
        return iva;
    }

    /** Base neta de la partida (importe bruto - descuento); base del IVA (V80). */
    public BigDecimal getSubtotal() {
        return subtotal;
    }
}

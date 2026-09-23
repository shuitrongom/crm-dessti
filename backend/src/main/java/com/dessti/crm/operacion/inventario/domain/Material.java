package com.dessti.crm.operacion.inventario.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA y raiz del agregado {@code material}: un Material del inventario y su
 * saldo vivo de existencias (Req 18), mapeada sobre la tabla {@code material} de la
 * migracion V18 (Req 18, 23).
 *
 * <p><strong>Multi-tenant (Req 23):</strong> extiende {@link TenantScopedEntity} y
 * hereda {@code tenant_id} (asignada automaticamente desde el
 * {@link com.dessti.crm.platform.tenant.TenantContext} al persistir, nunca desde la
 * peticion, Req 23.4), {@code version} (concurrencia optimista, Req 49) y las marcas
 * de auditoria. El mapeo de columnas coincide <em>exactamente</em> con V18.</p>
 *
 * <h2>Reglas de dominio (Req 18)</h2>
 * <ul>
 *   <li>{@link #crear(String, String, BigDecimal, String)} da de alta un Material con
 *       sus datos obligatorios validados (nombre 1..200, unidad de medida y stock
 *       minimo &gt;= 0) y <strong>existencias iniciales 0</strong> (Req 18.1).</li>
 *   <li>{@link #aplicarMovimiento(TipoMovimientoInventario, BigDecimal)} es el motor
 *       de inventario PURO (Req 18.2): una {@code entrada} suma, una {@code salida}
 *       resta y un {@code ajuste} aplica un delta con signo. Una salida (o un ajuste
 *       negativo) que dejaria las existencias por debajo de 0 se rechaza con
 *       {@link ReglaNegocioException} (422) y las existencias <strong>se conservan sin
 *       cambios</strong> (Req 18.3, Property 9). Devuelve un {@link ResultadoMovimiento}
 *       con las existencias resultantes y si el Material quedo en stock bajo (Req 18.5).</li>
 *   <li>{@link #desactivar()} realiza la baja logica del Material (Req 18, 3.1).</li>
 * </ul>
 *
 * <h2>No negatividad (Property 9)</h2>
 * <p>La invariante "las existencias nunca son negativas" se impone aqui, en el dominio,
 * como fuente de verdad (mensaje "existencias insuficientes"), y la BD la refuerza con
 * {@code CHECK (existencias >= 0)} en V18 como segunda capa de defensa. Las cantidades
 * se manejan con {@link BigDecimal} a escala 3, coherente con {@code NUMERIC(18,3)}.</p>
 */
@Entity
@Table(name = "material")
public class Material extends TenantScopedEntity {

    /** Escala decimal de las cantidades de Material, coherente con NUMERIC(18,3) de V18. */
    public static final int ESCALA_CANTIDAD = 3;

    /** Longitud maxima del nombre (Req 18.1), coherente con VARCHAR(200) de V18. */
    private static final int NOMBRE_MAX = 200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Nombre del Material, entre 1 y 200 caracteres (Req 18.1). */
    @Column(name = "nombre", nullable = false, length = NOMBRE_MAX)
    private String nombre;

    /** Unidad de medida (por ejemplo "pieza", "metro", "kg"); obligatoria (Req 18.1). */
    @Column(name = "unidad_medida", nullable = false, length = 50)
    private String unidadMedida;

    /** Stock minimo para la deteccion de stock bajo; &gt;= 0 (Req 18.1, 18.5). */
    @Column(name = "stock_minimo", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal stockMinimo;

    /** Saldo vivo de existencias (inventario perpetuo, Req 18.2); nunca negativo (Property 9). */
    @Column(name = "existencias", nullable = false, precision = 18, scale = ESCALA_CANTIDAD)
    private BigDecimal existencias;

    /** Indicador de baja logica (Req 18, 3.1); {@code true} mientras el Material esta activo. */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Material() {
        // Requerido por JPA.
    }

    /**
     * Da de alta un Material con sus datos obligatorios validados y existencias
     * iniciales en 0 (Req 18.1). El {@code tenant_id} lo fija {@link TenantScopedEntity}
     * al persistir (Req 23.4).
     *
     * @param nombre       nombre del Material; obligatorio, 1..200 caracteres (se recorta).
     * @param unidadMedida unidad de medida; obligatoria.
     * @param stockMinimo  stock minimo; obligatorio y &gt;= 0.
     * @param actor        identificador de quien da de alta, para {@code created_by}/
     *                     {@code updated_by}.
     * @return el Material listo para persistir, activo y con existencias 0.
     * @throws ReglaNegocioException si algun dato obligatorio falta o es invalido (422).
     */
    public static Material crear(String nombre, String unidadMedida, BigDecimal stockMinimo,
                                 String actor) {
        String nombreNormalizado = normalizarNombre(nombre);
        String unidadNormalizada = normalizarUnidad(unidadMedida);
        BigDecimal minimo = normalizarStockMinimo(stockMinimo);

        Material material = new Material();
        material.id = UUID.randomUUID();
        material.nombre = nombreNormalizado;
        material.unidadMedida = unidadNormalizada;
        material.stockMinimo = minimo;
        material.existencias = ceroEscalado();
        material.activo = true;
        material.setCreatedBy(actor);
        material.setUpdatedBy(actor);
        return material;
    }

    /**
     * Aplica un movimiento de inventario sobre este Material y actualiza sus
     * existencias (Req 18.2). Motor de inventario PURO y determinista:
     *
     * <ul>
     *   <li>{@code entrada}: existencias += cantidad.</li>
     *   <li>{@code salida}: existencias -= cantidad; si el resultado seria &lt; 0 se
     *       rechaza (Req 18.3, Property 9).</li>
     *   <li>{@code ajuste}: existencias += cantidad (delta con signo: positivo suma,
     *       negativo resta); si el resultado seria &lt; 0 se rechaza (Property 9).</li>
     * </ul>
     *
     * <p>En caso de rechazo, las existencias <strong>se conservan sin modificarlas</strong>
     * (la excepcion se lanza antes de mutar el estado, Req 18.3). En caso de exito, muta
     * {@code existencias} y devuelve el {@link ResultadoMovimiento} con el nuevo saldo y
     * si quedo por debajo del stock minimo (Req 18.5).</p>
     *
     * @param tipo     tipo de movimiento; obligatorio (Req 18.2).
     * @param cantidad cantidad del movimiento. Para {@code entrada}/{@code salida} debe
     *                 ser estrictamente positiva; para {@code ajuste} debe ser distinta
     *                 de cero (el signo indica el sentido).
     * @param actor    identificador de quien registra, para {@code updated_by}.
     * @return el resultado con las existencias resultantes y el indicador de stock bajo.
     * @throws ReglaNegocioException si el tipo o la cantidad son invalidos, o si una
     *         salida/ajuste dejaria las existencias por debajo de 0 (422).
     */
    public ResultadoMovimiento aplicarMovimiento(TipoMovimientoInventario tipo, BigDecimal cantidad,
                                                 String actor) {
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de Movimiento_Inventario es obligatorio.");
        }
        if (cantidad == null) {
            throw new ReglaNegocioException("La cantidad del Movimiento_Inventario es obligatoria.");
        }
        BigDecimal delta = calcularDelta(tipo, cantidad);
        BigDecimal resultado = this.existencias.add(delta);
        if (resultado.signum() < 0) {
            // Property 9 / Req 18.3: se rechaza y NO se modifican las existencias.
            throw new ReglaNegocioException("existencias insuficientes");
        }
        this.existencias = resultado.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
        this.setUpdatedBy(actor);
        return new ResultadoMovimiento(this.existencias, estaEnStockBajo());
    }

    /**
     * Da de baja logica el Material (Req 18, 3.1). Idempotente: desactivar un Material
     * ya inactivo no tiene efecto adicional.
     *
     * @param actor identificador de quien realiza la baja, para {@code updated_by}.
     */
    public void desactivar(String actor) {
        this.activo = false;
        this.setUpdatedBy(actor);
    }

    /**
     * Indica si el Material esta en condicion de stock bajo, esto es, si sus
     * existencias son estrictamente menores que su stock minimo (Req 18.5).
     *
     * @return {@code true} si {@code existencias < stock_minimo}.
     */
    public boolean estaEnStockBajo() {
        return this.existencias.compareTo(this.stockMinimo) < 0;
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private static BigDecimal calcularDelta(TipoMovimientoInventario tipo, BigDecimal cantidad) {
        return switch (tipo) {
            case ENTRADA -> {
                exigirPositiva(cantidad, "entrada");
                yield cantidad;
            }
            case SALIDA -> {
                exigirPositiva(cantidad, "salida");
                yield cantidad.negate();
            }
            case AJUSTE -> {
                if (cantidad.signum() == 0) {
                    throw new ReglaNegocioException(
                            "La cantidad de un ajuste de inventario debe ser distinta de cero.");
                }
                yield cantidad;
            }
        };
    }

    private static void exigirPositiva(BigDecimal cantidad, String tipo) {
        if (cantidad.signum() <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad de una " + tipo + " de inventario debe ser mayor que 0.");
        }
    }

    private static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del Material es obligatorio.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > NOMBRE_MAX) {
            throw new ReglaNegocioException(
                    "El nombre del Material no puede exceder " + NOMBRE_MAX + " caracteres.");
        }
        return limpio;
    }

    private static String normalizarUnidad(String unidadMedida) {
        if (unidadMedida == null || unidadMedida.isBlank()) {
            throw new ReglaNegocioException("La unidad de medida del Material es obligatoria.");
        }
        return unidadMedida.trim();
    }

    private static BigDecimal normalizarStockMinimo(BigDecimal stockMinimo) {
        if (stockMinimo == null) {
            throw new ReglaNegocioException("El stock minimo del Material es obligatorio.");
        }
        if (stockMinimo.signum() < 0) {
            throw new ReglaNegocioException("El stock minimo del Material debe ser mayor o igual a 0.");
        }
        return stockMinimo.setScale(ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    }

    private static BigDecimal ceroEscalado() {
        return BigDecimal.ZERO.setScale(ESCALA_CANTIDAD);
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public BigDecimal getStockMinimo() {
        return stockMinimo;
    }

    public BigDecimal getExistencias() {
        return existencias;
    }

    public boolean isActivo() {
        return activo;
    }
}

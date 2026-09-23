package com.dessti.crm.operacion.inventario.avanzado.domain;

/**
 * Tipos de {@link MovimientoAlmacen} (Kardex por Almacen) admitidos por el inventario
 * avanzado del Req 60. Cada constante conoce su etiqueta ASCII persistida en la columna
 * {@code movimiento_almacen.tipo} (VARCHAR con CHECK {@code IN ('entrada','salida',
 * 'transferencia_salida','transferencia_entrada','ajuste')} de la migracion V26),
 * coherente con la convencion de {@link com.dessti.crm.operacion.inventario.domain.TipoMovimientoInventario}.
 *
 * <h2>Semantica (Req 60)</h2>
 * <ul>
 *   <li>{@link #ENTRADA}: ingreso de material a un Almacen (suma al saldo).</li>
 *   <li>{@link #SALIDA}: egreso de material de un Almacen (resta del saldo).</li>
 *   <li>{@link #TRANSFERENCIA_SALIDA}/{@link #TRANSFERENCIA_ENTRADA}: las dos patas de
 *       una transferencia entre Almacenes, agrupadas por {@code transferencia_id}.</li>
 *   <li>{@link #AJUSTE}: ajuste de inventario del Almacen.</li>
 * </ul>
 *
 * <p><strong>Alcance:</strong> este enum se define en la tarea 23.1 y da soporte a la
 * lectura del Kardex; el registro efectivo de movimientos con costeo, lotes y las
 * transferencias entre Almacenes se implementa en la tarea 23.2.</p>
 */
public enum TipoMovimientoAlmacen {

    /** Ingreso de material a un Almacen: suma al saldo (Req 60). */
    ENTRADA("entrada"),

    /** Egreso de material de un Almacen: resta del saldo (Req 60). */
    SALIDA("salida"),

    /** Pata de salida de una transferencia entre Almacenes (Req 60). */
    TRANSFERENCIA_SALIDA("transferencia_salida"),

    /** Pata de entrada de una transferencia entre Almacenes (Req 60). */
    TRANSFERENCIA_ENTRADA("transferencia_entrada"),

    /** Ajuste de inventario del Almacen con conciliacion (Req 60). */
    AJUSTE("ajuste");

    private final String valorBd;

    TipoMovimientoAlmacen(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V26).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "transferencia_salida"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta persistida.
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si la etiqueta no corresponde a ningun tipo.
     */
    public static TipoMovimientoAlmacen desdeValorBd(String valor) {
        for (TipoMovimientoAlmacen tipo : values()) {
            if (tipo.valorBd.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Movimiento_Almacen desconocido: " + valor);
    }
}

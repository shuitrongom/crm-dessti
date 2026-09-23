package com.dessti.crm.operacion.inventario.domain;

/**
 * Tipos de {@link MovimientoInventario} admitidos por el inventario de Materiales
 * del Req 18.2: {@code entrada}, {@code salida} y {@code ajuste}. Cada constante
 * conoce su etiqueta ASCII persistida en la columna {@code movimiento_inventario.tipo}
 * (VARCHAR con CHECK {@code IN ('entrada','salida','ajuste')} de la migracion V18),
 * coherente con la convencion de etiquetas de V14/V16/V17.
 *
 * <h2>Semantica sobre las existencias (Req 18.2)</h2>
 * <ul>
 *   <li>{@link #ENTRADA}: suma la cantidad del movimiento a las existencias.</li>
 *   <li>{@link #SALIDA}: resta la cantidad de las existencias; una salida que
 *       dejaria las existencias por debajo de 0 se rechaza (Req 18.3, Property 9).</li>
 *   <li>{@link #AJUSTE}: aplica un ajuste con signo (positivo suma, negativo resta)
 *       para conciliar diferencias de inventario; tampoco puede dejar las
 *       existencias por debajo de 0 (Property 9).</li>
 * </ul>
 */
public enum TipoMovimientoInventario {

    /** Ingreso de material: suma a las existencias (Req 18.2). */
    ENTRADA("entrada"),

    /** Consumo/egreso de material: resta de las existencias (Req 18.2, 18.3, 18.4). */
    SALIDA("salida"),

    /** Ajuste de inventario con signo (conciliacion), acotado a existencias >= 0. */
    AJUSTE("ajuste");

    private final String valorBd;

    TipoMovimientoInventario(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V18).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "salida"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta persistida ({@code entrada}, {@code salida}, {@code ajuste}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si la etiqueta no corresponde a ningun tipo.
     */
    public static TipoMovimientoInventario desdeValorBd(String valor) {
        for (TipoMovimientoInventario tipo : values()) {
            if (tipo.valorBd.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Movimiento_Inventario desconocido: " + valor);
    }
}

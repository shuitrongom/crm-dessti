package com.dessti.crm.operacion.inventario.avanzado.domain;

/**
 * Metodos de costeo de inventario admitidos por el inventario avanzado por Almacen
 * del Req 60: {@code promedio} (costo promedio ponderado) y {@code peps} (primeras
 * entradas, primeras salidas / FIFO). Cada constante conoce su etiqueta ASCII
 * persistida en la columna {@code config_inventario_material.metodo_costeo} (VARCHAR
 * con CHECK {@code IN ('promedio','peps')} de la migracion V26), coherente con la
 * convencion de etiquetas de {@link TipoMovimientoAlmacen} y de V17/V18.
 *
 * <h2>Semantica del costeo (Req 60)</h2>
 * <ul>
 *   <li>{@link #PROMEDIO}: el costo unitario se recalcula como promedio ponderado de
 *       las existencias tras cada entrada.</li>
 *   <li>{@link #PEPS}: las salidas consumen primero las capas de costo mas antiguas
 *       (primeras entradas), preservando el costo historico por capa.</li>
 * </ul>
 *
 * <p><strong>Alcance:</strong> este enum se define en la tarea 23.1; el motor que
 * aplica la matematica del costeo (promedio ponderado y consumo de capas PEPS) llega
 * en la tarea 23.2.</p>
 */
public enum MetodoCosteo {

    /** Costo promedio ponderado: se recalcula el promedio tras cada entrada (Req 60). */
    PROMEDIO("promedio"),

    /** Primeras entradas, primeras salidas (FIFO): consume las capas mas antiguas (Req 60). */
    PEPS("peps");

    private final String valorBd;

    MetodoCosteo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V26).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "promedio"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve el metodo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta persistida ({@code promedio} o {@code peps}).
     * @return el metodo correspondiente.
     * @throws IllegalArgumentException si la etiqueta no corresponde a ningun metodo.
     */
    public static MetodoCosteo desdeValorBd(String valor) {
        for (MetodoCosteo metodo : values()) {
            if (metodo.valorBd.equals(valor)) {
                return metodo;
            }
        }
        throw new IllegalArgumentException("Metodo de costeo desconocido: " + valor);
    }
}

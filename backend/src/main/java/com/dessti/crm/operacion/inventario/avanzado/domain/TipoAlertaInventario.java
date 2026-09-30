package com.dessti.crm.operacion.inventario.avanzado.domain;

/**
 * Tipos de {@link AlertaInventario} persistibles (Req 60), coherentes con el CHECK de la
 * columna {@code alerta_inventario.tipo} de la migracion V88
 * ({@code IN ('minimo','maximo','reabastecimiento')}).
 *
 * <ul>
 *   <li>{@link #MINIMO}: el saldo cayo en o por debajo del punto de reorden.</li>
 *   <li>{@link #MAXIMO}: el saldo supero el stock maximo configurado.</li>
 *   <li>{@link #REABASTECIMIENTO}: sugerencia de reponer al cruzar el punto de reorden.</li>
 * </ul>
 */
public enum TipoAlertaInventario {

    /** El saldo cayo en o por debajo del punto de reorden (Req 60). */
    MINIMO("minimo"),

    /** El saldo supero el stock maximo configurado (Req 60). */
    MAXIMO("maximo"),

    /** Sugerencia de reabastecimiento al cruzar el punto de reorden (Req 60). */
    REABASTECIMIENTO("reabastecimiento");

    private final String valorBd;

    TipoAlertaInventario(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V88).
     *
     * @return la etiqueta de base de datos.
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
    public static TipoAlertaInventario desdeValorBd(String valor) {
        for (TipoAlertaInventario tipo : values()) {
            if (tipo.valorBd.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de AlertaInventario desconocido: " + valor);
    }
}

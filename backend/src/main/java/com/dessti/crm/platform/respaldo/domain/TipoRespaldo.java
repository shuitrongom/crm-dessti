package com.dessti.crm.platform.respaldo.domain;

/**
 * Tipo de operacion registrada en la bitacora de {@link Respaldo} (Req 50).
 */
public enum TipoRespaldo {

    /** Ejecucion de un respaldo (volcado cifrado de datos). */
    RESPALDO("respaldo"),

    /** Ejecucion de una restauracion a partir de un respaldo previo (Req 50.2). */
    RESTAURACION("restauracion");

    private final String valor;

    TipoRespaldo(String valor) {
        this.valor = valor;
    }

    /**
     * @return valor persistido en la columna {@code tipo} (coincide con el
     *         CHECK de la migracion V46).
     */
    public String valor() {
        return valor;
    }

    /**
     * Resuelve el tipo desde su valor persistido.
     *
     * @param valor valor de la columna {@code tipo}.
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor no corresponde a ningun tipo.
     */
    public static TipoRespaldo desde(String valor) {
        for (TipoRespaldo t : values()) {
            if (t.valor.equals(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de respaldo desconocido: " + valor);
    }
}

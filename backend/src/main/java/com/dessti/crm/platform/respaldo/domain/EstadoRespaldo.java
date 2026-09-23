package com.dessti.crm.platform.respaldo.domain;

/**
 * Estado del ciclo de vida de una ejecucion de {@link Respaldo} (Req 50).
 */
public enum EstadoRespaldo {

    /** La operacion esta en curso. */
    EN_PROCESO("en_proceso"),

    /** La operacion termino con exito. */
    COMPLETADO("completado"),

    /** La operacion fallo; el detalle del error queda registrado (sin secretos). */
    FALLIDO("fallido");

    private final String valor;

    EstadoRespaldo(String valor) {
        this.valor = valor;
    }

    /**
     * @return valor persistido en la columna {@code estado} (coincide con el
     *         CHECK de la migracion V46).
     */
    public String valor() {
        return valor;
    }

    /**
     * Resuelve el estado desde su valor persistido.
     *
     * @param valor valor de la columna {@code estado}.
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor no corresponde a ningun estado.
     */
    public static EstadoRespaldo desde(String valor) {
        for (EstadoRespaldo e : values()) {
            if (e.valor.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de respaldo desconocido: " + valor);
    }
}

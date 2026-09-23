package com.dessti.crm.tesoreria.domain;

import java.util.Locale;

/**
 * Estado de una {@link ConciliacionBancaria} (Req 43.5). La etiqueta persistida en
 * la columna {@code conciliacion_bancaria.estado} es ASCII en minusculas, tal como
 * exige el CHECK de la migracion V35.
 *
 * <h2>Regla de negocio (Req 43.5; Property 18)</h2>
 * <ul>
 *   <li>{@link #EN_PROCESO}: hay partidas sin explicar (movimientos en excepcion)
 *       o la diferencia entre el saldo bancario y el saldo contable no es cero.</li>
 *   <li>{@link #COMPLETA}: la conciliacion queda COMPLETA <strong>solo</strong>
 *       cuando la diferencia es cero una vez explicadas todas las partidas. La
 *       decision es una funcion PURA del dominio
 *       ({@link ReglasConciliacion#esCompleta(java.math.BigDecimal, int)}).</li>
 * </ul>
 */
public enum EstadoConciliacionBancaria {

    /** Conciliacion con partidas sin explicar o diferencia distinta de cero. */
    EN_PROCESO("en_proceso"),

    /** Conciliacion completa: diferencia cero sin partidas pendientes (Req 43.5). */
    COMPLETA("completa");

    private final String valorBd;

    EstadoConciliacionBancaria(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code conciliacion_bancaria.estado}.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'completa'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoConciliacionBancaria desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    "El estado de la Conciliacion_Bancaria no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoConciliacionBancaria estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException(
                "Estado de la Conciliacion_Bancaria desconocido: " + valor);
    }
}

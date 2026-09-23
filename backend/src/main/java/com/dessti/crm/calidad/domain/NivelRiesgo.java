package com.dessti.crm.calidad.domain;

import java.util.Locale;

/**
 * Nivel <strong>derivado</strong> de un {@link Riesgo} (Req 70.3, clausula 6.1.2). Es
 * de <em>solo lectura</em> para el cliente del API: su valor lo calcula la funcion
 * pura {@link #derivar(Probabilidad, Impacto)} a partir de la matriz probabilidad x
 * impacto y se persiste en {@code riesgo.nivel_derivado} conforme al CHECK de V47.
 *
 * <h2>Matriz de derivacion (producto de pesos 1..3)</h2>
 * <pre>
 *   producto    nivel
 *   1           bajo
 *   2           medio
 *   3, 4        alto
 *   6, 9        critico
 * </pre>
 * <p>El producto de {@link Probabilidad#peso()} por {@link Impacto#peso()} toma los
 * valores {1,2,3,4,6,9}; el valor 5, 7 y 8 no son alcanzables con pesos 1..3.</p>
 */
public enum NivelRiesgo {

    /** Nivel bajo (producto 1). */
    BAJO("bajo"),

    /** Nivel medio (producto 2). */
    MEDIO("medio"),

    /** Nivel alto (producto 3 o 4). */
    ALTO("alto"),

    /** Nivel critico (producto 6 o 9). */
    CRITICO("critico");

    private final String valorBd;

    NivelRiesgo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code riesgo.nivel_derivado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Deriva de forma <strong>pura</strong> el nivel del riesgo a partir de la
     * probabilidad y el impacto (Req 70.3). El resultado depende solo del producto de
     * sus pesos: es determinista y sin efectos secundarios.
     *
     * @param probabilidad probabilidad del riesgo; obligatoria.
     * @param impacto      impacto del riesgo; obligatorio.
     * @return el nivel derivado.
     * @throws IllegalArgumentException si algun argumento es nulo.
     */
    public static NivelRiesgo derivar(Probabilidad probabilidad, Impacto impacto) {
        if (probabilidad == null) {
            throw new IllegalArgumentException("La probabilidad del Riesgo es obligatoria");
        }
        if (impacto == null) {
            throw new IllegalArgumentException("El impacto del Riesgo es obligatorio");
        }
        int producto = probabilidad.peso() * impacto.peso();
        if (producto <= 1) {
            return BAJO;
        }
        if (producto == 2) {
            return MEDIO;
        }
        if (producto <= 4) {
            return ALTO;
        }
        return CRITICO;
    }

    /**
     * Reconstruye el nivel a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'critico'}).
     * @return el nivel correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static NivelRiesgo desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El nivel derivado del Riesgo no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (NivelRiesgo nivel : values()) {
            if (nivel.valorBd.equals(normalizado)) {
                return nivel;
            }
        }
        throw new IllegalArgumentException("Nivel de Riesgo desconocido: " + valor);
    }
}

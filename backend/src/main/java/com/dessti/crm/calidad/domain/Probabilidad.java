package com.dessti.crm.calidad.domain;

import java.util.Locale;

/**
 * Probabilidad de un {@link Riesgo} (Req 70.3, clausula 6.1.2). Etiquetas ASCII en
 * minusculas persistidas en {@code riesgo.probabilidad} conforme al CHECK de V47. El
 * {@link #peso()} ordena la escala para derivar el nivel del riesgo (matriz prob x
 * impacto).
 */
public enum Probabilidad {

    /** Probabilidad baja (peso 1). */
    BAJA("baja", 1),

    /** Probabilidad media (peso 2). */
    MEDIA("media", 2),

    /** Probabilidad alta (peso 3). */
    ALTA("alta", 3);

    private final String valorBd;
    private final int peso;

    Probabilidad(String valorBd, int peso) {
        this.valorBd = valorBd;
        this.peso = peso;
    }

    /**
     * Etiqueta persistida en {@code riesgo.probabilidad}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Peso ordinal de la probabilidad (1..3) para la matriz de derivacion del nivel.
     *
     * @return el peso de la probabilidad.
     */
    public int peso() {
        return peso;
    }

    /**
     * Reconstruye la probabilidad a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'alta'}).
     * @return la probabilidad correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static Probabilidad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("La probabilidad del Riesgo no puede ser nula");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (Probabilidad probabilidad : values()) {
            if (probabilidad.valorBd.equals(normalizado)) {
                return probabilidad;
            }
        }
        throw new IllegalArgumentException("Probabilidad de Riesgo desconocida: " + valor);
    }
}

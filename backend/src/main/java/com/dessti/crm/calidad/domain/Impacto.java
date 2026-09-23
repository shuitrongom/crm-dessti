package com.dessti.crm.calidad.domain;

import java.util.Locale;

/**
 * Impacto de un {@link Riesgo} (Req 70.3, clausula 6.1.2). Etiquetas ASCII en
 * minusculas persistidas en {@code riesgo.impacto} conforme al CHECK de V47. El
 * {@link #peso()} ordena la escala para derivar el nivel del riesgo (matriz prob x
 * impacto).
 */
public enum Impacto {

    /** Impacto bajo (peso 1). */
    BAJO("bajo", 1),

    /** Impacto medio (peso 2). */
    MEDIO("medio", 2),

    /** Impacto alto (peso 3). */
    ALTO("alto", 3);

    private final String valorBd;
    private final int peso;

    Impacto(String valorBd, int peso) {
        this.valorBd = valorBd;
        this.peso = peso;
    }

    /**
     * Etiqueta persistida en {@code riesgo.impacto}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Peso ordinal del impacto (1..3) para la matriz de derivacion del nivel.
     *
     * @return el peso del impacto.
     */
    public int peso() {
        return peso;
    }

    /**
     * Reconstruye el impacto a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'alto'}).
     * @return el impacto correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static Impacto desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El impacto del Riesgo no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (Impacto impacto : values()) {
            if (impacto.valorBd.equals(normalizado)) {
                return impacto;
            }
        }
        throw new IllegalArgumentException("Impacto de Riesgo desconocido: " + valor);
    }
}

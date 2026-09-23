package com.dessti.crm.calidad.domain;

import java.util.Locale;

/**
 * Tipo de cuestion de un {@link ContextoOrganizacion} (Req 70.5, clausula 4.1).
 * Etiquetas ASCII en minusculas persistidas en {@code contexto_organizacion.tipo}
 * conforme al CHECK de V47.
 */
public enum TipoContexto {

    /** Cuestion interna del contexto de la organizacion (clausula 4.1). */
    INTERNA("interna"),

    /** Cuestion externa del contexto de la organizacion (clausula 4.1). */
    EXTERNA("externa");

    private final String valorBd;

    TipoContexto(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code contexto_organizacion.tipo}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del tipo.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'interna'}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static TipoContexto desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo del Contexto_Organizacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoContexto tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Contexto_Organizacion desconocido: " + valor);
    }
}

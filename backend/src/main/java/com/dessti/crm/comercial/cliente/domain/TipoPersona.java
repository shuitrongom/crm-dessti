package com.dessti.crm.comercial.cliente.domain;

import java.util.Locale;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Tipo de persona del {@link Cliente} para efectos fiscales (Req 5): persona
 * {@link #FISICA fisica} o persona {@link #MORAL moral}.
 *
 * <p>Es un dato <strong>opcional</strong> del Cliente. Se persiste como su
 * {@link #clave() clave} en minusculas ({@code 'fisica'} / {@code 'moral'}),
 * coherente con la columna {@code cliente.tipo_persona VARCHAR(20)} y su
 * restriccion {@code ck_cliente_tipo_persona} (V59).</p>
 */
public enum TipoPersona {

    /** Persona fisica (RFC de 13 caracteres). */
    FISICA("fisica"),

    /** Persona moral (RFC de 12 caracteres). */
    MORAL("moral");

    private final String clave;

    TipoPersona(String clave) {
        this.clave = clave;
    }

    /**
     * Clave persistida en minusculas ({@code 'fisica'} / {@code 'moral'}).
     *
     * @return la clave de almacenamiento.
     */
    public String clave() {
        return clave;
    }

    /**
     * Interpreta un valor de entrada opcional como {@link TipoPersona},
     * tolerando espacios y mayusculas/minusculas (Req 5). Un valor nulo o en
     * blanco se interpreta como ausencia de dato y devuelve {@code null}.
     *
     * @param valor texto a interpretar; puede ser {@code null}.
     * @return el {@link TipoPersona} correspondiente, o {@code null} si no se
     *         proporciono.
     * @throws ReglaNegocioException si el valor no es {@code 'fisica'} ni
     *                               {@code 'moral'} (HTTP 422).
     */
    public static TipoPersona desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoPersona tipo : values()) {
            if (tipo.clave.equals(normalizado)) {
                return tipo;
            }
        }
        throw new ReglaNegocioException(
                "El tipo de persona debe ser 'fisica' o 'moral'.");
    }
}

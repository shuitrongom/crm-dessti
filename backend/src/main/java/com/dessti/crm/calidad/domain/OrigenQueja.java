package com.dessti.crm.calidad.domain;

import java.util.Locale;

/**
 * Origen de una {@link QuejaCliente} (Req 70.1, 70.8, clausula 10.2). Incluye el
 * canal de la Bandeja_Unificada del Req 64 ({@link #SOCIAL}). Etiquetas ASCII en
 * minusculas persistidas en {@code queja_cliente.origen} conforme al CHECK de V47.
 */
public enum OrigenQueja {

    /** La queja llega por el Portal del Cliente (Req 45). */
    PORTAL("portal"),

    /** La queja se origina en una Conversacion de la Bandeja_Unificada (Req 64). */
    SOCIAL("social"),

    /** La queja llega por correo electronico. */
    CORREO("correo"),

    /** La queja llega por telefono. */
    TELEFONO("telefono"),

    /** Otro origen no clasificado. */
    OTRO("otro");

    private final String valorBd;

    OrigenQueja(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code queja_cliente.origen}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del origen.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el origen a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'social'}).
     * @return el origen correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static OrigenQueja desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El origen de la Queja_Cliente no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (OrigenQueja origen : values()) {
            if (origen.valorBd.equals(normalizado)) {
                return origen;
            }
        }
        throw new IllegalArgumentException("Origen de Queja_Cliente desconocido: " + valor);
    }
}

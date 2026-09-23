package com.dessti.crm.social.domain;

import java.util.Locale;

/**
 * Tipo de un {@link MensajeSocial} (Req 64.7, 64.11): {@link #TEXTO} (texto libre,
 * solo dentro de la Ventana_Servicio), {@link #PLANTILLA} (Plantilla_Mensaje
 * aprobada, requerida fuera de la ventana) o {@link #INTERACTIVO} (botones/listas,
 * Req 64.11). Sigue el patron de enum con etiqueta de base de datos de
 * {@code EstadoActivoFijo}.
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'texto'}, {@code 'plantilla'} o
 * {@code 'interactivo'}, tal como exige el CHECK de la migracion V41.</p>
 */
public enum TipoMensaje {

    /** Texto libre; solo permitido dentro de la Ventana_Servicio (Req 64.6). */
    TEXTO("texto"),

    /** Plantilla_Mensaje aprobada; requerida fuera de la Ventana_Servicio (Req 64.7). */
    PLANTILLA("plantilla"),

    /** Mensaje interactivo con botones o listas (Req 64.11). */
    INTERACTIVO("interactivo");

    private final String valorBd;

    TipoMensaje(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code mensaje_social.tipo}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del tipo.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Indica si este tipo es texto libre, sujeto a la guarda de Ventana_Servicio
     * (Req 64.6, 64.7).
     *
     * @return {@code true} si es {@link #TEXTO}.
     */
    public boolean esTextoLibre() {
        return this == TEXTO;
    }

    /**
     * Reconstruye el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'texto'}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static TipoMensaje desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de Mensaje_Social no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoMensaje tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Mensaje_Social desconocido: " + valor);
    }
}

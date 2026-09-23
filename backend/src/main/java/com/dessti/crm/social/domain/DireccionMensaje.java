package com.dessti.crm.social.domain;

import java.util.Locale;

/**
 * Sentido de un {@link MensajeSocial} (Req 64.4): {@link #ENTRANTE} (del Cliente
 * hacia la Empresa) o {@link #SALIENTE} (de la Empresa hacia el Cliente). Sigue el
 * patron de enum con etiqueta de base de datos de {@code EstadoActivoFijo}.
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'entrante'} o {@code 'saliente'}, tal como exige el
 * CHECK de la migracion V41.</p>
 */
public enum DireccionMensaje {

    /** Mensaje recibido del Cliente/Contacto; actualiza la Ventana_Servicio (Req 64.6). */
    ENTRANTE("entrante"),

    /** Mensaje enviado por la Empresa (texto, plantilla o interactivo). */
    SALIENTE("saliente");

    private final String valorBd;

    DireccionMensaje(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code mensaje_social.direccion}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del sentido.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el sentido a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'entrante'}).
     * @return el sentido correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static DireccionMensaje desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("La direccion del Mensaje_Social no puede ser nula");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (DireccionMensaje direccion : values()) {
            if (direccion.valorBd.equals(normalizado)) {
                return direccion;
            }
        }
        throw new IllegalArgumentException("Direccion de Mensaje_Social desconocida: " + valor);
    }
}

package com.dessti.crm.social.domain;

import java.util.Locale;

/**
 * Estado de entrega de un {@link MensajeSocial} saliente (Req 64.11), mapeado
 * desde la respuesta del proveedor por el adaptador de {@code MensajeriaSocialPort}:
 * {@link #ENVIADO}, {@link #ENTREGADO}, {@link #LEIDO} o {@link #FALLIDO}. En los
 * mensajes entrantes es {@code null} (no aplica).
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'enviado'}, {@code 'entregado'}, {@code 'leido'} o
 * {@code 'fallido'}, tal como admite el CHECK de la migracion V41.</p>
 */
public enum EstadoEntrega {

    /** El proveedor acepto el mensaje para su envio. */
    ENVIADO("enviado"),

    /** El proveedor confirmo la entrega al dispositivo del destinatario. */
    ENTREGADO("entregado"),

    /** El destinatario leyo el mensaje. */
    LEIDO("leido"),

    /** El envio fallo (sujeto a la politica de reintentos, Req 64.13). */
    FALLIDO("fallido");

    private final String valorBd;

    EstadoEntrega(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code mensaje_social.estado_entrega}, en minusculas
     * ASCII.
     *
     * @return la etiqueta de base de datos del estado de entrega.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Indica si este estado representa un envio fallido (Req 64.13).
     *
     * @return {@code true} si es {@link #FALLIDO}.
     */
    public boolean esFallido() {
        return this == FALLIDO;
    }

    /**
     * Reconstruye el estado de entrega a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'enviado'}).
     * @return el estado de entrega correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoEntrega desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de entrega no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoEntrega estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de entrega desconocido: " + valor);
    }
}

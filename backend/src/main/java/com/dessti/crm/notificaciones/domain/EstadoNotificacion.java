package com.dessti.crm.notificaciones.domain;

import java.util.Locale;

/**
 * Estado del ciclo de vida de una {@link Notificacion} (Req 46.1, 46.3, 46.7).
 * Refleja el resultado del intento de entrega registrado por la
 * {@code ServicioNotificaciones}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #PENDIENTE} — estado inicial: la Notificacion se genero pero aun no
 *       se ha despachado a ningun canal (Req 46.1).</li>
 *   <li>{@link #ENVIADA} — el envio tuvo exito en alguno de los intentos (Req 46.3);
 *       se fija {@code enviada_en}.</li>
 *   <li>{@link #FALLIDA} — el envio fallo tras agotar la politica de reintentos
 *       configurable (Req 46.3); cada intento queda registrado.</li>
 *   <li>{@link #OMITIDA} — no se envio deliberadamente: el destinatario carece de
 *       Opt_In vigente para el Canal_Social y la Notificacion es de marketing
 *       (Req 46.7); se registra el {@code motivo_omision}.</li>
 * </ul>
 *
 * <h2>Valor persistido</h2>
 * <p>Se almacena la etiqueta ASCII en minusculas ({@link #valorBd()}):
 * {@code 'pendiente'}, {@code 'enviada'}, {@code 'fallida'}, {@code 'omitida'},
 * coherente con el CHECK de la columna {@code notificacion.estado} de la migracion
 * V42. Sigue el patron de etiqueta ASCII de {@code EstadoOrdenFabricacion}.</p>
 */
public enum EstadoNotificacion {

    /** Estado inicial: generada, aun sin despachar (Req 46.1). */
    PENDIENTE("pendiente"),

    /** El envio tuvo exito (Req 46.3). */
    ENVIADA("enviada"),

    /** El envio fallo tras agotar los reintentos (Req 46.3). */
    FALLIDA("fallida"),

    /** No se envio por falta de Opt_In vigente en Canal_Social de marketing (Req 46.7). */
    OMITIDA("omitida");

    private final String valorBd;

    EstadoNotificacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code notificacion.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V42.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'pendiente'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoNotificacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Notificacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoNotificacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Notificacion desconocido: " + valor);
    }
}

package com.dessti.crm.notificaciones.domain;

import java.util.Locale;

/**
 * Canal por el que se entrega una {@link Notificacion} (Req 46.1, 46.6). Determina
 * el puerto de salida que la {@code ServicioNotificaciones} utiliza para el envio.
 *
 * <h2>Canales</h2>
 * <ul>
 *   <li>{@link #CORREO} — correo electronico (Req 46.1, 46.2). No es un
 *       Canal_Social; no aplica la guarda de Opt_In de marketing.</li>
 *   <li>{@link #WHATSAPP} — WhatsApp. Es un Canal_Social (Req 46.6): respeta
 *       Ventana_Servicio, Plantilla_Mensaje y Opt_In (Req 46.6, 46.7).</li>
 *   <li>{@link #MESSENGER} — Messenger. Canal_Social (Req 46.6).</li>
 *   <li>{@link #INSTAGRAM} — Instagram. Canal_Social (Req 46.6).</li>
 * </ul>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'correo'}, {@code 'whatsapp'}, {@code 'messenger'},
 * {@code 'instagram'}, coherente con el CHECK de la columna {@code notificacion.canal}
 * de la migracion V42. El {@link CanalNotificacionConverter} traduce entre el enum y
 * esta etiqueta. Sigue el mismo patron de etiqueta ASCII de {@code EstadoOrdenFabricacion}.</p>
 */
public enum CanalNotificacion {

    /** Correo electronico (Req 46.1, 46.2). No es Canal_Social. */
    CORREO("correo", false),

    /** WhatsApp. Canal_Social sujeto a Ventana_Servicio/Plantilla/Opt_In (Req 46.6, 46.7). */
    WHATSAPP("whatsapp", true),

    /** Messenger. Canal_Social (Req 46.6). */
    MESSENGER("messenger", true),

    /** Instagram. Canal_Social (Req 46.6). */
    INSTAGRAM("instagram", true);

    private final String valorBd;
    private final boolean social;

    CanalNotificacion(String valorBd, boolean social) {
        this.valorBd = valorBd;
        this.social = social;
    }

    /**
     * Etiqueta persistida en la columna {@code notificacion.canal}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V42.
     *
     * @return la etiqueta de base de datos del canal.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Indica si este canal es un <strong>Canal_Social</strong> (WhatsApp, Messenger
     * o Instagram) y por tanto queda sujeto a la reutilizacion de la integracion
     * social respetando Ventana_Servicio, Plantilla_Mensaje y Opt_In (Req 46.6,
     * 46.7). El correo no es social.
     *
     * @return {@code true} si el canal es social.
     */
    public boolean esSocial() {
        return social;
    }

    /**
     * Reconstruye el canal a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'correo'}).
     * @return el canal correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun canal conocido.
     */
    public static CanalNotificacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El canal de la Notificacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (CanalNotificacion canal : values()) {
            if (canal.valorBd.equals(normalizado)) {
                return canal;
            }
        }
        throw new IllegalArgumentException("Canal de Notificacion desconocido: " + valor);
    }
}

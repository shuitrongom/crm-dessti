package com.dessti.crm.notificaciones.domain;

import java.util.Locale;

/**
 * Tipo de evento de negocio que da origen a una {@link Notificacion} (Req 46.1).
 * Identifica el disparador relevante que motiva notificar a un destinatario por
 * correo o por un Canal_Social.
 *
 * <h2>Eventos relevantes (Req 46.1)</h2>
 * <ul>
 *   <li>{@link #PRUEBA_DISENO_ENVIADA} — una Prueba_Diseno se envio para aprobacion.</li>
 *   <li>{@link #PERMISO_POR_VENCER} — un Permiso_Instalacion esta proximo a vencer.</li>
 *   <li>{@link #TICKET_SLA_POR_INCUMPLIR} — un Ticket_Servicio esta proximo a
 *       incumplir su SLA.</li>
 *   <li>{@link #FACTURA_TIMBRADA} — una Factura fue timbrada.</li>
 *   <li>{@link #NOMINA_TIMBRADA} — una Nomina fue timbrada.</li>
 * </ul>
 *
 * <p><strong>Extensibilidad:</strong> el catalogo puede crecer con nuevos eventos
 * relevantes; los modulos productores (facturacion, permiso, mantenimiento, etc.)
 * invocan {@code NotificacionPort.notificar(...)} indicando el evento de origen. La
 * integracion de las llamadas desde cada modulo productor es trabajo posterior; el
 * bloque 43 aporta el mecanismo y el catalogo.</p>
 *
 * <h2>Valor persistido</h2>
 * <p>Se almacena la etiqueta ASCII en minusculas ({@link #valorBd()}) en la columna
 * {@code notificacion.evento_origen} (VARCHAR(40)) de la migracion V42, sin acentos
 * por estabilidad de codificacion.</p>
 */
public enum TipoEventoNotificacion {

    /** Una Prueba_Diseno se envio para aprobacion (Req 46.1). */
    PRUEBA_DISENO_ENVIADA("prueba_diseno_enviada"),

    /** Un Permiso_Instalacion esta proximo a vencer (Req 46.1). */
    PERMISO_POR_VENCER("permiso_por_vencer"),

    /** Un Ticket_Servicio esta proximo a incumplir su SLA (Req 46.1). */
    TICKET_SLA_POR_INCUMPLIR("ticket_sla_por_incumplir"),

    /** Una Factura fue timbrada (Req 46.1). */
    FACTURA_TIMBRADA("factura_timbrada"),

    /** Una Nomina fue timbrada (Req 46.1). */
    NOMINA_TIMBRADA("nomina_timbrada");

    private final String valorBd;

    TipoEventoNotificacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code notificacion.evento_origen}, en
     * minusculas ASCII (sin acentos), coherente con la migracion V42.
     *
     * @return la etiqueta de base de datos del evento de origen.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el evento a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'factura_timbrada'}).
     * @return el evento correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun evento conocido.
     */
    public static TipoEventoNotificacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El evento de origen de la Notificacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoEventoNotificacion evento : values()) {
            if (evento.valorBd.equals(normalizado)) {
                return evento;
            }
        }
        throw new IllegalArgumentException("Evento de origen de Notificacion desconocido: " + valor);
    }
}

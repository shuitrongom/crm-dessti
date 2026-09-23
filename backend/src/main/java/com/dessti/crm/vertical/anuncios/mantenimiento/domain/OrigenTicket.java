package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import java.util.Locale;

/**
 * Origen de un {@link TicketServicio} (Req 20.2): identifica si el ticket se creo
 * manualmente o de forma automatica por mantenimiento preventivo programado.
 *
 * <h2>Valores</h2>
 * <ul>
 *   <li>{@link #MANUAL} — el ticket lo creo un operador de forma manual; puede no
 *       tener Contrato_Mantenimiento asociado.</li>
 *   <li>{@link #PREVENTIVO} — el ticket se genero automaticamente por el
 *       mantenimiento preventivo programado de un Contrato_Mantenimiento.</li>
 * </ul>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'manual'} o {@code 'preventivo'}, tal como exige el
 * CHECK de la migracion V27. El {@link OrigenTicketConverter} traduce entre el enum
 * y esta etiqueta.</p>
 */
public enum OrigenTicket {

    /** El ticket lo creo un operador de forma manual (Req 20.2). */
    MANUAL("manual"),

    /** El ticket se genero por mantenimiento preventivo programado (Req 20.2). */
    PREVENTIVO("preventivo");

    private final String valorBd;

    OrigenTicket(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code ticket_servicio.origen}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V27.
     *
     * @return la etiqueta de base de datos del origen.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el origen a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'manual'}).
     * @return el origen correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun origen conocido.
     */
    public static OrigenTicket desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El origen del Ticket_Servicio no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (OrigenTicket origen : values()) {
            if (origen.valorBd.equals(normalizado)) {
                return origen;
            }
        }
        throw new IllegalArgumentException("Origen de Ticket_Servicio desconocido: " + valor);
    }
}

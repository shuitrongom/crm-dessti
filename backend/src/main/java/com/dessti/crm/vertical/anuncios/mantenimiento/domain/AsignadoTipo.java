package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import java.util.Locale;

/**
 * Tipo de destinatario de la asignacion de un {@link TicketServicio} (Req 20.3):
 * un tecnico (usuario) o una Cuadrilla.
 *
 * <h2>Valores</h2>
 * <ul>
 *   <li>{@link #TECNICO} — el ticket se asigna a un tecnico individual (usuario).</li>
 *   <li>{@link #CUADRILLA} — el ticket se asigna a una Cuadrilla.</li>
 * </ul>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'tecnico'} o {@code 'cuadrilla'}, tal como exige el
 * CHECK de la migracion V27. El identificador del destinatario ({@code asignado_id})
 * es una referencia debil (UUID sin FK), pues el destino es polimorfico (V27,
 * DECISION 3). El {@link AsignadoTipoConverter} traduce entre el enum y esta
 * etiqueta.</p>
 */
public enum AsignadoTipo {

    /** El ticket se asigna a un tecnico individual (usuario) (Req 20.3). */
    TECNICO("tecnico"),

    /** El ticket se asigna a una Cuadrilla (Req 20.3). */
    CUADRILLA("cuadrilla");

    private final String valorBd;

    AsignadoTipo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code ticket_servicio.asignado_tipo}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V27.
     *
     * @return la etiqueta de base de datos del tipo de asignacion.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el tipo de asignacion a partir de su etiqueta de base de datos
     * (inversa de {@link #valorBd()}). La comparacion es insensible a mayusculas y
     * recorta espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'tecnico'}).
     * @return el tipo de asignacion correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun tipo conocido.
     */
    public static AsignadoTipo desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de asignacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (AsignadoTipo tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de asignacion desconocido: " + valor);
    }
}

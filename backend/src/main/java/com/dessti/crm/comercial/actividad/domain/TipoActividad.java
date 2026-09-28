package com.dessti.crm.comercial.actividad.domain;

import java.util.Locale;

/**
 * Tipo de interaccion o tarea de seguimiento comercial (V79). Determina la
 * naturaleza de la {@link Actividad} registrada en el timeline del Cliente y del
 * pipeline.
 *
 * <h2>Tipos</h2>
 * <ul>
 *   <li>{@link #LLAMADA} — contacto telefonico.</li>
 *   <li>{@link #CORREO} — comunicacion por correo electronico.</li>
 *   <li>{@link #REUNION} — junta o cita (etiqueta ASCII sin acento).</li>
 *   <li>{@link #TAREA} — pendiente accionable con posible vencimiento.</li>
 *   <li>{@link #NOTA} — anotacion o registro de algo ya ocurrido.</li>
 * </ul>
 *
 * <h2>Estado inicial segun el tipo (Req de negocio)</h2>
 * <p>Una {@link #NOTA} documenta un hecho consumado, por lo que nace en
 * {@link EstadoActividad#COMPLETADA}; los demas tipos representan un seguimiento
 * planificado y nacen en {@link EstadoActividad#PENDIENTE}. Esta regla la expone
 * {@link #estadoInicial()} y la aplica la fabrica de dominio {@code Actividad.crear}.</p>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'llamada'}, {@code 'correo'}, {@code 'reunion'},
 * {@code 'tarea'}, {@code 'nota'}, respetando el CHECK {@code ck_actividad_tipo}
 * de V79. {@link TipoActividadConverter} traduce entre el enum y esta etiqueta.</p>
 */
public enum TipoActividad {

    /** Contacto telefonico con el Cliente. */
    LLAMADA("llamada"),

    /** Comunicacion por correo electronico. */
    CORREO("correo"),

    /** Junta o cita (etiqueta ASCII sin acento por estabilidad de codificacion). */
    REUNION("reunion"),

    /** Pendiente accionable de seguimiento, con posible vencimiento. */
    TAREA("tarea"),

    /** Anotacion o registro de un hecho ya ocurrido. */
    NOTA("nota");

    private final String valorBd;

    TipoActividad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code actividad_comercial.tipo}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V79.
     *
     * @return la etiqueta de base de datos del tipo.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Estado en el que nace una actividad de este tipo. Una {@link #NOTA} nace
     * {@link EstadoActividad#COMPLETADA} (documenta un hecho consumado); los demas
     * tipos nacen {@link EstadoActividad#PENDIENTE} (seguimiento planificado).
     *
     * @return el estado inicial correspondiente al tipo.
     */
    public EstadoActividad estadoInicial() {
        return (this == NOTA) ? EstadoActividad.COMPLETADA : EstadoActividad.PENDIENTE;
    }

    /**
     * Reconstruye el tipo a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'llamada'}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static TipoActividad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de la Actividad no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoActividad tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Actividad desconocido: " + valor);
    }
}

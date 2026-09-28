package com.dessti.crm.comercial.actividad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados del ciclo de vida de una {@link Actividad} de seguimiento comercial y
 * su maquina de estados <strong>pura</strong> (V79).
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #PENDIENTE} — seguimiento planificado aun no realizado (estado
 *       inicial de llamada/correo/reunion/tarea).</li>
 *   <li>{@link #COMPLETADA} — la interaccion se realizo o la tarea se cumplio
 *       (estado <strong>final</strong>). Es el estado inicial de una nota.</li>
 *   <li>{@link #CANCELADA} — el seguimiento se descarto (estado
 *       <strong>final</strong>).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas</h2>
 * <pre>
 *   pendiente -&gt; completada | cancelada
 *   (completada, cancelada: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquiera que parta de un estado final—
 * es invalida.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'pendiente'}, {@code 'completada'},
 * {@code 'cancelada'}, respetando el CHECK {@code ck_actividad_estado} de V79.
 * {@link EstadoActividadConverter} traduce entre el enum y esta etiqueta.</p>
 */
public enum EstadoActividad {

    /** Seguimiento planificado aun no realizado (estado inicial habitual). */
    PENDIENTE("pendiente"),

    /** Estado final: la interaccion se realizo o la tarea se cumplio. */
    COMPLETADA("completada"),

    /** Estado final: el seguimiento se descarto. */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura del seguimiento. Se construye una sola vez y es
     * inmutable. Los estados finales {@link #COMPLETADA} y {@link #CANCELADA} no
     * declaran transiciones salientes, por lo que la maquina los trata como
     * finales automaticamente.
     */
    private static final MaquinaEstados<EstadoActividad> MAQUINA =
            MaquinaEstados.<EstadoActividad>builder(EstadoActividad.class)
                    .permitir(PENDIENTE, COMPLETADA, CANCELADA)
                    .construir();

    private final String valorBd;

    EstadoActividad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code actividad_comercial.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V79.
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
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoActividad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Actividad no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoActividad estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Actividad desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (completada o cancelada) y
     * por tanto no admite ninguna transicion posterior.
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas. Toda transicion que
     * parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoActividad destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

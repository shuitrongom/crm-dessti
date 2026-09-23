package com.dessti.crm.vertical.anuncios.instalacion.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link OrdenTrabajoInstalacion} y su maquina de estados
 * <strong>pura</strong> (Req 19.5, 19.6; design.md, seccion <em>State Machines</em>).
 * Sigue el mismo patron que {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #PROGRAMADA} — estado inicial de toda Orden_Trabajo_Instalacion
 *       recien creada a partir de una Orden_Fabricacion terminada (Req 19.1).</li>
 *   <li>{@link #EN_CURSO} — la instalacion esta en ejecucion en sitio.</li>
 *   <li>{@link #COMPLETADA} — estado <strong>final</strong>: instalacion concluida
 *       (Req 19.5). Su transicion exige que no queden pendientes por resolver
 *       (Req 19.6), guarda que aplica la capa de aplicacion.</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: la instalacion se
 *       cancelo (Req 19.5).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 19.5)</h2>
 * <pre>
 *   programada  -&gt; en_curso | cancelada
 *   en_curso    -&gt; completada | cancelada
 *   (completada, cancelada: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquiera que parta de un estado final—
 * es invalida (Req 19.5) y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual sin modificarlo.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'programada'}, {@code 'en_curso'},
 * {@code 'completada'}, {@code 'cancelada'}, tal como exige el CHECK de la
 * migracion V24. El {@link EstadoOrdenTrabajoInstalacionConverter} traduce entre el
 * enum y esta etiqueta.</p>
 */
public enum EstadoOrdenTrabajoInstalacion {

    /** Estado inicial de una Orden_Trabajo_Instalacion recien creada (Req 19.1). */
    PROGRAMADA("programada"),

    /** La instalacion esta en ejecucion en sitio (Req 19.5). */
    EN_CURSO("en_curso"),

    /** Estado final: instalacion concluida (Req 19.5, 19.6). */
    COMPLETADA("completada"),

    /** Estado final: instalacion cancelada (Req 19.5). */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Orden_Trabajo_Instalacion (Req 19.5). Se
     * construye una sola vez y es inmutable. Los estados finales
     * {@link #COMPLETADA} y {@link #CANCELADA} no declaran transiciones salientes,
     * por lo que la maquina los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoOrdenTrabajoInstalacion> MAQUINA =
            MaquinaEstados.<EstadoOrdenTrabajoInstalacion>builder(EstadoOrdenTrabajoInstalacion.class)
                    .permitir(PROGRAMADA, EN_CURSO, CANCELADA)
                    .permitir(EN_CURSO, COMPLETADA, CANCELADA)
                    .construir();

    private final String valorBd;

    EstadoOrdenTrabajoInstalacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code orden_trabajo_instalacion.estado},
     * en minusculas ASCII, tal como la exige el CHECK de la migracion V24.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'programada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoOrdenTrabajoInstalacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    "El estado de la Orden_Trabajo_Instalacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoOrdenTrabajoInstalacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException(
                "Estado de Orden_Trabajo_Instalacion desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (completada o cancelada) y
     * por tanto no admite ninguna transicion posterior (Req 19.5).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 19.5. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoOrdenTrabajoInstalacion destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

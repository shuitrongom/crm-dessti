package com.dessti.crm.operacion.produccion.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link OrdenFabricacion} y su maquina de estados
 * <strong>pura</strong> (Req 7.5, 7.6; design.md, seccion <em>State Machines</em>).
 * Sigue el mismo patron que {@code EstadoCotizacion} y {@code EstadoPruebaDiseno}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #PENDIENTE} — estado inicial de toda Orden_Fabricacion recien
 *       generada (Req 7.4).</li>
 *   <li>{@link #EN_PRODUCCION} — la Orden_Fabricacion esta en manufactura.</li>
 *   <li>{@link #TERMINADA} — estado <strong>final</strong>: manufactura concluida
 *       (Req 7.5, 7.6). Es la precondicion de la Orden_Trabajo_Instalacion
 *       (bloque 22).</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: la Orden_Fabricacion
 *       se cancelo (Req 7.5, 7.6).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 7.5)</h2>
 * <pre>
 *   pendiente      -&gt; en_produccion | cancelada
 *   en_produccion  -&gt; terminada | cancelada
 *   (terminada, cancelada: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquiera que parta de un estado final—
 * es invalida (Req 7.6) y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual sin modificarlo.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'pendiente'}, {@code 'en_produccion'},
 * {@code 'terminada'}, {@code 'cancelada'}, tal como exige el CHECK de la
 * migracion V17. Notese que {@code 'en_produccion'} se persiste
 * <strong>sin acento</strong> por estabilidad de codificacion (la prosa del
 * requisito usa "en_producción"); la capa de presentacion aplica la etiqueta
 * visible acentuada. El {@link EstadoOrdenFabricacionConverter} traduce entre el
 * enum y esta etiqueta.</p>
 */
public enum EstadoOrdenFabricacion {

    /** Estado inicial de una Orden_Fabricacion recien generada (Req 7.4). */
    PENDIENTE("pendiente"),

    /**
     * La Orden_Fabricacion esta en manufactura. Etiqueta ASCII {@code en_produccion}
     * (sin acento) por estabilidad de codificacion, coherente con V17.
     */
    EN_PRODUCCION("en_produccion"),

    /** Estado final: manufactura concluida (Req 7.5, 7.6). */
    TERMINADA("terminada"),

    /** Estado final: Orden_Fabricacion cancelada (Req 7.5, 7.6). */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Orden_Fabricacion (Req 7.5, 7.6). Se construye
     * una sola vez y es inmutable. Los estados finales {@link #TERMINADA} y
     * {@link #CANCELADA} no declaran transiciones salientes, por lo que la maquina
     * los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoOrdenFabricacion> MAQUINA =
            MaquinaEstados.<EstadoOrdenFabricacion>builder(EstadoOrdenFabricacion.class)
                    .permitir(PENDIENTE, EN_PRODUCCION, CANCELADA)
                    .permitir(EN_PRODUCCION, TERMINADA, CANCELADA)
                    .construir();

    private final String valorBd;

    EstadoOrdenFabricacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code orden_fabricacion.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V17.
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
    public static EstadoOrdenFabricacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Orden_Fabricacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoOrdenFabricacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Orden_Fabricacion desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (terminada o cancelada) y
     * por tanto no admite ninguna transicion posterior (Req 7.5, 7.6).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 7.5. Toda
     * transicion que parta de un estado final devuelve {@code false} (Req 7.6).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoOrdenFabricacion destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

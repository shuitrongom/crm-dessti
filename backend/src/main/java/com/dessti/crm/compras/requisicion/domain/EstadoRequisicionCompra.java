package com.dessti.crm.compras.requisicion.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link RequisicionCompra} y su maquina de estados
 * <strong>pura</strong> (Req 30.2, 30.3). Sigue el mismo patron que
 * {@code EstadoCotizacion} y {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #BORRADOR} — estado inicial de toda Requisicion_Compra (Req 30.1).</li>
 *   <li>{@link #ENVIADA} — la Requisicion se envio para su resolucion.</li>
 *   <li>{@link #APROBADA} — estado <strong>final</strong>: requisicion aprobada,
 *       habilita generar una Orden_Compra (Req 30.3, 30.4).</li>
 *   <li>{@link #RECHAZADA} — estado <strong>final</strong>: requisicion rechazada
 *       (Req 30.2).</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: requisicion cancelada
 *       (Req 30.2).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 30.2)</h2>
 * <pre>
 *   borrador -&gt; enviada
 *   enviada  -&gt; aprobada | rechazada
 *   (aprobada, rechazada, cancelada: finales, sin salida)
 * </pre>
 * <p><strong>Decision (bloque 26):</strong> el Req 30.2 declara {@code cancelada}
 * como estado <em>final</em> pero <strong>no</strong> especifica ninguna
 * transicion de entrada hacia el. Se modela literalmente: {@code CANCELADA} se
 * declara como estado final (sin transiciones salientes) y sin transiciones
 * entrantes desde la maquina, de modo que solo se permiten las tres transiciones
 * listadas. Cualquier otra transicion —incluida cualquiera hacia {@code cancelada}
 * o que parta de un estado final— es invalida y se rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual (Req 30.2).</p>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'borrador'}, {@code 'enviada'}, {@code 'aprobada'},
 * {@code 'rechazada'}, {@code 'cancelada'}, tal como exige el CHECK de la migracion
 * V28. El {@link EstadoRequisicionCompraConverter} traduce entre el enum y esta
 * etiqueta.</p>
 */
public enum EstadoRequisicionCompra {

    /** Estado inicial de una Requisicion_Compra recien creada (Req 30.1). */
    BORRADOR("borrador"),

    /** La Requisicion se envio y esta pendiente de resolucion. */
    ENVIADA("enviada"),

    /** Estado final: requisicion aprobada (Req 30.2, 30.3). */
    APROBADA("aprobada"),

    /** Estado final: requisicion rechazada (Req 30.2). */
    RECHAZADA("rechazada"),

    /** Estado final: requisicion cancelada (Req 30.2). */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Requisicion_Compra (Req 30.2). Se construye una
     * sola vez y es inmutable. Los estados finales {@link #APROBADA},
     * {@link #RECHAZADA} y {@link #CANCELADA} no declaran transiciones salientes,
     * por lo que la maquina los trata como finales automaticamente. Solo se
     * declaran las tres transiciones del Req 30.2.
     */
    private static final MaquinaEstados<EstadoRequisicionCompra> MAQUINA =
            MaquinaEstados.<EstadoRequisicionCompra>builder(EstadoRequisicionCompra.class)
                    .permitir(BORRADOR, ENVIADA)
                    .permitir(ENVIADA, APROBADA, RECHAZADA)
                    .construir();

    private final String valorBd;

    EstadoRequisicionCompra(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code requisicion_compra.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V28.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'borrador'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoRequisicionCompra desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Requisicion_Compra no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoRequisicionCompra estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Requisicion_Compra desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (aprobada, rechazada o
     * cancelada) y por tanto no admite ninguna transicion posterior (Req 30.2).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 30.2. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoRequisicionCompra destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

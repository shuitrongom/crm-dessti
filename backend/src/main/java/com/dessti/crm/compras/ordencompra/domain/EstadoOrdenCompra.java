package com.dessti.crm.compras.ordencompra.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link OrdenCompra} y su maquina de estados <strong>pura</strong>
 * (Req 31.5, 31.6). Sigue el mismo patron que {@code EstadoCotizacion} y
 * {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #ABIERTA} — estado inicial de toda Orden_Compra recien creada
 *       (Req 31.4).</li>
 *   <li>{@link #RECIBIDA_PARCIAL} — se recibio parte de la mercancia.</li>
 *   <li>{@link #RECIBIDA_TOTAL} — se recibio la totalidad de la mercancia.</li>
 *   <li>{@link #CERRADA} — estado <strong>final</strong>: Orden_Compra cerrada
 *       (Req 31.6).</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: Orden_Compra cancelada
 *       (Req 31.6).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 31.6)</h2>
 * <pre>
 *   abierta          -&gt; recibida_parcial | cancelada
 *   recibida_parcial -&gt; recibida_total | cancelada
 *   recibida_total   -&gt; cerrada
 *   (cerrada, cancelada: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquiera que parta de un estado final—
 * es invalida (Req 31.6) y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual sin modificarlo.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'abierta'}, {@code 'recibida_parcial'},
 * {@code 'recibida_total'}, {@code 'cerrada'}, {@code 'cancelada'}, tal como exige
 * el CHECK de la migracion V28. El {@link EstadoOrdenCompraConverter} traduce entre
 * el enum y esta etiqueta.</p>
 */
public enum EstadoOrdenCompra {

    /** Estado inicial de una Orden_Compra recien creada (Req 31.4). */
    ABIERTA("abierta"),

    /** Se recibio parte de la mercancia de la Orden_Compra. */
    RECIBIDA_PARCIAL("recibida_parcial"),

    /** Se recibio la totalidad de la mercancia de la Orden_Compra. */
    RECIBIDA_TOTAL("recibida_total"),

    /** Estado final: Orden_Compra cerrada (Req 31.6). */
    CERRADA("cerrada"),

    /** Estado final: Orden_Compra cancelada (Req 31.6). */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Orden_Compra (Req 31.5, 31.6). Se construye una
     * sola vez y es inmutable. Los estados finales {@link #CERRADA} y
     * {@link #CANCELADA} no declaran transiciones salientes, por lo que la maquina
     * los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoOrdenCompra> MAQUINA =
            MaquinaEstados.<EstadoOrdenCompra>builder(EstadoOrdenCompra.class)
                    .permitir(ABIERTA, RECIBIDA_PARCIAL, CANCELADA)
                    .permitir(RECIBIDA_PARCIAL, RECIBIDA_TOTAL, CANCELADA)
                    .permitir(RECIBIDA_TOTAL, CERRADA)
                    .construir();

    private final String valorBd;

    EstadoOrdenCompra(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code orden_compra.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V28.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'abierta'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoOrdenCompra desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Orden_Compra no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoOrdenCompra estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Orden_Compra desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (cerrada o cancelada) y por
     * tanto no admite ninguna transicion posterior (Req 31.6).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 31.6. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoOrdenCompra destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

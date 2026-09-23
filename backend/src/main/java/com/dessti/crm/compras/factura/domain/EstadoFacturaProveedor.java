package com.dessti.crm.compras.factura.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link FacturaProveedor} y su maquina de estados
 * <strong>pura</strong> (Req 33.6). Sigue el mismo patron que
 * {@code EstadoOrdenCompra} y {@code EstadoCotizacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #REGISTRADA} — estado inicial de toda Factura_Proveedor recien
 *       registrada (Req 33.1).</li>
 *   <li>{@link #CONCILIADA} — la Conciliacion_Tres_Vias resulto satisfactoria; la
 *       factura queda habilitada para pago (Req 33.5).</li>
 *   <li>{@link #DISCREPANCIA} — estado <strong>final</strong>: la conciliacion
 *       detecto una discrepancia de cantidad o de precio fuera de tolerancia; no se
 *       autoriza el pago (Req 33.4).</li>
 *   <li>{@link #PAGADA} — estado <strong>final</strong>: pago autorizado desde una
 *       factura conciliada (Req 33.7).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 33.6)</h2>
 * <pre>
 *   registrada -&gt; conciliada | discrepancia
 *   conciliada -&gt; pagada
 *   (pagada, discrepancia: finales, sin salida)
 * </pre>
 *
 * <p>El Req 33.6 lista <em>unicamente</em> estas tres transiciones; por tanto
 * {@link #DISCREPANCIA} es <strong>terminal</strong> (no vuelve a
 * {@code conciliada}). Una re-conciliacion tras corregir la discrepancia
 * requeriria RE-REGISTRAR la factura (nuevo registro en estado
 * {@code registrada}); se documenta como trabajo futuro y no se habilita aqui para
 * no introducir transiciones fuera del Req 33.6. Cualquier otra transicion —incluida
 * cualquiera que parta de un estado final— es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual sin modificarlo (Req 33.7).</p>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'registrada'}, {@code 'conciliada'},
 * {@code 'discrepancia'}, {@code 'pagada'}, tal como exige el CHECK de la migracion
 * V29. El {@link EstadoFacturaProveedorConverter} traduce entre el enum y esta
 * etiqueta.</p>
 */
public enum EstadoFacturaProveedor {

    /** Estado inicial de una Factura_Proveedor recien registrada (Req 33.1). */
    REGISTRADA("registrada"),

    /** La conciliacion resulto satisfactoria; habilitada para pago (Req 33.5). */
    CONCILIADA("conciliada"),

    /** Estado final: discrepancia detectada; no se autoriza el pago (Req 33.4). */
    DISCREPANCIA("discrepancia"),

    /** Estado final: pago autorizado desde una factura conciliada (Req 33.7). */
    PAGADA("pagada");

    /**
     * Maquina de estados pura de la Factura_Proveedor (Req 33.6). Se construye una
     * sola vez y es inmutable. Los estados finales {@link #PAGADA} y
     * {@link #DISCREPANCIA} no declaran transiciones salientes, por lo que la
     * maquina los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoFacturaProveedor> MAQUINA =
            MaquinaEstados.<EstadoFacturaProveedor>builder(EstadoFacturaProveedor.class)
                    .permitir(REGISTRADA, CONCILIADA, DISCREPANCIA)
                    .permitir(CONCILIADA, PAGADA)
                    .construir();

    private final String valorBd;

    EstadoFacturaProveedor(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code factura_proveedor.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V29.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'registrada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoFacturaProveedor desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Factura_Proveedor no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoFacturaProveedor estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Factura_Proveedor desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (pagada o discrepancia) y por
     * tanto no admite ninguna transicion posterior (Req 33.6).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 33.6. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoFacturaProveedor destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

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
 *   <li>{@link #DISCREPANCIA} — la conciliacion detecto una discrepancia de
 *       cantidad o de precio fuera de tolerancia; no se autoriza el pago
 *       (Req 33.4). Es <strong>recuperable</strong>: puede REABRIRSE a
 *       {@code registrada} para reintentar la conciliacion tras corregir.</li>
 *   <li>{@link #PAGADA} — estado <strong>final</strong>: pago autorizado desde una
 *       factura conciliada (Req 33.7).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 33.6 + mejora enterprise de reapertura)</h2>
 * <pre>
 *   registrada   -&gt; conciliada | discrepancia
 *   conciliada   -&gt; pagada
 *   discrepancia -&gt; registrada        (reapertura para re-conciliar)
 *   (pagada: final, sin salida)
 * </pre>
 *
 * <p>A las tres transiciones del Req 33.6 se anade {@code discrepancia -> registrada}
 * (mejora enterprise): una factura marcada en discrepancia se puede REABRIR a
 * {@code registrada} para reintentar la Conciliacion_Tres_Vias tras corregir la
 * recepcion, la orden o el folio, sin re-registrarla desde cero. El unico estado
 * <strong>terminal</strong> es {@link #PAGADA}. Cualquier otra transicion —incluida
 * cualquiera que parta de {@code pagada}— es invalida y el dominio la rechaza con
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
     * Maquina de estados pura de la Factura_Proveedor (Req 33.6 + reapertura de
     * discrepancia). Se construye una sola vez y es inmutable. El unico estado
     * final es {@link #PAGADA} (sin transiciones salientes); {@link #DISCREPANCIA}
     * es recuperable via {@code discrepancia -> registrada}.
     */
    private static final MaquinaEstados<EstadoFacturaProveedor> MAQUINA =
            MaquinaEstados.<EstadoFacturaProveedor>builder(EstadoFacturaProveedor.class)
                    .permitir(REGISTRADA, CONCILIADA, DISCREPANCIA)
                    .permitir(CONCILIADA, PAGADA)
                    // Discrepancia RECUPERABLE (mejora enterprise): tras corregir la
                    // recepcion/orden o el folio, la factura puede REABRIRSE a
                    // 'registrada' para volver a intentar la Conciliacion_Tres_Vias,
                    // en lugar de exigir re-registrarla desde cero.
                    .permitir(DISCREPANCIA, REGISTRADA)
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
     * Indica si este estado es <strong>final</strong> (solo {@link #PAGADA}) y por
     * tanto no admite ninguna transicion posterior. {@link #DISCREPANCIA} NO es
     * final: puede reabrirse a {@code registrada}.
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

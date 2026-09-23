package com.dessti.crm.facturacion.factura.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link Factura} (CFDI) y su maquina de estados <strong>pura</strong>
 * (Req 35.7). Sigue el mismo patron que {@code EstadoCotizacion}/
 * {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #BORRADOR} — estado inicial de toda Factura recien emitida (Req 34.1).</li>
 *   <li>{@link #TIMBRADA} — la Factura fue timbrada por el PAC y tiene Folio_Fiscal
 *       (Req 35.1). Sus datos fiscales son inmutables a partir de aqui (Req 35.3).</li>
 *   <li>{@link #CANCELACION_EN_PROCESO} — se solicito la cancelacion al PAC y se
 *       espera la aceptacion del receptor o el vencimiento del plazo (Req 35.5).</li>
 *   <li>{@link #CANCELADA} — estado <strong>final</strong>: la cancelacion se
 *       confirmo (Req 35.5). El CFDI timbrado y su Folio_Fiscal se conservan como
 *       historico inmutable (Req 35.6).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 35.7)</h2>
 * <pre>
 *   borrador                -&gt; timbrada
 *   timbrada                -&gt; cancelacion_en_proceso
 *   cancelacion_en_proceso  -&gt; cancelada
 *   (cancelada: final, sin salida)
 * </pre>
 * Cualquier otra transicion es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'borrador'}, {@code 'timbrada'},
 * {@code 'cancelacion_en_proceso'}, {@code 'cancelada'}, tal como exige el CHECK
 * de la migracion V30. El {@link EstadoFacturaConverter} traduce entre el enum y
 * esta etiqueta.</p>
 */
public enum EstadoFactura {

    /** Estado inicial de una Factura recien emitida (Req 34.1). */
    BORRADOR("borrador"),

    /** La Factura fue timbrada por el PAC y tiene Folio_Fiscal (Req 35.1). */
    TIMBRADA("timbrada"),

    /** Cancelacion solicitada al PAC, en espera de aceptacion/plazo (Req 35.5). */
    CANCELACION_EN_PROCESO("cancelacion_en_proceso"),

    /** Estado final: cancelacion confirmada (Req 35.5). */
    CANCELADA("cancelada");

    /**
     * Maquina de estados pura de la Factura (Req 35.7). Se construye una sola vez y
     * es inmutable. El estado final {@link #CANCELADA} no declara transiciones
     * salientes, por lo que la maquina lo trata como final.
     */
    private static final MaquinaEstados<EstadoFactura> MAQUINA =
            MaquinaEstados.<EstadoFactura>builder(EstadoFactura.class)
                    .permitir(BORRADOR, TIMBRADA)
                    .permitir(TIMBRADA, CANCELACION_EN_PROCESO)
                    .permitir(CANCELACION_EN_PROCESO, CANCELADA)
                    .construir();

    private final String valorBd;

    EstadoFactura(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code factura.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V30.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'timbrada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoFactura desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Factura no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoFactura estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Factura desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #CANCELADA}) y por
     * tanto no admite ninguna transicion posterior (Req 35.7).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 35.7.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoFactura destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

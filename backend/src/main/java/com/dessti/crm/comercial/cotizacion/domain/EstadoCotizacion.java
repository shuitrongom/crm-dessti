package com.dessti.crm.comercial.cotizacion.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link Cotizacion} y su maquina de estados <strong>pura</strong>
 * (Req 6.6, 6.7; design.md, seccion <em>State Machines &rarr; Cotizacion</em>).
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #BORRADOR} — estado inicial de toda Cotizacion (Req 6.1).</li>
 *   <li>{@link #ENVIADA} — la Cotizacion se envio al Cliente.</li>
 *   <li>{@link #APROBADA}, {@link #RECHAZADA} — estados <strong>finales</strong>
 *       que no admiten ninguna transicion posterior (Req 6.6, 6.7).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 6.6)</h2>
 * <pre>
 *   borrador -&gt; enviada
 *   enviada  -&gt; aprobada | rechazada
 *   (aprobada, rechazada: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquiera que parta de un estado final—
 * es invalida (Req 6.7) y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'borrador'}, {@code 'enviada'}, {@code 'aprobada'},
 * {@code 'rechazada'}, tal como exige el CHECK de la migracion V14. El
 * {@link EstadoCotizacionConverter} traduce entre el enum y esta etiqueta.</p>
 *
 * <h2>Funcion pura y reutilizacion</h2>
 * <p>Las transiciones se declaran una sola vez en {@link #MAQUINA} usando el
 * helper generico {@link MaquinaEstados}, del mismo modo que
 * {@code EtapaOportunidad}. {@link #puedeTransicionarA(EstadoCotizacion)} es una
 * funcion pura {@code (actual, destino) -> boolean} sin dependencias de
 * framework, trivialmente verificable por pruebas unitarias y de propiedad.</p>
 */
public enum EstadoCotizacion {

    /** Estado inicial de una Cotizacion recien creada (Req 6.1). */
    BORRADOR("borrador"),

    /** La Cotizacion se envio al Cliente y esta pendiente de resolucion. */
    ENVIADA("enviada"),

    /** Estado final: Cotizacion aprobada por el Cliente (Req 6.6). */
    APROBADA("aprobada"),

    /** Estado final: Cotizacion rechazada por el Cliente (Req 6.6). */
    RECHAZADA("rechazada");

    /**
     * Maquina de estados pura de la Cotizacion (Req 6.6, 6.7). Se construye una
     * sola vez y es inmutable. Los estados finales {@link #APROBADA} y
     * {@link #RECHAZADA} no declaran transiciones salientes, por lo que la maquina
     * los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoCotizacion> MAQUINA =
            MaquinaEstados.<EstadoCotizacion>builder(EstadoCotizacion.class)
                    .permitir(BORRADOR, ENVIADA)
                    .permitir(ENVIADA, APROBADA, RECHAZADA)
                    .construir();

    private final String valorBd;

    EstadoCotizacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code cotizacion.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V14.
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
    public static EstadoCotizacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Cotizacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoCotizacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Cotizacion desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (aprobada o rechazada) y
     * por tanto no admite ninguna transicion posterior (Req 6.6, 6.7).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 6.6. Toda
     * transicion que parta de un estado final devuelve {@code false} (Req 6.7).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoCotizacion destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

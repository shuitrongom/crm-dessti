package com.dessti.crm.rhnomina.nomina.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link Nomina} y su maquina de estados <strong>pura</strong>
 * (Req 41.5, 41.6). Sigue el mismo patron que {@code EstadoFactura}/
 * {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #BORRADOR} — estado inicial de una Nomina recien creada (Req 41.5).</li>
 *   <li>{@link #CALCULADA} — se calcularon percepciones/deducciones/subsidio y neto
 *       por Empleado, generando los Recibo_Nomina (Req 41.1).</li>
 *   <li>{@link #AUTORIZADA} — la Nomina fue autorizada para su Timbrado (Req 41.4).</li>
 *   <li>{@link #TIMBRADA} — se timbraron los Recibo_Nomina como CFDI de nomina
 *       (Req 41.4).</li>
 *   <li>{@link #PAGADA} — estado <strong>final</strong>: la Nomina fue pagada
 *       (Req 41.5).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 41.5)</h2>
 * <pre>
 *   borrador   -&gt; calculada
 *   calculada  -&gt; autorizada
 *   autorizada -&gt; timbrada
 *   timbrada   -&gt; pagada
 *   (pagada: final, sin salida)
 * </pre>
 * Cualquier otra transicion es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409, Req 41.6),
 * conservando el estado actual.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'borrador'}, {@code 'calculada'},
 * {@code 'autorizada'}, {@code 'timbrada'}, {@code 'pagada'}, tal como exige el
 * CHECK de la migracion V34. El {@link EstadoNominaConverter} traduce entre el enum
 * y esta etiqueta.</p>
 */
public enum EstadoNomina {

    /** Estado inicial de una Nomina recien creada (Req 41.5). */
    BORRADOR("borrador"),

    /** La Nomina fue calculada por Empleado, generando los Recibo_Nomina (Req 41.1). */
    CALCULADA("calculada"),

    /** La Nomina fue autorizada para su Timbrado (Req 41.4). */
    AUTORIZADA("autorizada"),

    /** Los Recibo_Nomina fueron timbrados como CFDI de nomina (Req 41.4). */
    TIMBRADA("timbrada"),

    /** Estado final: la Nomina fue pagada (Req 41.5). */
    PAGADA("pagada");

    /**
     * Maquina de estados pura de la Nomina (Req 41.5). Se construye una sola vez y es
     * inmutable. El estado final {@link #PAGADA} no declara transiciones salientes.
     */
    private static final MaquinaEstados<EstadoNomina> MAQUINA =
            MaquinaEstados.<EstadoNomina>builder(EstadoNomina.class)
                    .permitir(BORRADOR, CALCULADA)
                    .permitir(CALCULADA, AUTORIZADA)
                    .permitir(AUTORIZADA, TIMBRADA)
                    .permitir(TIMBRADA, PAGADA)
                    .construir();

    private final String valorBd;

    EstadoNomina(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code nomina.estado}, en minusculas ASCII,
     * tal como la exige el CHECK de la migracion V34.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'calculada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoNomina desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Nomina no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoNomina estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Nomina desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #PAGADA}) y por tanto
     * no admite ninguna transicion posterior (Req 41.5).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}
     * segun las transiciones permitidas del Req 41.5.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoNomina destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

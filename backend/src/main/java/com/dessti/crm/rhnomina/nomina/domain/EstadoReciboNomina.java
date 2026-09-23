package com.dessti.crm.rhnomina.nomina.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link ReciboNomina} y su maquina de estados <strong>pura</strong>
 * (Req 41.5, 41.6, 41.7). Sigue el mismo patron que {@code EstadoFactura}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #CALCULADO} — estado inicial: el Recibo_Nomina se genero con sus
 *       importes calculados por Empleado (Req 41.1). Sus datos financieros son
 *       modificables solo en este estado.</li>
 *   <li>{@link #TIMBRADO} — el Recibo_Nomina fue timbrado como CFDI de nomina por el
 *       PAC y tiene Folio_Fiscal (Req 41.4). A partir de aqui sus datos financieros y
 *       el Folio_Fiscal son <strong>inmutables</strong> (historico, Req 41.7).</li>
 *   <li>{@link #CANCELADO} — estado <strong>final</strong>: el CFDI de nomina fue
 *       cancelado. El Folio_Fiscal timbrado se conserva como historico (Req 41.7).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 41.5)</h2>
 * <pre>
 *   calculado -&gt; timbrado
 *   timbrado  -&gt; cancelado
 *   (cancelado: final, sin salida)
 * </pre>
 * Cualquier otra transicion es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409, Req 41.6).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'calculado'}, {@code 'timbrado'}, {@code 'cancelado'},
 * tal como exige el CHECK de la migracion V34. El {@link EstadoReciboNominaConverter}
 * traduce entre el enum y esta etiqueta.</p>
 */
public enum EstadoReciboNomina {

    /** Estado inicial: Recibo_Nomina generado con sus importes calculados (Req 41.1). */
    CALCULADO("calculado"),

    /** El Recibo_Nomina fue timbrado como CFDI de nomina y tiene Folio_Fiscal (Req 41.4). */
    TIMBRADO("timbrado"),

    /** Estado final: el CFDI de nomina fue cancelado (Req 41.7 conserva el historico). */
    CANCELADO("cancelado");

    /**
     * Maquina de estados pura del Recibo_Nomina (Req 41.5). Se construye una sola vez
     * y es inmutable. El estado final {@link #CANCELADO} no declara salidas.
     */
    private static final MaquinaEstados<EstadoReciboNomina> MAQUINA =
            MaquinaEstados.<EstadoReciboNomina>builder(EstadoReciboNomina.class)
                    .permitir(CALCULADO, TIMBRADO)
                    .permitir(TIMBRADO, CANCELADO)
                    .construir();

    private final String valorBd;

    EstadoReciboNomina(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code recibo_nomina.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V34.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'timbrado'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoReciboNomina desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Recibo_Nomina no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoReciboNomina estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Recibo_Nomina desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #CANCELADO}) y por
     * tanto no admite ninguna transicion posterior (Req 41.5).
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
    public boolean puedeTransicionarA(EstadoReciboNomina destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

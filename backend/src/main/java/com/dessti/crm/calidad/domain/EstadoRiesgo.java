package com.dessti.crm.calidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link Riesgo} y su maquina de estados <strong>pura</strong>
 * (Req 70.3, clausula 6.1.2).
 *
 * <h2>Transiciones permitidas (Req 70.3)</h2>
 * <pre>
 *   identificado    -&gt; en_tratamiento, aceptado
 *   en_tratamiento  -&gt; mitigado, aceptado
 *   (mitigado, aceptado: finales, sin salida)
 * </pre>
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}) conforme al CHECK de V47.</p>
 */
public enum EstadoRiesgo {

    /** Estado inicial: Riesgo identificado (Req 70.3). */
    IDENTIFICADO("identificado"),

    /** Riesgo en tratamiento (Req 70.3). */
    EN_TRATAMIENTO("en_tratamiento"),

    /** Estado final: Riesgo mitigado (Req 70.3). */
    MITIGADO("mitigado"),

    /** Estado final: Riesgo aceptado (Req 70.3). */
    ACEPTADO("aceptado");

    /**
     * Maquina de estados pura del Riesgo (Req 70.3). Se construye una sola vez y es
     * inmutable. Los estados {@link #MITIGADO} y {@link #ACEPTADO} son finales.
     */
    private static final MaquinaEstados<EstadoRiesgo> MAQUINA =
            MaquinaEstados.<EstadoRiesgo>builder(EstadoRiesgo.class)
                    .permitir(IDENTIFICADO, EN_TRATAMIENTO, ACEPTADO)
                    .permitir(EN_TRATAMIENTO, MITIGADO, ACEPTADO)
                    .construir();

    private final String valorBd;

    EstadoRiesgo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code riesgo.estado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'mitigado'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoRiesgo desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Riesgo no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoRiesgo estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Riesgo desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong>.
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}
     * segun las transiciones permitidas del Req 70.3.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoRiesgo destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

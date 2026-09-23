package com.dessti.crm.calidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link OportunidadCalidad} y su maquina de estados
 * <strong>pura</strong> (Req 70.3, clausula 6.1.3).
 *
 * <h2>Transiciones permitidas (Req 70.3)</h2>
 * <pre>
 *   identificada  -&gt; en_evaluacion, descartada
 *   en_evaluacion -&gt; en_ejecucion, descartada
 *   en_ejecucion  -&gt; realizada, descartada
 *   (realizada, descartada: finales, sin salida)
 * </pre>
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}) conforme al CHECK de V47.</p>
 */
public enum EstadoOportunidadCalidad {

    /** Estado inicial: Oportunidad identificada (Req 70.3). */
    IDENTIFICADA("identificada"),

    /** Oportunidad en evaluacion (Req 70.3). */
    EN_EVALUACION("en_evaluacion"),

    /** Oportunidad en ejecucion (Req 70.3). */
    EN_EJECUCION("en_ejecucion"),

    /** Estado final: Oportunidad realizada (Req 70.3). */
    REALIZADA("realizada"),

    /** Estado final: Oportunidad descartada (Req 70.3). */
    DESCARTADA("descartada");

    /**
     * Maquina de estados pura de la Oportunidad de calidad (Req 70.3). Se construye una
     * sola vez y es inmutable. Los estados {@link #REALIZADA} y {@link #DESCARTADA} son
     * finales.
     */
    private static final MaquinaEstados<EstadoOportunidadCalidad> MAQUINA =
            MaquinaEstados.<EstadoOportunidadCalidad>builder(EstadoOportunidadCalidad.class)
                    .permitir(IDENTIFICADA, EN_EVALUACION, DESCARTADA)
                    .permitir(EN_EVALUACION, EN_EJECUCION, DESCARTADA)
                    .permitir(EN_EJECUCION, REALIZADA, DESCARTADA)
                    .construir();

    private final String valorBd;

    EstadoOportunidadCalidad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code oportunidad_calidad.estado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'realizada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoOportunidadCalidad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Oportunidad_Calidad no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoOportunidadCalidad estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Oportunidad_Calidad desconocido: " + valor);
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
    public boolean puedeTransicionarA(EstadoOportunidadCalidad destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

package com.dessti.crm.calidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link CambioSgc} y su maquina de estados <strong>pura</strong>
 * (Req 70.4, clausula 6.3).
 *
 * <h2>Transiciones permitidas (Req 70.4)</h2>
 * <pre>
 *   propuesto    -&gt; aprobado, rechazado
 *   aprobado     -&gt; implementado
 *   (implementado, rechazado: finales, sin salida)
 * </pre>
 * <p>El {@code rechazado} es un estado final <em>alterno</em> alcanzable solo desde
 * {@code propuesto}.</p>
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}) conforme al CHECK de V47.</p>
 */
public enum EstadoCambioSgc {

    /** Estado inicial: Cambio_SGC propuesto (Req 70.4). */
    PROPUESTO("propuesto"),

    /** Cambio_SGC aprobado (Req 70.4). */
    APROBADO("aprobado"),

    /** Estado final: Cambio_SGC implementado (Req 70.4). */
    IMPLEMENTADO("implementado"),

    /** Estado final alterno: Cambio_SGC rechazado (Req 70.4). */
    RECHAZADO("rechazado");

    /**
     * Maquina de estados pura del Cambio_SGC (Req 70.4). Se construye una sola vez y es
     * inmutable. Los estados {@link #IMPLEMENTADO} y {@link #RECHAZADO} son finales.
     */
    private static final MaquinaEstados<EstadoCambioSgc> MAQUINA =
            MaquinaEstados.<EstadoCambioSgc>builder(EstadoCambioSgc.class)
                    .permitir(PROPUESTO, APROBADO, RECHAZADO)
                    .permitir(APROBADO, IMPLEMENTADO)
                    .construir();

    private final String valorBd;

    EstadoCambioSgc(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code cambio_sgc.estado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'aprobado'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoCambioSgc desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Cambio_SGC no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoCambioSgc estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Cambio_SGC desconocido: " + valor);
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
     * segun las transiciones permitidas del Req 70.4.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoCambioSgc destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

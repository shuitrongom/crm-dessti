package com.dessti.crm.calidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link AccionCorrectiva} y su maquina de estados
 * <strong>pura</strong> (Req 70.2, clausula 10.2). Sigue el mismo patron que
 * {@code EstadoConversacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #ABIERTA} — estado inicial de toda Accion_Correctiva recien creada.</li>
 *   <li>{@link #EN_ANALISIS} — analisis de causa raiz en curso.</li>
 *   <li>{@link #EN_EJECUCION} — acciones planificadas en ejecucion.</li>
 *   <li>{@link #VERIFICACION} — verificacion de la eficacia de las acciones.</li>
 *   <li>{@link #CERRADA} — estado <strong>final</strong>: la Accion_Correctiva se dio
 *       por concluida. Solo se alcanza si la eficacia esta verificada (Property 43).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 70.2)</h2>
 * <pre>
 *   abierta       -&gt; en_analisis
 *   en_analisis   -&gt; en_ejecucion
 *   en_ejecucion  -&gt; verificacion
 *   verificacion  -&gt; cerrada (exige eficacia_verificada = true, Property 43)
 *   (cerrada: final, sin salida)
 * </pre>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'abierta'}, {@code 'en_analisis'},
 * {@code 'en_ejecucion'}, {@code 'verificacion'} o {@code 'cerrada'}, tal como
 * exige el CHECK de la migracion V47.</p>
 */
public enum EstadoAccionCorrectiva {

    /** Estado inicial: Accion_Correctiva recien registrada (Req 70.2). */
    ABIERTA("abierta"),

    /** Analisis de causa raiz en curso (Req 70.2). */
    EN_ANALISIS("en_analisis"),

    /** Acciones planificadas en ejecucion (Req 70.2). */
    EN_EJECUCION("en_ejecucion"),

    /** Verificacion de la eficacia de las acciones (Req 70.2). */
    VERIFICACION("verificacion"),

    /** Estado final: Accion_Correctiva concluida con eficacia verificada (Req 70.2). */
    CERRADA("cerrada");

    /**
     * Maquina de estados pura de la Accion_Correctiva (Req 70.2). Se construye una
     * sola vez y es inmutable. El estado final {@link #CERRADA} no declara
     * transiciones salientes. La guarda adicional de eficacia verificada para el
     * cierre la aplica el dominio ({@link AccionCorrectiva#cerrar}), no la tabla de
     * transiciones (Property 43).
     */
    private static final MaquinaEstados<EstadoAccionCorrectiva> MAQUINA =
            MaquinaEstados.<EstadoAccionCorrectiva>builder(EstadoAccionCorrectiva.class)
                    .permitir(ABIERTA, EN_ANALISIS)
                    .permitir(EN_ANALISIS, EN_EJECUCION)
                    .permitir(EN_EJECUCION, VERIFICACION)
                    .permitir(VERIFICACION, CERRADA)
                    .construir();

    private final String valorBd;

    EstadoAccionCorrectiva(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code accion_correctiva.estado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'verificacion'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoAccionCorrectiva desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Accion_Correctiva no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoAccionCorrectiva estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Accion_Correctiva desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #CERRADA}).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}
     * segun las transiciones permitidas del Req 70.2. La guarda de eficacia
     * verificada para el cierre se aplica aparte, en el dominio (Property 43).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoAccionCorrectiva destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

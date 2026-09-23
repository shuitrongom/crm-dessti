package com.dessti.crm.comercial.oportunidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Etapas del pipeline comercial de una {@link Oportunidad} y su maquina de
 * estados <strong>pura</strong> (Req 14.3, 14.4; design.md, seccion <em>State
 * Machines &rarr; Oportunidad / Pipeline</em>).
 *
 * <h2>Etapas</h2>
 * <ul>
 *   <li>{@link #NUEVO} — etapa inicial de toda Oportunidad (Req 14.1).</li>
 *   <li>{@link #CALIFICADO}, {@link #PROPUESTA}, {@link #NEGOCIACION} — etapas
 *       intermedias del embudo.</li>
 *   <li>{@link #GANADO}, {@link #PERDIDO} — etapas <strong>finales</strong> que
 *       no admiten ninguna transicion posterior (Req 14.3, 14.4).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 14.3)</h2>
 * <pre>
 *   nuevo       -&gt; calificado | perdido
 *   calificado  -&gt; propuesta  | perdido
 *   propuesta   -&gt; negociacion| perdido
 *   negociacion -&gt; ganado     | perdido
 *   (ganado, perdido: finales, sin salida)
 * </pre>
 * Es decir, el avance lineal {@code nuevo -> calificado -> propuesta ->
 * negociacion -> ganado} y, <em>desde cualquier etapa no final</em>, la
 * transicion directa a {@code perdido}. Cualquier otra transicion —incluida
 * cualquiera que parta de una etapa final— es invalida (Req 14.4).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'nuevo'}, {@code 'calificado'}, {@code 'propuesta'},
 * {@code 'negociacion'}, {@code 'ganado'}, {@code 'perdido'}. Se usa la forma sin
 * acento {@code 'negociacion'} (aunque el Req 14.3 escriba "negociacion" con
 * acento en prosa) por estabilidad de codificacion, coherente con la convencion
 * de V5 para los nombres de rol. El {@link EtapaOportunidadConverter} traduce
 * entre el enum y esta etiqueta para respetar el CHECK de V13.</p>
 *
 * <h2>Funcion pura y reutilizacion (tarea 46.1)</h2>
 * <p>Las transiciones se declaran una sola vez en {@link #MAQUINA} usando el
 * helper generico {@link MaquinaEstados}. {@link #puedeTransicionarA(EtapaOportunidad)}
 * es una funcion pura {@code (actual, destino) -> boolean} sin dependencias de
 * framework; la tarea 46.1 consolidara todas las maquinas del sistema sobre este
 * patron y la 46.2 la ejercera con la <em>Property 5</em>.</p>
 */
public enum EtapaOportunidad {

    /** Etapa inicial de una Oportunidad recien registrada (Req 14.1). */
    NUEVO("nuevo"),

    /** Oportunidad calificada tras una primera valoracion. */
    CALIFICADO("calificado"),

    /** Se ha presentado una propuesta al prospecto. */
    PROPUESTA("propuesta"),

    /** Negociacion en curso (etiqueta ASCII sin acento por estabilidad). */
    NEGOCIACION("negociacion"),

    /** Etapa final: Oportunidad ganada, convertible en Cotizacion (Req 14.5). */
    GANADO("ganado"),

    /** Etapa final: Oportunidad perdida. */
    PERDIDO("perdido");

    /**
     * Maquina de estados pura del pipeline (Req 14.3, 14.4). Se construye una
     * sola vez y es inmutable. Las etapas finales {@link #GANADO} y
     * {@link #PERDIDO} no declaran transiciones salientes, por lo que la maquina
     * las trata como finales automaticamente.
     */
    private static final MaquinaEstados<EtapaOportunidad> MAQUINA =
            MaquinaEstados.<EtapaOportunidad>builder(EtapaOportunidad.class)
                    .permitir(NUEVO, CALIFICADO, PERDIDO)
                    .permitir(CALIFICADO, PROPUESTA, PERDIDO)
                    .permitir(PROPUESTA, NEGOCIACION, PERDIDO)
                    .permitir(NEGOCIACION, GANADO, PERDIDO)
                    .construir();

    private final String valorBd;

    EtapaOportunidad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code oportunidad.etapa}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V13.
     *
     * @return la etiqueta de base de datos de la etapa.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye la etapa a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'nuevo'}).
     * @return la etapa correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ninguna etapa conocida.
     */
    public static EtapaOportunidad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("La etapa de la Oportunidad no puede ser nula");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EtapaOportunidad etapa : values()) {
            if (etapa.valorBd.equals(normalizado)) {
                return etapa;
            }
        }
        throw new IllegalArgumentException("Etapa de Oportunidad desconocida: " + valor);
    }

    /**
     * Indica si esta etapa es <strong>final</strong> (ganado o perdido) y por
     * tanto no admite ninguna transicion posterior (Req 14.3, 14.4).
     *
     * @return {@code true} si es una etapa final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura del pipeline: indica si desde esta etapa se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 14.3. Toda
     * transicion que parta de una etapa final devuelve {@code false} (Req 14.4).
     *
     * @param destino etapa destino pretendida; obligatoria.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EtapaOportunidad destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

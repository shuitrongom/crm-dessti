package com.dessti.crm.social.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link PublicacionSocial} y su maquina de estados
 * <strong>pura</strong> (Req 65.3, 65.4). Sigue el mismo patron que
 * {@link EstadoConversacion} y {@code EstadoNomina}, delegando la decision de
 * transicion en la {@link MaquinaEstados} generica de la plataforma.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #BORRADOR} — estado inicial de toda Publicacion_Social recien
 *       creada (Req 65.1). Aun no esta programada para su publicacion.</li>
 *   <li>{@link #PROGRAMADA} — la Publicacion_Social quedo programada para
 *       publicarse en su {@code fecha_programada} (Req 65.1).</li>
 *   <li>{@link #PUBLICADA} — estado <strong>final</strong>: el adaptador confirmo
 *       la publicacion exitosa en el Canal_Social (Req 65.5).</li>
 *   <li>{@link #FALLIDA} — estado <strong>final</strong>: agotada la politica de
 *       reintentos, la publicacion no pudo completarse (Req 65.5, 65.6).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 65.3)</h2>
 * <pre>
 *   borrador   -&gt; programada
 *   programada -&gt; publicada, fallida
 *   (publicada, fallida: finales, sin salida)
 * </pre>
 * Cualquier otra transicion es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409, Req 65.4),
 * conservando el estado actual.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'borrador'}, {@code 'programada'},
 * {@code 'publicada'} o {@code 'fallida'}, tal como exige el CHECK de la migracion
 * V43. El {@link EstadoPublicacionConverter} traduce entre el enum y esta etiqueta.</p>
 */
public enum EstadoPublicacion {

    /** Estado inicial: Publicacion_Social en borrador, aun sin programar (Req 65.1). */
    BORRADOR("borrador"),

    /** Publicacion_Social programada para su fecha de publicacion (Req 65.1). */
    PROGRAMADA("programada"),

    /** Estado final: publicacion confirmada por el adaptador (Req 65.5). */
    PUBLICADA("publicada"),

    /** Estado final: publicacion fallida tras agotar reintentos (Req 65.5, 65.6). */
    FALLIDA("fallida");

    /**
     * Maquina de estados pura de la Publicacion_Social (Req 65.3). Se construye una
     * sola vez y es inmutable. Los estados {@link #PUBLICADA} y {@link #FALLIDA} no
     * declaran transiciones salientes: son finales.
     */
    private static final MaquinaEstados<EstadoPublicacion> MAQUINA =
            MaquinaEstados.<EstadoPublicacion>builder(EstadoPublicacion.class)
                    .permitir(BORRADOR, PROGRAMADA)
                    .permitir(PROGRAMADA, PUBLICADA, FALLIDA)
                    .construir();

    private final String valorBd;

    EstadoPublicacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code publicacion_social.estado}, en minusculas ASCII.
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
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoPublicacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Publicacion_Social no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoPublicacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Publicacion_Social desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #PUBLICADA} o
     * {@link #FALLIDA}).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}
     * segun las transiciones permitidas del Req 65.3.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoPublicacion destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

package com.dessti.crm.social.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link Conversacion} y su maquina de estados <strong>pura</strong>
 * (Req 64.10, handover). Sigue el mismo patron que {@code EstadoActivoFijo}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #ABIERTA} — estado inicial de toda Conversacion recien creada
 *       (por un entrante o iniciada por la Empresa). Aun sin asignar.</li>
 *   <li>{@link #ASIGNADA} — la Conversacion fue asignada/transferida a un Usuario
 *       (handover, Req 64.10). Puede reasignarse (permanece en {@code asignada}).</li>
 *   <li>{@link #CERRADA} — estado <strong>final</strong>: la atencion se dio por
 *       concluida. Conserva el historial de Mensaje_Social.</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 64.10)</h2>
 * <pre>
 *   abierta  -&gt; asignada, cerrada
 *   asignada -&gt; asignada (reasignacion), abierta (liberar), cerrada
 *   (cerrada: final, sin salida)
 * </pre>
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'abierta'}, {@code 'asignada'} o {@code 'cerrada'},
 * tal como exige el CHECK de la migracion V41.</p>
 */
public enum EstadoConversacion {

    /** Estado inicial: Conversacion abierta y sin asignar (Req 64.10). */
    ABIERTA("abierta"),

    /** Conversacion asignada/transferida a un Usuario (handover, Req 64.10). */
    ASIGNADA("asignada"),

    /** Estado final: atencion concluida; conserva historial (Req 64.10). */
    CERRADA("cerrada");

    /**
     * Maquina de estados pura de la Conversacion (Req 64.10). Se construye una sola
     * vez y es inmutable. La reasignacion se modela como {@code asignada -> asignada}
     * (transicion explicita) y la liberacion como {@code asignada -> abierta}. El
     * estado final {@link #CERRADA} no declara transiciones salientes.
     */
    private static final MaquinaEstados<EstadoConversacion> MAQUINA =
            MaquinaEstados.<EstadoConversacion>builder(EstadoConversacion.class)
                    .permitir(ABIERTA, ASIGNADA, CERRADA)
                    .permitir(ASIGNADA, ASIGNADA, ABIERTA, CERRADA)
                    .construir();

    private final String valorBd;

    EstadoConversacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code conversacion.estado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'abierta'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoConversacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Conversacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoConversacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Conversacion desconocido: " + valor);
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
     * segun las transiciones permitidas del Req 64.10.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoConversacion destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

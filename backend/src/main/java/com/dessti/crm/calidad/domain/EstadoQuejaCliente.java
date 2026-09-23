package com.dessti.crm.calidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link QuejaCliente} y su maquina de estados <strong>pura</strong>
 * (Req 70.1, clausula 10.2).
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #REGISTRADA} — estado inicial de toda Queja_Cliente recien creada.</li>
 *   <li>{@link #VINCULADA} — la queja fue vinculada -sin obligar- como entrada a una
 *       Accion_Correctiva (Req 70.1).</li>
 *   <li>{@link #ATENDIDA} — estado <strong>final</strong>: la queja se dio por
 *       atendida.</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 70.1)</h2>
 * <pre>
 *   registrada -&gt; vinculada, atendida
 *   vinculada  -&gt; atendida
 *   (atendida: final, sin salida)
 * </pre>
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'registrada'}, {@code 'vinculada'} o {@code 'atendida'},
 * tal como exige el CHECK de la migracion V47.</p>
 */
public enum EstadoQuejaCliente {

    /** Estado inicial: Queja_Cliente recien registrada (Req 70.1). */
    REGISTRADA("registrada"),

    /** La queja fue vinculada como entrada a una Accion_Correctiva (Req 70.1). */
    VINCULADA("vinculada"),

    /** Estado final: la queja fue atendida (Req 70.1). */
    ATENDIDA("atendida");

    /**
     * Maquina de estados pura de la Queja_Cliente (Req 70.1). Se construye una sola
     * vez y es inmutable. El estado final {@link #ATENDIDA} no declara transiciones
     * salientes.
     */
    private static final MaquinaEstados<EstadoQuejaCliente> MAQUINA =
            MaquinaEstados.<EstadoQuejaCliente>builder(EstadoQuejaCliente.class)
                    .permitir(REGISTRADA, VINCULADA, ATENDIDA)
                    .permitir(VINCULADA, ATENDIDA)
                    .construir();

    private final String valorBd;

    EstadoQuejaCliente(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code queja_cliente.estado}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'registrada'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoQuejaCliente desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Queja_Cliente no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoQuejaCliente estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Queja_Cliente desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #ATENDIDA}).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}
     * segun las transiciones permitidas del Req 70.1.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoQuejaCliente destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

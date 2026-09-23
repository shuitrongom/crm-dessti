package com.dessti.crm.vertical.anuncios.levantamiento.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link LevantamientoSitio} y su maquina de estados
 * <strong>pura</strong> (Req 16.1, 16.4; design.md, seccion <em>State Machines</em>).
 * Sigue el mismo patron que {@code EstadoOrdenFabricacion} y
 * {@code EstadoPruebaDiseno}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #EN_PROCESO} — estado inicial de todo Levantamiento_Sitio recien
 *       creado (Req 16.1).</li>
 *   <li>{@link #COMPLETADO} — estado <strong>final</strong>: el levantamiento se
 *       marco como completado, con actor y marca temporal UTC (Req 16.4). Es la
 *       precondicion de la programacion de instalacion del Sitio (Req 16.5,
 *       bloque 22).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 16.4)</h2>
 * <pre>
 *   en_proceso -&gt; completado
 *   (completado: final, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida completar un Levantamiento ya completado—
 * es invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual sin modificarlo.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'en_proceso'}, {@code 'completado'}, tal como exige
 * el CHECK de la migracion V19. El {@link EstadoLevantamientoConverter} traduce
 * entre el enum y esta etiqueta.</p>
 */
public enum EstadoLevantamiento {

    /** Estado inicial de un Levantamiento_Sitio recien creado (Req 16.1). */
    EN_PROCESO("en_proceso"),

    /** Estado final: Levantamiento_Sitio completado con actor y UTC (Req 16.4). */
    COMPLETADO("completado");

    /**
     * Maquina de estados pura del Levantamiento_Sitio (Req 16.4). Se construye una
     * sola vez y es inmutable. El estado final {@link #COMPLETADO} no declara
     * transiciones salientes, por lo que la maquina lo trata como final
     * automaticamente.
     */
    private static final MaquinaEstados<EstadoLevantamiento> MAQUINA =
            MaquinaEstados.<EstadoLevantamiento>builder(EstadoLevantamiento.class)
                    .permitir(EN_PROCESO, COMPLETADO)
                    .construir();

    private final String valorBd;

    EstadoLevantamiento(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code levantamiento_sitio.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V19.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'en_proceso'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoLevantamiento desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Levantamiento_Sitio no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoLevantamiento estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Levantamiento_Sitio desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@code completado}) y por
     * tanto no admite ninguna transicion posterior (Req 16.4).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 16.4. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoLevantamiento destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

package com.dessti.crm.vertical.anuncios.mantenimiento.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link TicketServicio} y su maquina de estados
 * <strong>pura</strong> (Req 20.4, 20.5; design.md, seccion <em>State Machines</em>).
 * Sigue el mismo patron que {@code EstadoOrdenFabricacion} y
 * {@code EstadoPermisoInstalacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #ABIERTO} — estado inicial de todo Ticket_Servicio recien generado
 *       (Req 20.2).</li>
 *   <li>{@link #ASIGNADO} — el Ticket_Servicio se asigno a un tecnico o a una
 *       Cuadrilla (Req 20.3).</li>
 *   <li>{@link #EN_PROCESO} — el destinatario esta atendiendo el ticket.</li>
 *   <li>{@link #RESUELTO} — el ticket se resolvio; al entrar a este estado se
 *       registra el cumplimiento o incumplimiento del SLA (Req 20.6).</li>
 *   <li>{@link #CERRADO} — estado <strong>final</strong>: el ticket se cerro tras
 *       su resolucion (Req 20.4).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 20.4)</h2>
 * <pre>
 *   abierto     -&gt; asignado
 *   asignado    -&gt; en_proceso
 *   en_proceso  -&gt; resuelto
 *   resuelto    -&gt; cerrado
 *   (cerrado: final, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquiera que parta del estado final— es
 * invalida (Req 20.5) y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409),
 * conservando el estado actual sin modificarlo.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'abierto'}, {@code 'asignado'}, {@code 'en_proceso'},
 * {@code 'resuelto'}, {@code 'cerrado'}, tal como exige el CHECK de la migracion
 * V27. Notese que {@code 'en_proceso'} se persiste <strong>sin acento</strong> por
 * estabilidad de codificacion; la capa de presentacion aplica la etiqueta visible
 * acentuada si procede. El {@link EstadoTicketServicioConverter} traduce entre el
 * enum y esta etiqueta.</p>
 */
public enum EstadoTicketServicio {

    /** Estado inicial de un Ticket_Servicio recien generado (Req 20.2). */
    ABIERTO("abierto"),

    /** El Ticket_Servicio se asigno a un tecnico o a una Cuadrilla (Req 20.3). */
    ASIGNADO("asignado"),

    /**
     * El destinatario esta atendiendo el ticket. Etiqueta ASCII {@code en_proceso}
     * (sin acento) por estabilidad de codificacion, coherente con V27.
     */
    EN_PROCESO("en_proceso"),

    /** El ticket se resolvio; se registra el cumplimiento del SLA (Req 20.6). */
    RESUELTO("resuelto"),

    /** Estado final: el ticket se cerro tras su resolucion (Req 20.4). */
    CERRADO("cerrado");

    /**
     * Maquina de estados pura del Ticket_Servicio (Req 20.4, 20.5). Se construye
     * una sola vez y es inmutable. El estado final {@link #CERRADO} no declara
     * transiciones salientes, por lo que la maquina lo trata como final
     * automaticamente.
     */
    private static final MaquinaEstados<EstadoTicketServicio> MAQUINA =
            MaquinaEstados.<EstadoTicketServicio>builder(EstadoTicketServicio.class)
                    .permitir(ABIERTO, ASIGNADO)
                    .permitir(ASIGNADO, EN_PROCESO)
                    .permitir(EN_PROCESO, RESUELTO)
                    .permitir(RESUELTO, CERRADO)
                    .construir();

    private final String valorBd;

    EstadoTicketServicio(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code ticket_servicio.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V27.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'abierto'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoTicketServicio desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Ticket_Servicio no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoTicketServicio estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Ticket_Servicio desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (cerrado) y por tanto no
     * admite ninguna transicion posterior (Req 20.4, 20.5).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 20.4. Toda
     * transicion que parta de un estado final devuelve {@code false} (Req 20.5).
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoTicketServicio destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

package com.dessti.crm.vertical.anuncios.pruebadiseno.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link PruebaDiseno} y su maquina de estados <strong>pura</strong>
 * (Req 15; design.md, seccion <em>State Machines</em>). Sigue el mismo patron que
 * {@code EstadoCotizacion} y {@code EtapaOportunidad}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #PENDIENTE} — estado inicial de toda Prueba_Diseno recien generada
 *       (Req 15.1).</li>
 *   <li>{@link #APROBADA} — estado <strong>final</strong>: el Cliente aprobo el
 *       arte (Req 15.2).</li>
 *   <li>{@link #RECHAZADA} — estado <strong>final</strong>: el Cliente rechazo el
 *       arte; el rechazo genera una nueva Prueba_Diseno con numero de version + 1
 *       en {@link #PENDIENTE} (Req 15.3, Property 8).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 15.2, 15.3)</h2>
 * <pre>
 *   pendiente -&gt; aprobada | rechazada
 *   (aprobada, rechazada: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida decidir una Prueba_Diseno ya decidida— es
 * invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409). Esto
 * refuerza la inmutabilidad del historial (Req 15.4): una version aprobada o
 * rechazada no puede volver a cambiar de estado.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'pendiente'}, {@code 'aprobada'},
 * {@code 'rechazada'}, tal como exige el CHECK de la migracion V16. El
 * {@link EstadoPruebaDisenoConverter} traduce entre el enum y esta etiqueta.</p>
 */
public enum EstadoPruebaDiseno {

    /** Estado inicial de una Prueba_Diseno recien generada (Req 15.1). */
    PENDIENTE("pendiente"),

    /** Estado final: Prueba_Diseno aprobada por el Cliente (Req 15.2). */
    APROBADA("aprobada"),

    /** Estado final: Prueba_Diseno rechazada por el Cliente (Req 15.3). */
    RECHAZADA("rechazada");

    /**
     * Maquina de estados pura de la Prueba_Diseno (Req 15.2, 15.3). Se construye
     * una sola vez y es inmutable. Los estados finales {@link #APROBADA} y
     * {@link #RECHAZADA} no declaran transiciones salientes, por lo que la maquina
     * los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoPruebaDiseno> MAQUINA =
            MaquinaEstados.<EstadoPruebaDiseno>builder(EstadoPruebaDiseno.class)
                    .permitir(PENDIENTE, APROBADA, RECHAZADA)
                    .construir();

    private final String valorBd;

    EstadoPruebaDiseno(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code prueba_diseno.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V16.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'pendiente'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoPruebaDiseno desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la Prueba_Diseno no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoPruebaDiseno estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Prueba_Diseno desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> (aprobada o rechazada) y por
     * tanto no admite ninguna transicion posterior (Req 15.2, 15.3, 15.4).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 15.2/15.3. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoPruebaDiseno destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

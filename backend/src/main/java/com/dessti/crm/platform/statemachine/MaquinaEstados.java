package com.dessti.crm.platform.statemachine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maquina de estados generica, <strong>pura</strong> e inmutable, basada en una
 * tabla de transiciones sobre un tipo enumerado {@code E} (design.md, seccion
 * <em>State Machines</em>).
 *
 * <p>Modela el patron comun del diseno: dado un estado actual y un estado
 * destino, la transicion es valida <em>si y solo si</em> pertenece al conjunto
 * de transiciones declaradas para el estado actual. Un estado sin transiciones
 * salientes es <strong>final</strong> y no admite ninguna transicion posterior.
 * La funcion {@link #puedeTransicionar(Enum, Enum)} es una funcion pura
 * {@code (actual, destino) -> boolean}: determinista, sin efectos secundarios y
 * sin dependencias de framework, lo que la hace trivialmente verificable por
 * pruebas unitarias y de propiedad.</p>
 *
 * <p><strong>Reutilizacion (tarea 46.1 / Property 5):</strong> este componente
 * es la base sobre la que estan consolidadas las maquinas de estado de todos los
 * modulos. La consolidacion de la tarea 46.1 esta <em>completa</em>: las doce
 * maquinas con transiciones dirigidas por eventos del alcance de la Property 5
 * declaran su tabla de transiciones aqui y delegan la decision en
 * {@link #puedeTransicionar(Enum, Enum)} y {@link #esFinal(Enum)}, sin duplicar
 * la logica de aceptacion/rechazo ni el tratamiento de estados finales:</p>
 * <ul>
 *   <li>{@code EstadoCotizacion} (comercial.cotizacion) — Req 6.6, 6.7.</li>
 *   <li>{@code EstadoOrdenFabricacion} (operacion.produccion) — Req 7.5, 7.6.</li>
 *   <li>{@code EtapaOportunidad} (comercial.oportunidad) — Req 14.3, 14.4.</li>
 *   <li>{@code EstadoPermisoInstalacion} (vertical.anuncios.permiso) — Req 17.2, 17.3.</li>
 *   <li>{@code EstadoOrdenTrabajoInstalacion} (vertical.anuncios.instalacion) — Req 19.5.</li>
 *   <li>{@code EstadoTicketServicio} (mantenimiento) — Req 20.4, 20.5.</li>
 *   <li>{@code EstadoRequisicionCompra} (compras.requisicion) — Req 30.3, 30.4.</li>
 *   <li>{@code EstadoOrdenCompra} (compras.ordencompra) — Req 31.6, 31.7.</li>
 *   <li>{@code EstadoFacturaProveedor} (compras.factura) — Req 33.6.</li>
 *   <li>{@code EstadoFactura} (facturacion.factura, CFDI) — Req 35.7.</li>
 *   <li>{@code EstadoNomina} (rhnomina.nomina) — Req 41.5, 41.6.</li>
 *   <li>{@code EstadoPublicacion} (social) — Req 65.3, 65.4.</li>
 * </ul>
 * <p>Otras maquinas del sistema tambien reutilizan este helper (por ejemplo
 * {@code EstadoCuentaPorCobrar}, {@code EstadoCuentaPorPagar},
 * {@code EstadoConversacion}, {@code EstadoNotaCredito},
 * {@code EstadoReciboNomina}, {@code EstadoPruebaDiseno},
 * {@code EstadoLevantamiento}, {@code EstadoActivoFijo}). Los estados que son
 * <em>derivaciones de solo lectura</em> (por ejemplo {@code EstadoObjetivo},
 * {@code EstadoConsolidadoProyecto}) o clasificaciones de resultado sin
 * transiciones encadenadas (por ejemplo {@code EstadoNotificacion},
 * {@code EstadoEntrega}) no se modelan como maquinas dirigidas por eventos y, de
 * forma deliberada, no usan este componente (design.md, seccion
 * <em>State Machines</em>).</p>
 *
 * <p>Instancias tipicas se construyen una sola vez (por ejemplo, en una constante
 * {@code static final} del enum de estados del modulo) mediante el
 * {@link Builder}. La instancia resultante es inmutable y segura para compartir
 * entre hilos.</p>
 *
 * @param <E> tipo enumerado que representa los estados de la maquina.
 */
public final class MaquinaEstados<E extends Enum<E>> {

    private final Class<E> tipoEstado;
    private final Map<E, Set<E>> transiciones;

    private MaquinaEstados(Class<E> tipoEstado, Map<E, Set<E>> transiciones) {
        this.tipoEstado = tipoEstado;
        this.transiciones = transiciones;
    }

    /**
     * Crea un {@link Builder} para declarar la tabla de transiciones de una
     * maquina sobre el tipo enumerado indicado.
     *
     * @param tipoEstado clase del enum de estados; obligatoria.
     * @param <E>        tipo enumerado de estados.
     * @return un constructor vacio al que agregar transiciones.
     */
    public static <E extends Enum<E>> Builder<E> builder(Class<E> tipoEstado) {
        return new Builder<>(tipoEstado);
    }

    /**
     * Indica si la transicion {@code actual -> destino} es valida segun la tabla
     * declarada. Funcion pura: mismo resultado para las mismas entradas, sin
     * efectos secundarios.
     *
     * <p>Devuelve {@code false} cuando {@code actual} es un estado final (sin
     * transiciones salientes) o cuando {@code destino} no figura entre las
     * transiciones permitidas de {@code actual}. Una transicion de un estado a
     * si mismo solo es valida si se declaro explicitamente.</p>
     *
     * @param actual  estado de partida; obligatorio.
     * @param destino estado al que se pretende transitar; obligatorio.
     * @return {@code true} si la transicion pertenece al conjunto declarado.
     */
    public boolean puedeTransicionar(E actual, E destino) {
        Objects.requireNonNull(actual, "El estado actual es obligatorio");
        Objects.requireNonNull(destino, "El estado destino es obligatorio");
        return transiciones.getOrDefault(actual, Set.of()).contains(destino);
    }

    /**
     * Indica si el estado dado es <strong>final</strong>, es decir, no tiene
     * ninguna transicion saliente declarada (design.md: los estados finales no
     * admiten transiciones posteriores).
     *
     * @param estado estado a evaluar; obligatorio.
     * @return {@code true} si el estado no admite ninguna transicion posterior.
     */
    public boolean esFinal(E estado) {
        Objects.requireNonNull(estado, "El estado es obligatorio");
        return transiciones.getOrDefault(estado, Set.of()).isEmpty();
    }

    /**
     * Devuelve el conjunto (inmutable) de estados destino alcanzables en un solo
     * paso desde {@code actual}. Para un estado final devuelve el conjunto vacio.
     *
     * @param actual estado de partida; obligatorio.
     * @return conjunto inmutable de destinos permitidos.
     */
    public Set<E> transicionesDesde(E actual) {
        Objects.requireNonNull(actual, "El estado actual es obligatorio");
        return transiciones.getOrDefault(actual, Set.of());
    }

    /**
     * Tipo enumerado de estados que gobierna esta maquina.
     *
     * @return la clase del enum de estados.
     */
    public Class<E> tipoEstado() {
        return tipoEstado;
    }

    /**
     * Constructor de {@link MaquinaEstados}: declara transiciones y produce una
     * maquina inmutable. No es seguro para uso concurrente; se emplea durante la
     * inicializacion estatica y se descarta.
     *
     * @param <E> tipo enumerado de estados.
     */
    public static final class Builder<E extends Enum<E>> {

        private final Class<E> tipoEstado;
        private final Map<E, Set<E>> transiciones;

        private Builder(Class<E> tipoEstado) {
            this.tipoEstado = Objects.requireNonNull(tipoEstado, "El tipo de estado es obligatorio");
            this.transiciones = new EnumMap<>(tipoEstado);
        }

        /**
         * Declara que desde {@code desde} se puede transitar a cada uno de los
         * estados {@code destinos}. Invocaciones sucesivas para el mismo estado
         * de origen acumulan destinos.
         *
         * @param desde    estado de origen; obligatorio.
         * @param destinos estados destino permitidos; obligatorio y no vacio.
         * @return este mismo constructor para encadenar.
         */
        @SafeVarargs
        public final Builder<E> permitir(E desde, E... destinos) {
            Objects.requireNonNull(desde, "El estado de origen es obligatorio");
            Objects.requireNonNull(destinos, "Los destinos son obligatorios");
            if (destinos.length == 0) {
                throw new IllegalArgumentException("Debe indicarse al menos un estado destino");
            }
            Set<E> conjunto = transiciones.computeIfAbsent(desde, k -> EnumSet.noneOf(tipoEstado));
            for (E destino : destinos) {
                conjunto.add(Objects.requireNonNull(destino, "Un estado destino no puede ser nulo"));
            }
            return this;
        }

        /**
         * Materializa una {@link MaquinaEstados} inmutable con las transiciones
         * declaradas. Los conjuntos de destino se copian a vistas inmutables.
         *
         * @return la maquina de estados inmutable.
         */
        public MaquinaEstados<E> construir() {
            Map<E, Set<E>> copia = new EnumMap<>(tipoEstado);
            for (Map.Entry<E, Set<E>> entrada : transiciones.entrySet()) {
                copia.put(entrada.getKey(), Set.copyOf(entrada.getValue()));
            }
            return new MaquinaEstados<>(tipoEstado, copia);
        }
    }
}

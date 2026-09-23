package com.dessti.crm.activosfijos.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link ActivoFijo} y su maquina de estados <strong>pura</strong>
 * (Req 44.4). Sigue el mismo patron que {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #ACTIVO} — estado inicial de todo Activo_Fijo recien dado de alta
 *       (Req 44.1). Es el unico estado sobre el que se puede depreciar (Req 44.3).</li>
 *   <li>{@link #BAJA} — estado <strong>final</strong>: el Activo_Fijo fue dado de
 *       baja o vendido (Req 44.4). La baja es logica: conserva el historico
 *       (el Activo_Fijo y sus depreciaciones no se borran).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 44.4)</h2>
 * <pre>
 *   activo -&gt; baja
 *   (baja: final, sin salida)
 * </pre>
 * Es una maquina de estados minima pero explicita (ver DECISION 1 de V37): se
 * prefiere un estado con etiqueta propia frente a un booleano, porque la baja es
 * una transicion de negocio auditable (Req 44.6) y el filtro del listado
 * (Req 44.5) es por esta columna. Volver a dar de baja un Activo_Fijo ya en
 * {@link #BAJA} es una transicion invalida (no hay salida de un estado final).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'activo'} o {@code 'baja'}, tal como exige el CHECK
 * de la migracion V37. El {@link EstadoActivoFijoConverter} traduce entre el enum y
 * esta etiqueta.</p>
 */
public enum EstadoActivoFijo {

    /** Estado inicial: Activo_Fijo vigente y depreciable (Req 44.1, 44.3). */
    ACTIVO("activo"),

    /** Estado final: Activo_Fijo dado de baja o vendido; conserva historico (Req 44.4). */
    BAJA("baja");

    /**
     * Maquina de estados pura del Activo_Fijo (Req 44.4). Se construye una sola vez
     * y es inmutable. El estado final {@link #BAJA} no declara transiciones
     * salientes, por lo que la maquina lo trata como final automaticamente.
     */
    private static final MaquinaEstados<EstadoActivoFijo> MAQUINA =
            MaquinaEstados.<EstadoActivoFijo>builder(EstadoActivoFijo.class)
                    .permitir(ACTIVO, BAJA)
                    .construir();

    private final String valorBd;

    EstadoActivoFijo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code activo_fijo.estado}, en minusculas
     * ASCII, tal como la exige el CHECK de la migracion V37.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'activo'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoActivoFijo desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Activo_Fijo no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoActivoFijo estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Activo_Fijo desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@link #BAJA}) y por tanto
     * no admite ninguna transicion posterior (Req 44.4).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 44.4.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoActivoFijo destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

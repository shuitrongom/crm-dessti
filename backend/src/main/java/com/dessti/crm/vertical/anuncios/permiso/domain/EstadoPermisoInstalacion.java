package com.dessti.crm.vertical.anuncios.permiso.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link PermisoInstalacion} y su maquina de estados
 * <strong>pura</strong> (Req 17.2, 17.3; design.md, seccion <em>State Machines &rarr;
 * Permiso de Instalacion</em>). Sigue el mismo patron que {@code EstadoLevantamiento}
 * y {@code EstadoOrdenFabricacion}.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #SOLICITADO} — estado inicial de todo Permiso_Instalacion recien
 *       creado (Req 17.1).</li>
 *   <li>{@link #APROBADO} — estado <strong>final</strong>: el permiso fue aprobado,
 *       con actor y marca temporal UTC (Req 17.2). Es la precondicion de la
 *       programacion de instalacion del Sitio (Req 17.4, bloque 22).</li>
 *   <li>{@link #RECHAZADO} — estado <strong>final</strong>: el permiso fue rechazado,
 *       con actor y marca temporal UTC (Req 17.2).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas (Req 17.2)</h2>
 * <pre>
 *   solicitado -&gt; aprobado
 *   solicitado -&gt; rechazado
 *   (aprobado, rechazado: finales, sin salida)
 * </pre>
 * Cualquier otra transicion —incluida cualquier salida desde un estado final— es
 * invalida y el dominio la rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409, Req 17.3),
 * conservando el estado actual sin modificarlo.
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'solicitado'}, {@code 'aprobado'}, {@code 'rechazado'},
 * tal como exige el CHECK de la migracion V20. El
 * {@link EstadoPermisoInstalacionConverter} traduce entre el enum y esta etiqueta.</p>
 */
public enum EstadoPermisoInstalacion {

    /** Estado inicial de un Permiso_Instalacion recien creado (Req 17.1). */
    SOLICITADO("solicitado"),

    /** Estado final: permiso aprobado con actor y UTC (Req 17.2). */
    APROBADO("aprobado"),

    /** Estado final: permiso rechazado con actor y UTC (Req 17.2). */
    RECHAZADO("rechazado");

    /**
     * Maquina de estados pura del Permiso_Instalacion (Req 17.2). Se construye una
     * sola vez y es inmutable. Los estados finales {@link #APROBADO} y
     * {@link #RECHAZADO} no declaran transiciones salientes, por lo que la maquina
     * los trata como finales automaticamente.
     */
    private static final MaquinaEstados<EstadoPermisoInstalacion> MAQUINA =
            MaquinaEstados.<EstadoPermisoInstalacion>builder(EstadoPermisoInstalacion.class)
                    .permitir(SOLICITADO, APROBADO, RECHAZADO)
                    .construir();

    private final String valorBd;

    EstadoPermisoInstalacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code permiso_instalacion.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V20.
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
     * @param valor etiqueta almacenada (por ejemplo {@code 'solicitado'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun estado conocido.
     */
    public static EstadoPermisoInstalacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Permiso_Instalacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoPermisoInstalacion estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Permiso_Instalacion desconocido: " + valor);
    }

    /**
     * Indica si este estado es <strong>final</strong> ({@code aprobado} o
     * {@code rechazado}) y por tanto no admite ninguna transicion posterior
     * (Req 17.2, 17.3).
     *
     * @return {@code true} si es un estado final.
     */
    public boolean esFinal() {
        return MAQUINA.esFinal(this);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a
     * {@code destino} segun las transiciones permitidas del Req 17.2. Toda
     * transicion que parta de un estado final devuelve {@code false}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoPermisoInstalacion destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

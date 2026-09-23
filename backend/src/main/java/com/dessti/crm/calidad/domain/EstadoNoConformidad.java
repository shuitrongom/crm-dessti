package com.dessti.crm.calidad.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de una {@link NoConformidad} y su maquina de estados <strong>pura</strong>
 * (Req 70.2, clausula 10.2).
 *
 * <h2>Transiciones permitidas (Req 70.2)</h2>
 * <pre>
 *   abierta        -&gt; en_tratamiento, cerrada
 *   en_tratamiento -&gt; cerrada
 *   (cerrada: final, sin salida)
 * </pre>
 *
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'abierta'}, {@code 'en_tratamiento'} o
 * {@code 'cerrada'}, tal como exige el CHECK de la migracion V47.</p>
 */
public enum EstadoNoConformidad {

    /** Estado inicial: No_Conformidad recien registrada (Req 70.2). */
    ABIERTA("abierta"),

    /** No_Conformidad en tratamiento (con Accion_Correctiva asociada, Req 70.2). */
    EN_TRATAMIENTO("en_tratamiento"),

    /** Estado final: No_Conformidad cerrada (Req 70.2). */
    CERRADA("cerrada");

    /**
     * Maquina de estados pura de la No_Conformidad (Req 70.2). Se construye una sola
     * vez y es inmutable. El estado final {@link #CERRADA} no declara transiciones
     * salientes.
     */
    private static final MaquinaEstados<EstadoNoConformidad> MAQUINA =
            MaquinaEstados.<EstadoNoConformidad>builder(EstadoNoConformidad.class)
                    .permitir(ABIERTA, EN_TRATAMIENTO, CERRADA)
                    .permitir(EN_TRATAMIENTO, CERRADA)
                    .construir();

    private final String valorBd;

    EstadoNoConformidad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code no_conformidad.estado}, en minusculas ASCII.
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
    public static EstadoNoConformidad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado de la No_Conformidad no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoNoConformidad estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de No_Conformidad desconocido: " + valor);
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
     * segun las transiciones permitidas del Req 70.2.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoNoConformidad destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

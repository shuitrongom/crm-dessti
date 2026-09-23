package com.dessti.crm.estrategia.domain;

import java.util.Locale;

/**
 * Estado <strong>derivado</strong> (de solo lectura) de un
 * {@link ObjetivoEstrategico} (Req 58.10). No se persiste ni se gestiona con una
 * maquina de estados: es el resultado de la funcion pura
 * {@link DerivacionEstadoObjetivo}, calculada en tiempo de consulta a partir del
 * {@code avance} (0..100) del objetivo y de la fraccion del periodo transcurrida.
 *
 * <p>Se modela igual que {@code EstadoConsolidadoProyecto} (bloque 22): un enum
 * con etiqueta ASCII en minusculas ({@link #valorBd()}) que se expone en los DTOs
 * de salida, coherente con el resto de estados del sistema. Al ser derivado, no
 * hay transiciones ni persistencia; una consulta posterior lo recalcula.</p>
 *
 * <h2>Valores (Req 58.10)</h2>
 * <ul>
 *   <li>{@link #EN_RIESGO}: el avance queda por debajo de la fraccion del periodo
 *       transcurrida (rezago); el objetivo va retrasado respecto a lo esperado.</li>
 *   <li>{@link #EN_CURSO}: el objetivo avanza de forma consistente con la fraccion
 *       del periodo transcurrida (aun no cumplido).</li>
 *   <li>{@link #CUMPLIDO}: el avance alcanzo el 100% (Req 58.9).</li>
 * </ul>
 */
public enum EstadoObjetivo {

    /** El avance va por debajo de lo esperado para el periodo transcurrido (rezago). */
    EN_RIESGO("en_riesgo"),

    /** El avance es consistente con la fraccion del periodo transcurrida. */
    EN_CURSO("en_curso"),

    /** El objetivo alcanzo el 100% de avance (Req 58.9). */
    CUMPLIDO("cumplido");

    private final String valorBd;

    EstadoObjetivo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII en minusculas del estado derivado, usada para exponerlo en los
     * DTOs de salida (Req 58.10).
     *
     * @return la etiqueta del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta (inversa de {@link #valorBd()}).
     * La comparacion es insensible a mayusculas y recorta espacios. Se ofrece por
     * simetria con el resto de enums del dominio; el estado derivado normalmente no
     * se persiste ni se recibe en peticiones.
     *
     * @param valor etiqueta (por ejemplo {@code 'en_curso'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoObjetivo desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Objetivo_Estrategico no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoObjetivo estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Objetivo_Estrategico desconocido: " + valor);
    }
}

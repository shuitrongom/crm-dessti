package com.dessti.crm.contabilidad.polizas.domain;

import java.util.Locale;

import com.dessti.crm.platform.statemachine.MaquinaEstados;

/**
 * Estados de un {@link PeriodoContable} (cierre de periodo mensual) y su maquina de
 * estados <strong>pura</strong>.
 *
 * <h2>Estados</h2>
 * <ul>
 *   <li>{@link #ABIERTO} — el periodo admite el registro y reverso de
 *       Polizas_Contables. Es el estado por defecto (incluso sin fila explicita en
 *       {@code periodo_contable}).</li>
 *   <li>{@link #CERRADO} — el periodo esta bloqueado: ninguna Poliza_Contable nueva
 *       ni reverso puede afectarlo (candado contable), protegiendo la informacion
 *       ya declarada. Se puede reabrir con motivo (reapertura auditada).</li>
 * </ul>
 *
 * <h2>Transiciones permitidas</h2>
 * <pre>
 *   abierto -&gt; cerrado   (cerrar el periodo)
 *   cerrado -&gt; abierto   (reapertura auditada con motivo)
 * </pre>
 * A diferencia de otras maquinas del sistema, aqui NO hay estados finales: un
 * periodo cerrado puede reabrirse y viceversa. Toda transicion no permitida (por
 * ejemplo cerrar un periodo ya cerrado) se rechaza con
 * {@link com.dessti.crm.platform.error.TransicionInvalidaException} (409).
 *
 * <h2>Valor persistido</h2>
 * <p>En la base de datos se almacena la etiqueta ASCII en minusculas
 * ({@link #valorBd()}): {@code 'abierto'}, {@code 'cerrado'}, tal como exige el
 * CHECK de la migracion V72.</p>
 */
public enum EstadoPeriodo {

    /** El periodo admite polizas y reversos (estado por defecto). */
    ABIERTO("abierto"),

    /** El periodo esta bloqueado (candado contable); no admite polizas ni reversos. */
    CERRADO("cerrado");

    /**
     * Maquina de estados pura del periodo contable. Se construye una sola vez y es
     * inmutable. Ambos estados admiten una transicion (no hay estados finales).
     */
    private static final MaquinaEstados<EstadoPeriodo> MAQUINA =
            MaquinaEstados.<EstadoPeriodo>builder(EstadoPeriodo.class)
                    .permitir(ABIERTO, CERRADO)
                    .permitir(CERRADO, ABIERTO)
                    .construir();

    private final String valorBd;

    EstadoPeriodo(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code periodo_contable.estado}, en
     * minusculas ASCII, tal como la exige el CHECK de la migracion V72.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). Insensible a mayusculas; recorta espacios.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'cerrado'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoPeriodo desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El estado del Periodo_Contable no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoPeriodo estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de Periodo_Contable desconocido: " + valor);
    }

    /**
     * Funcion pura: indica si desde este estado se puede transitar a {@code destino}.
     *
     * @param destino estado destino pretendido; obligatorio.
     * @return {@code true} si la transicion es valida.
     */
    public boolean puedeTransicionarA(EstadoPeriodo destino) {
        return MAQUINA.puedeTransicionar(this, destino);
    }
}

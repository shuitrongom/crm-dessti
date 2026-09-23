package com.dessti.crm.tesoreria.domain;

import java.util.Locale;

/**
 * Estado de conciliacion de un {@link MovimientoBancario} (Req 43.3, 43.4). La
 * etiqueta persistida en la columna {@code movimiento_bancario.estado_conciliacion}
 * es ASCII en minusculas, tal como exige el CHECK de la migracion V35.
 *
 * <h2>Ciclo de vida (maquina de estados del dominio)</h2>
 * <ul>
 *   <li>{@link #PENDIENTE}: estado inicial al importar el movimiento (Req 43.2),
 *       aun sin emparejar.</li>
 *   <li>{@link #CONCILIADO}: el movimiento se emparejo con una Poliza_Contable o un
 *       Pago dentro de tolerancia (Req 43.3).</li>
 *   <li>{@link #EXCEPCION}: el movimiento no encontro coincidencia y queda para
 *       revision manual (Req 43.4).</li>
 * </ul>
 */
public enum EstadoConciliacionMovimiento {

    /** Movimiento importado aun sin emparejar (estado inicial, Req 43.2). */
    PENDIENTE("pendiente"),

    /** Movimiento emparejado con una Poliza_Contable o un Pago (Req 43.3). */
    CONCILIADO("conciliado"),

    /** Movimiento sin coincidencia; requiere revision manual (Req 43.4). */
    EXCEPCION("excepcion");

    private final String valorBd;

    EstadoConciliacionMovimiento(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code movimiento_bancario.estado_conciliacion}.
     *
     * @return la etiqueta de base de datos del estado.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el estado a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'excepcion'}).
     * @return el estado correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static EstadoConciliacionMovimiento desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    "El estado de conciliacion del Movimiento_Bancario no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (EstadoConciliacionMovimiento estado : values()) {
            if (estado.valorBd.equals(normalizado)) {
                return estado;
            }
        }
        throw new IllegalArgumentException(
                "Estado de conciliacion del Movimiento_Bancario desconocido: " + valor);
    }
}

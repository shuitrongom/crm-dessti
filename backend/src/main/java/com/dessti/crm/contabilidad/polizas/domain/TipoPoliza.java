package com.dessti.crm.contabilidad.polizas.domain;

import java.util.Locale;

/**
 * Tipo de una {@link PolizaContable} (Req 38.2). La etiqueta persistida en la
 * columna {@code poliza_contable.tipo} es ASCII en minusculas, tal como exige el
 * CHECK de la migracion V33.
 */
public enum TipoPoliza {

    /** Poliza de ingreso. */
    INGRESO("ingreso"),

    /** Poliza de egreso. */
    EGRESO("egreso"),

    /** Poliza de diario (movimientos que no son estrictamente de ingreso ni egreso). */
    DIARIO("diario");

    private final String valorBd;

    TipoPoliza(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code poliza_contable.tipo}.
     *
     * @return la etiqueta de base de datos del tipo.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'diario'}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static TipoPoliza desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de la Poliza_Contable no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoPoliza tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Poliza_Contable desconocido: " + valor);
    }
}

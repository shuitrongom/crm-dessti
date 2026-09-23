package com.dessti.crm.contabilidad.polizas.domain;

import java.util.Locale;

/**
 * Tipo contable de una {@link CuentaContable} (Req 38.1). La etiqueta persistida en
 * la columna {@code cuenta_contable.tipo} es ASCII en minusculas, tal como exige el
 * CHECK de la migracion V33.
 */
public enum TipoCuentaContable {

    /** Cuenta de activo. */
    ACTIVO("activo"),

    /** Cuenta de pasivo. */
    PASIVO("pasivo"),

    /** Cuenta de capital. */
    CAPITAL("capital"),

    /** Cuenta de ingreso. */
    INGRESO("ingreso"),

    /** Cuenta de gasto. */
    GASTO("gasto");

    private final String valorBd;

    TipoCuentaContable(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code cuenta_contable.tipo}.
     *
     * @return la etiqueta de base de datos del tipo.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'activo'}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static TipoCuentaContable desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de la Cuenta_Contable no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoCuentaContable tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Cuenta_Contable desconocido: " + valor);
    }
}

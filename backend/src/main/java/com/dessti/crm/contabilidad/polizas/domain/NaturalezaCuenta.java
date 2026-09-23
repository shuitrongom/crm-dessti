package com.dessti.crm.contabilidad.polizas.domain;

import java.util.Locale;

/**
 * Naturaleza del saldo de una {@link CuentaContable} (Req 38.1): deudora o
 * acreedora. La etiqueta persistida en la columna {@code cuenta_contable.naturaleza}
 * es ASCII en minusculas, tal como exige el CHECK de la migracion V33.
 */
public enum NaturalezaCuenta {

    /** Naturaleza deudora (el saldo aumenta con cargos). */
    DEUDORA("deudora"),

    /** Naturaleza acreedora (el saldo aumenta con abonos). */
    ACREEDORA("acreedora");

    private final String valorBd;

    NaturalezaCuenta(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en la columna {@code cuenta_contable.naturaleza}.
     *
     * @return la etiqueta de base de datos de la naturaleza.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye la naturaleza a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'deudora'}).
     * @return la naturaleza correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static NaturalezaCuenta desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("La naturaleza de la Cuenta_Contable no puede ser nula");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (NaturalezaCuenta naturaleza : values()) {
            if (naturaleza.valorBd.equals(normalizado)) {
                return naturaleza;
            }
        }
        throw new IllegalArgumentException("Naturaleza de Cuenta_Contable desconocida: " + valor);
    }
}

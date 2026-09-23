package com.dessti.crm.rhnomina.empleado.domain;

/**
 * Periodicidad de pago de un {@link ContratoLaboral} (Req 40.1): {@code semanal},
 * {@code quincenal} y {@code mensual}. Cada constante conoce su etiqueta ASCII
 * persistida en la columna {@code contrato_laboral.periodicidad} (VARCHAR con
 * CHECK {@code IN ('semanal','quincenal','mensual')} de la migracion V32).
 */
public enum Periodicidad {

    /** Pago semanal (Req 40.1). */
    SEMANAL("semanal"),

    /** Pago quincenal (Req 40.1). */
    QUINCENAL("quincenal"),

    /** Pago mensual (Req 40.1). */
    MENSUAL("mensual");

    private final String valorBd;

    Periodicidad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V32).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "quincenal"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve la periodicidad a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta persistida.
     * @return la periodicidad correspondiente.
     * @throws IllegalArgumentException si la etiqueta no corresponde a ninguna.
     */
    public static Periodicidad desdeValorBd(String valor) {
        for (Periodicidad periodicidad : values()) {
            if (periodicidad.valorBd.equals(valor)) {
                return periodicidad;
            }
        }
        throw new IllegalArgumentException("Periodicidad de Contrato_Laboral desconocida: " + valor);
    }
}

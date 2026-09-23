package com.dessti.crm.rhnomina.empleado.domain;

/**
 * Tipos de {@link ContratoLaboral} admitidos por el modulo base de RH (Req 40.1):
 * {@code indeterminado}, {@code determinado}, {@code obra} y {@code capacitacion}.
 * Cada constante conoce su etiqueta ASCII persistida en la columna
 * {@code contrato_laboral.tipo} (VARCHAR con CHECK {@code IN ('indeterminado',
 * 'determinado','obra','capacitacion')} de la migracion V32), coherente con la
 * convencion de etiquetas del resto de modulos.
 */
public enum TipoContrato {

    /** Contrato por tiempo indeterminado (Req 40.1). */
    INDETERMINADO("indeterminado"),

    /** Contrato por tiempo determinado (Req 40.1). */
    DETERMINADO("determinado"),

    /** Contrato por obra determinada (Req 40.1). */
    OBRA("obra"),

    /** Contrato de capacitacion inicial (Req 40.1). */
    CAPACITACION("capacitacion");

    private final String valorBd;

    TipoContrato(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V32).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "indeterminado"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve el tipo a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta persistida.
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si la etiqueta no corresponde a ningun tipo.
     */
    public static TipoContrato desdeValorBd(String valor) {
        for (TipoContrato tipo : values()) {
            if (tipo.valorBd.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Contrato_Laboral desconocido: " + valor);
    }
}

package com.dessti.crm.rhnomina.empleado.domain;

/**
 * Tipos de {@link Incidencia} de un Empleado en un Periodo_Nomina (Req 40.3):
 * {@code asistencia}, {@code falta}, {@code permiso}, {@code incapacidad} y
 * {@code tiempo_extra}. Cada constante conoce su etiqueta ASCII persistida en la
 * columna {@code incidencia.tipo} (VARCHAR con CHECK {@code IN ('asistencia',
 * 'falta','permiso','incapacidad','tiempo_extra')} de la migracion V32).
 */
public enum TipoIncidencia {

    /** Asistencia registrada (Req 40.3). */
    ASISTENCIA("asistencia"),

    /** Falta del Empleado (Req 40.3). */
    FALTA("falta"),

    /** Permiso concedido al Empleado (Req 40.3). */
    PERMISO("permiso"),

    /** Incapacidad (por ejemplo del IMSS) (Req 40.3). */
    INCAPACIDAD("incapacidad"),

    /** Tiempo extra trabajado (Req 40.3). */
    TIEMPO_EXTRA("tiempo_extra");

    private final String valorBd;

    TipoIncidencia(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V32).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "tiempo_extra"}).
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
    public static TipoIncidencia desdeValorBd(String valor) {
        for (TipoIncidencia tipo : values()) {
            if (tipo.valorBd.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Incidencia desconocido: " + valor);
    }
}

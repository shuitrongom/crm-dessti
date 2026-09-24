package com.dessti.crm.contabilidad.electronica.domain.xml;

/**
 * Datos de encabezado comunes a los tres XML de Contabilidad Electronica del SAT
 * (Anexo 24): version del esquema, RFC del contribuyente y periodo (mes/anio).
 *
 * @param version version del esquema del SAT (p. ej. {@code 1.3}).
 * @param rfc     RFC del contribuyente emisor (Datos_Fiscales_Empresa).
 * @param anio    anio del periodo (p. ej. {@code 2026}).
 * @param mes     mes del periodo, formateado a dos digitos (p. ej. {@code 09}).
 */
public record EncabezadoSat(String version, String rfc, int anio, String mes) {

    /**
     * Construye el encabezado normalizando el mes a dos digitos.
     *
     * @param version version del esquema.
     * @param rfc     RFC del contribuyente.
     * @param anio    anio del periodo.
     * @param mes     mes del periodo (1-12).
     * @return el encabezado con el mes formateado a dos digitos.
     */
    public static EncabezadoSat de(String version, String rfc, int anio, int mes) {
        return new EncabezadoSat(version, rfc, anio, String.format("%02d", mes));
    }
}

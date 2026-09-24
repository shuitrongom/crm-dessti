package com.dessti.crm.contabilidad.electronica.application;

import com.dessti.crm.contabilidad.electronica.domain.CodigoAgrupadorSat;

/**
 * DTO de salida de un codigo agrupador del SAT (Anexo 24) para el autocompletar y
 * la consulta del catalogo oficial.
 *
 * @param codigo     clave del agrupador (p. ej. {@code 101.01}).
 * @param nombre     nombre descriptivo (p. ej. {@code Caja y efectivo}).
 * @param nivel      1 = cuenta de mayor, 2 = subcuenta de primer nivel.
 * @param naturaleza {@code D} (deudora) o {@code A} (acreedora).
 */
public record CodigoAgrupadorSatDto(
        String codigo,
        String nombre,
        short nivel,
        String naturaleza) {

    /**
     * Proyecta una entidad del catalogo a su DTO de salida.
     *
     * @param entidad entidad del catalogo agrupador.
     * @return el DTO correspondiente.
     */
    public static CodigoAgrupadorSatDto de(CodigoAgrupadorSat entidad) {
        return new CodigoAgrupadorSatDto(
                entidad.getCodigo(),
                entidad.getNombre(),
                entidad.getNivel(),
                entidad.getNaturaleza());
    }
}

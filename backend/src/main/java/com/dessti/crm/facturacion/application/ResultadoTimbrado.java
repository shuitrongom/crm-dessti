package com.dessti.crm.facturacion.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado inmutable del Timbrado de un CFDI devuelto por el {@link PacPort}
 * (Req 35.1, 35.2).
 *
 * <p>En caso de <strong>exito</strong> ({@link #exito()} {@code == true}) trae el
 * {@link #folioFiscal() Folio_Fiscal} (UUID del SAT), el {@link #selloSat() sello}
 * y la {@link #fechaTimbrado() fecha de timbrado}; {@link #mensajeError()} es
 * {@code null}. En caso de <strong>rechazo</strong> ({@code exito == false}), el
 * folio, el sello y la fecha son {@code null} y {@link #mensajeError()} contiene
 * el motivo devuelto por el PAC (Req 35.2).</p>
 *
 * @param exito         {@code true} si el PAC timbro con exito; {@code false} si lo rechazo.
 * @param folioFiscal   Folio_Fiscal (UUID del SAT) en exito; {@code null} en rechazo (Req 35.1, 35.2).
 * @param selloSat      sello digital del SAT en exito; {@code null} en rechazo.
 * @param fechaTimbrado fecha/hora del Timbrado (UTC) en exito; {@code null} en rechazo.
 * @param mensajeError  motivo del rechazo del PAC en rechazo; {@code null} en exito (Req 35.2).
 */
public record ResultadoTimbrado(
        boolean exito,
        UUID folioFiscal,
        String selloSat,
        Instant fechaTimbrado,
        String mensajeError) {

    /**
     * Construye un resultado de Timbrado <strong>exitoso</strong> (Req 35.1).
     *
     * @param folioFiscal   Folio_Fiscal (UUID del SAT); obligatorio.
     * @param selloSat      sello digital del SAT; obligatorio.
     * @param fechaTimbrado fecha/hora del Timbrado (UTC); obligatoria.
     * @return el resultado exitoso, sin mensaje de error.
     */
    public static ResultadoTimbrado exitoso(UUID folioFiscal, String selloSat, Instant fechaTimbrado) {
        return new ResultadoTimbrado(true, folioFiscal, selloSat, fechaTimbrado, null);
    }

    /**
     * Construye un resultado de Timbrado <strong>rechazado</strong> por el PAC
     * (Req 35.2): sin folio, sin sello, sin fecha, con el motivo del rechazo.
     *
     * @param mensajeError motivo del rechazo devuelto por el PAC; obligatorio.
     * @return el resultado de rechazo.
     */
    public static ResultadoTimbrado rechazado(String mensajeError) {
        return new ResultadoTimbrado(false, null, null, null, mensajeError);
    }
}

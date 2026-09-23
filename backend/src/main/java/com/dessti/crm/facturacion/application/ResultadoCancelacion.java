package com.dessti.crm.facturacion.application;

/**
 * Resultado inmutable de la cancelacion de un CFDI devuelto por el
 * {@link PacPort} (Req 35.4, 35.5).
 *
 * <p>En <strong>exito</strong> ({@link #exito()} {@code == true}) trae el
 * {@link #acuse() acuse} de cancelacion del PAC/SAT y {@link #mensajeError()} es
 * {@code null}. En <strong>rechazo</strong> ({@code exito == false}), el acuse es
 * {@code null} y {@link #mensajeError()} contiene el motivo devuelto por el
 * PAC.</p>
 *
 * @param exito        {@code true} si el PAC acepto la solicitud de cancelacion.
 * @param acuse        acuse de cancelacion del PAC/SAT en exito; {@code null} en rechazo.
 * @param mensajeError motivo del rechazo del PAC en rechazo; {@code null} en exito.
 */
public record ResultadoCancelacion(boolean exito, String acuse, String mensajeError) {

    /**
     * Construye un resultado de cancelacion <strong>aceptado</strong> por el PAC
     * (Req 35.4, 35.5).
     *
     * @param acuse acuse de cancelacion devuelto por el PAC/SAT; obligatorio.
     * @return el resultado aceptado, sin mensaje de error.
     */
    public static ResultadoCancelacion aceptada(String acuse) {
        return new ResultadoCancelacion(true, acuse, null);
    }

    /**
     * Construye un resultado de cancelacion <strong>rechazado</strong> por el PAC.
     *
     * @param mensajeError motivo del rechazo devuelto por el PAC; obligatorio.
     * @return el resultado de rechazo.
     */
    public static ResultadoCancelacion rechazada(String mensajeError) {
        return new ResultadoCancelacion(false, null, mensajeError);
    }
}

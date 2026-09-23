package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.EstadoEntrega;

/**
 * Resultado inmutable del envio de un Mensaje_Social devuelto por el
 * {@link MensajeriaSocialPort} (Req 64.11, 64.13). Sigue el patron de
 * {@code ResultadoTimbrado}.
 *
 * <p>En caso de <strong>exito</strong> ({@link #exito()} {@code == true}) trae el
 * {@link #externoId() id externo} del proveedor y el {@link #estadoEntrega() estado
 * de entrega} inicial (por ejemplo {@link EstadoEntrega#ENVIADO}); {@link #mensajeError()}
 * es {@code null}. En caso de <strong>fallo</strong> ({@code exito == false}), el
 * id externo es {@code null}, el estado de entrega es {@link EstadoEntrega#FALLIDO}
 * y {@link #mensajeError()} contiene el motivo (candidato a reintento, Req 64.13).</p>
 *
 * @param exito         {@code true} si el proveedor acepto el envio.
 * @param externoId     id del mensaje en el proveedor en exito; {@code null} en fallo.
 * @param estadoEntrega estado de entrega mapeado desde el proveedor (Req 64.11).
 * @param mensajeError  motivo del fallo; {@code null} en exito.
 */
public record ResultadoEnvioSocial(
        boolean exito,
        String externoId,
        EstadoEntrega estadoEntrega,
        String mensajeError) {

    /**
     * Construye un resultado de envio <strong>exitoso</strong> (Req 64.11).
     *
     * @param externoId     id del mensaje en el proveedor; obligatorio.
     * @param estadoEntrega estado de entrega inicial (por ejemplo {@code enviado}).
     * @return el resultado exitoso, sin mensaje de error.
     */
    public static ResultadoEnvioSocial exitoso(String externoId, EstadoEntrega estadoEntrega) {
        return new ResultadoEnvioSocial(true, externoId, estadoEntrega, null);
    }

    /**
     * Construye un resultado de envio <strong>fallido</strong> (Req 64.13): sin id
     * externo, con estado {@link EstadoEntrega#FALLIDO} y el motivo del fallo.
     *
     * @param mensajeError motivo del fallo devuelto por el proveedor; obligatorio.
     * @return el resultado de fallo.
     */
    public static ResultadoEnvioSocial fallido(String mensajeError) {
        return new ResultadoEnvioSocial(false, null, EstadoEntrega.FALLIDO, mensajeError);
    }
}

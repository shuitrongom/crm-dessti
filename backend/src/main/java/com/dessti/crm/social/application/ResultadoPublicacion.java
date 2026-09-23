package com.dessti.crm.social.application;

/**
 * Resultado inmutable de una publicacion devuelto por el
 * {@link PublicacionSocialPort} (Req 65.5, 65.6). Sigue el patron de
 * {@link ResultadoEnvioSocial}.
 *
 * <p>En caso de <strong>exito</strong> ({@link #exito()} {@code == true}) trae el
 * {@link #externoId() id externo} del proveedor y {@link #mensajeError()} es
 * {@code null}. En caso de <strong>fallo</strong> ({@code exito == false}), el id
 * externo es {@code null} y {@link #mensajeError()} contiene el motivo (candidato a
 * reintento, Req 65.6).</p>
 *
 * @param exito        {@code true} si el proveedor confirmo la publicacion.
 * @param externoId    id de la publicacion en el proveedor en exito; {@code null} en fallo.
 * @param mensajeError motivo del fallo; {@code null} en exito.
 */
public record ResultadoPublicacion(
        boolean exito,
        String externoId,
        String mensajeError) {

    /**
     * Construye un resultado de publicacion <strong>exitoso</strong> (Req 65.5).
     *
     * @param externoId id de la publicacion en el proveedor; obligatorio.
     * @return el resultado exitoso, sin mensaje de error.
     */
    public static ResultadoPublicacion exitoso(String externoId) {
        return new ResultadoPublicacion(true, externoId, null);
    }

    /**
     * Construye un resultado de publicacion <strong>fallido</strong> (Req 65.6): sin
     * id externo y con el motivo del fallo.
     *
     * @param mensajeError motivo del fallo devuelto por el proveedor; obligatorio.
     * @return el resultado de fallo.
     */
    public static ResultadoPublicacion fallido(String mensajeError) {
        return new ResultadoPublicacion(false, null, mensajeError);
    }
}

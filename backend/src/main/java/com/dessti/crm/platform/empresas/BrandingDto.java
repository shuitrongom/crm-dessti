package com.dessti.crm.platform.empresas;

/**
 * DTO de salida de la personalizacion de marca (branding) de una Empresa
 * (Req 26.2), distinto de la entidad de persistencia {@link Empresa}.
 *
 * <p>Expone unicamente los datos de branding que la interfaz aplica para la
 * Empresa del Usuario: el nombre visible, el logotipo (URL o {@code data URI})
 * y el color primario de marca ({@code #RRGGBB}). Los tres pueden ser
 * {@code null} cuando la Empresa aun no ha personalizado su marca.</p>
 *
 * @param nombreVisible nombre visible de la Empresa; {@code null} si no se
 *                      ha personalizado.
 * @param logo          logotipo como URL o {@code data URI}; {@code null} si no
 *                      se ha personalizado.
 * @param colorPrimario color primario de marca en formato {@code #RRGGBB};
 *                      {@code null} si no se ha personalizado.
 */
public record BrandingDto(String nombreVisible, String logo, String colorPrimario) {

    /**
     * Proyecta el branding de una entidad {@link Empresa} a su DTO de salida.
     *
     * @param empresa entidad de la que se toma el branding.
     * @return el DTO con el nombre visible, el logotipo y el color primario
     *         actuales.
     */
    public static BrandingDto de(Empresa empresa) {
        return new BrandingDto(
                empresa.getBrandingNombreVisible(),
                empresa.getBrandingLogo(),
                empresa.getBrandingColorPrimario());
    }
}

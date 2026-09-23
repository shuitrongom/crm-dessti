package com.dessti.crm.platform.empresas;

/**
 * Comando de aplicacion para actualizar la personalizacion de marca (branding)
 * de la Empresa del Usuario autenticado (Req 26.1).
 *
 * <p>Ambos campos son <strong>opcionales</strong> (personalizacion): un valor
 * {@code null} o en blanco limpia el dato correspondiente. El {@code tenant_id}
 * y el actor <em>no</em> forman parte del comando: se derivan siempre del
 * contexto autenticado ({@code TenantContext}/{@code AutenticacionActual}),
 * nunca del cuerpo de la peticion (Req 23.4).</p>
 *
 * @param nombreVisible nombre visible a establecer; {@code null}/blanco lo limpia.
 * @param logo          logotipo (URL o {@code data URI}) a establecer;
 *                      {@code null}/blanco lo limpia.
 * @param colorPrimario color primario de marca ({@code #RRGGBB}) a establecer;
 *                      {@code null}/blanco lo limpia.
 */
public record ActualizarBrandingCommand(String nombreVisible, String logo, String colorPrimario) {
}

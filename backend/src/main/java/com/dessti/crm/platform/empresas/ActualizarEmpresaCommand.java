package com.dessti.crm.platform.empresas;

/**
 * Comando de aplicacion para que el {@code super_admin} EDITE los datos de
 * plataforma de una Empresa existente (CHANGE 1), desacoplado de las entidades
 * JPA.
 *
 * <p>Incluye la identidad editable ({@code nombre}), el identificador fiscal
 * ({@code rfc}, que se normaliza/valida con el mismo {@link RfcValidador} del
 * alta) y la ficha descriptiva/de contacto completa ({@link DatosDescriptivosEmpresa}:
 * nombre comercial, correo, telefono, sitio web, direccion, notas y logo).</p>
 *
 * <p><strong>Fuera de alcance por diseno:</strong> este comando NO reasigna el
 * {@code estado} (activar/suspender/cancelar tienen sus propios endpoints), ni el
 * {@code giro} (flujo dedicado {@code cambiarGiro}, Req 3, que valida datos del
 * vertical), ni el Plan/Suscripcion (flujos de monetizacion). La edicion se
 * limita a los datos descriptivos/fiscales de la Empresa.</p>
 *
 * @param nombre nombre de la Empresa; obligatorio.
 * @param rfc    identificador fiscal (RFC); obligatorio (se normaliza y valida).
 * @param datos  ficha descriptiva/de contacto a aplicar (reemplazo completo); el
 *               correo de contacto es obligatorio a nivel web.
 */
public record ActualizarEmpresaCommand(
        String nombre,
        String rfc,
        DatosDescriptivosEmpresa datos) {
}

package com.dessti.crm.platform.empresas;

/**
 * Comando de aplicacion para que el {@code admin_empresa} EDITE el perfil de
 * CONTACTO de SU PROPIA Empresa (CHANGE 2), desacoplado de las entidades JPA.
 *
 * <p>Solo transporta los campos de contacto/perfil que un {@code admin_empresa}
 * puede modificar de su Empresa: nombre, correo de contacto, telefono, direccion
 * desglosada y logotipo. <strong>Deliberadamente NO incluye</strong> el
 * identificador fiscal ({@code rfc}), el {@code giro}, el Plan ni el
 * {@code estado}: al no formar parte del comando, no hay forma de que el
 * {@code admin_empresa} los altere (son atributos de plataforma del
 * {@code super_admin}).</p>
 *
 * @param nombre          nombre de la Empresa; obligatorio.
 * @param emailContacto   correo de contacto; obligatorio (se normaliza a minusculas).
 * @param telefono        telefono de contacto; opcional.
 * @param direccionCalle  calle y numero; opcional.
 * @param direccionCiudad ciudad; opcional.
 * @param direccionEstado estado/provincia; opcional.
 * @param direccionCp     codigo postal; opcional.
 * @param direccionPais   pais; opcional.
 * @param logo            logotipo (URL o data URI); opcional.
 */
public record ActualizarMiEmpresaCommand(
        String nombre,
        String emailContacto,
        String telefono,
        String direccionCalle,
        String direccionCiudad,
        String direccionEstado,
        String direccionCp,
        String direccionPais,
        String logo) {
}

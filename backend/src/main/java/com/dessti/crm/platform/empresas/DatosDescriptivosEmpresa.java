package com.dessti.crm.platform.empresas;

/**
 * Portador inmutable de los datos DESCRIPTIVOS y de CONTACTO opcionales de una
 * Empresa (Tenant) a nivel de plataforma (Req 24).
 *
 * <p>Agrupa la ficha descriptiva de la Empresa (nombre comercial, correo,
 * telefono, sitio web, direccion desglosada y notas libres) mas un
 * {@code logo} opcional de branding (URL o {@code data URI}) para poder fijarlo
 * en el mismo alta. Todos los campos son <strong>opcionales</strong>: un valor
 * {@code null} o en blanco se interpreta como "sin dato".</p>
 *
 * <p>La normalizacion (recorte de espacios, {@code null} para blancos, correo a
 * minusculas) y la validacion de longitudes maximas contra las columnas de la
 * migracion V54 las aplica la entidad {@link Empresa} al asignar estos datos; el
 * record solo transporta los valores en crudo entre las capas.</p>
 *
 * @param nombreComercial nombre comercial (marca) de la Empresa (max. 200).
 * @param emailContacto   correo de contacto (max. 255); se normaliza a minusculas.
 * @param telefono        telefono de contacto (max. 40).
 * @param sitioWeb        sitio web (max. 255).
 * @param direccionCalle  calle y numero (max. 255).
 * @param direccionCiudad ciudad (max. 120).
 * @param direccionEstado estado/provincia (max. 120).
 * @param direccionCp     codigo postal (max. 12).
 * @param direccionPais   pais (max. 80).
 * @param notas           notas libres del super_admin (texto sin cota de columna).
 * @param logo            logotipo de branding como URL o {@code data URI}
 *                        (max. {@link Empresa#LONGITUD_MAXIMA_LOGO}); opcional.
 */
public record DatosDescriptivosEmpresa(
        String nombreComercial,
        String emailContacto,
        String telefono,
        String sitioWeb,
        String direccionCalle,
        String direccionCiudad,
        String direccionEstado,
        String direccionCp,
        String direccionPais,
        String notas,
        String logo) {
}

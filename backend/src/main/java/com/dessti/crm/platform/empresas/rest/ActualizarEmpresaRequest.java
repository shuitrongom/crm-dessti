package com.dessti.crm.platform.empresas.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para que el {@code super_admin} EDITE los datos de
 * plataforma de una Empresa existente ({@code PUT /empresas/{id}}, CHANGE 1).
 *
 * <p>Reutiliza EXACTAMENTE las mismas validaciones y cotas del alta
 * ({@link CrearEmpresaRequest}) para los campos compartidos: nombre
 * ({@link NotBlank}, max 200), rfc ({@link NotBlank}, 12..13), correo de contacto
 * <strong>obligatorio</strong> ({@link NotBlank} {@link Email} max 255) y el resto
 * de la ficha descriptiva/de contacto (nombre comercial, telefono, sitio web,
 * direccion desglosada, notas y logo), todos opcionales y acotados a las columnas
 * de la migracion V54. La normalizacion (recorte, correo a minusculas) y la
 * validacion estructural del RFC las aplica el dominio/servicio reutilizando el
 * mismo {@code RfcValidador} del alta.</p>
 *
 * <p><strong>Fuera de alcance (por diseno):</strong> este cuerpo NO acepta
 * {@code giroId}, {@code planId} ni {@code estado}. La reasignacion de Giro tiene
 * su propio flujo controlado ({@code cambiarGiro}, Req 3, que valida datos del
 * vertical); el Plan/Suscripcion se gestionan por los flujos de monetizacion
 * ({@code PUT /empresas/{id}/modulos}, moneda de facturacion, etc.); y el estado
 * se cambia por {@code POST /empresas/{id}/activar|suspender}. Mantener esa
 * separacion evita duplicar reglas divergentes.</p>
 *
 * @param nombre           nombre de la Empresa; obligatorio.
 * @param rfc              identificador fiscal (RFC); obligatorio (12..13).
 * @param nombreComercial  nombre comercial (marca); opcional (max 200).
 * @param emailContacto    correo de contacto; obligatorio (formato de correo, 1..255).
 * @param telefono         telefono de contacto; opcional (max 40).
 * @param sitioWeb         sitio web; opcional (max 255).
 * @param direccionCalle   calle y numero; opcional (max 255).
 * @param direccionCiudad  ciudad; opcional (max 120).
 * @param direccionEstado  estado/provincia; opcional (max 120).
 * @param direccionCp      codigo postal; opcional (max 12).
 * @param direccionPais    pais; opcional (max 80).
 * @param notas            notas libres del super_admin; opcional (max 5000).
 * @param logo             logotipo (URL o data URI); opcional (max 1 MiB).
 */
public record ActualizarEmpresaRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(min = 12, max = 13) String rfc,
        @Size(max = 200) String nombreComercial,
        @NotBlank @Email @Size(max = 255) String emailContacto,
        @Size(max = 40) String telefono,
        @Size(max = 255) String sitioWeb,
        @Size(max = 255) String direccionCalle,
        @Size(max = 120) String direccionCiudad,
        @Size(max = 120) String direccionEstado,
        @Size(max = 12) String direccionCp,
        @Size(max = 80) String direccionPais,
        @Size(max = 5000) String notas,
        @Size(max = 1_048_576) String logo) {
}

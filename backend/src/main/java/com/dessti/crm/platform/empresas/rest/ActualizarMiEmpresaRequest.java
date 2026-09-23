package com.dessti.crm.platform.empresas.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para que el {@code admin_empresa} EDITE el perfil de
 * CONTACTO de SU PROPIA Empresa ({@code PUT /empresas/mi-empresa}, CHANGE 2).
 *
 * <p>Solo admite los campos de contacto/perfil que un {@code admin_empresa} puede
 * modificar de su Empresa, reutilizando las MISMAS validaciones y cotas del alta
 * ({@link CrearEmpresaRequest}): nombre ({@link NotBlank}, max 200), correo de
 * contacto <strong>obligatorio</strong> ({@link NotBlank} {@link Email}, max 255),
 * telefono, direccion desglosada y logo (todos opcionales y acotados a V54).</p>
 *
 * <p><strong>No editable por esta via (por diseno):</strong> el cuerpo NO incluye
 * {@code rfc}, {@code giroId}, {@code planId}, {@code estado} ni notas de
 * plataforma. Al no formar parte del contrato, un {@code admin_empresa} no puede
 * enviarlos ni alterarlos; siguen siendo competencia exclusiva del
 * {@code super_admin}.</p>
 *
 * @param nombre          nombre de la Empresa; obligatorio.
 * @param emailContacto   correo de contacto; obligatorio (formato de correo, 1..255).
 * @param telefono        telefono de contacto; opcional (max 40).
 * @param direccionCalle  calle y numero; opcional (max 255).
 * @param direccionCiudad ciudad; opcional (max 120).
 * @param direccionEstado estado/provincia; opcional (max 120).
 * @param direccionCp     codigo postal; opcional (max 12).
 * @param direccionPais   pais; opcional (max 80).
 * @param logo            logotipo (URL o data URI); opcional (max 1 MiB).
 */
public record ActualizarMiEmpresaRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Email @Size(max = 255) String emailContacto,
        @Size(max = 40) String telefono,
        @Size(max = 255) String direccionCalle,
        @Size(max = 120) String direccionCiudad,
        @Size(max = 120) String direccionEstado,
        @Size(max = 12) String direccionCp,
        @Size(max = 80) String direccionPais,
        @Size(max = 1_048_576) String logo) {
}

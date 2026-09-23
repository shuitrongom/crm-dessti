package com.dessti.crm.social.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Cuenta_Canal_Social (Req 64.1, 11). DTO
 * de entrada del contrato REST, distinto de la entidad JPA.
 *
 * <p>El campo {@code credencialesRef} es una <strong>referencia</strong> al almacen
 * de secretos (por ejemplo, la clave logica del token en el vault), NUNCA el valor
 * del secreto (Req 11).</p>
 *
 * @param canal                 etiqueta del Canal_Social (whatsapp/messenger/instagram); obligatorio.
 * @param identificadorExterno  identificador en el canal (numero WA / page id / ig id); obligatorio.
 * @param nombre                nombre descriptivo; obligatorio.
 * @param credencialesRef       referencia al secreto (NUNCA el valor, Req 11); obligatorio.
 */
public record CrearCuentaCanalSocialRequest(
        @NotBlank @Size(max = 12) String canal,
        @NotBlank @Size(max = 120) String identificadorExterno,
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 200) String credencialesRef) {
}

package com.dessti.crm.comercial.cliente.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar un Cliente (Req 5.4).
 *
 * <p>DTO de entrada del contrato REST, distinto de la entidad y del comando
 * {@link com.dessti.crm.comercial.cliente.application.ActualizarClienteCommand}
 * (Req 12.2). El {@code tenant_id} se deriva del contexto (Req 23.4).</p>
 *
 * <p>Como en el alta, la validacion de campo se limita a presencia/longitud y a
 * patrones laxos para los opcionales (400); el formato del RFC/email lo valida
 * el dominio (422). Los patrones opcionales toleran el valor ausente
 * ({@code null}).</p>
 *
 * @param nombre            nuevo nombre; obligatorio (1..200).
 * @param rfc               nuevo identificador fiscal; obligatorio (12..13).
 * @param email             nuevo correo electronico; opcional.
 * @param telefono          nuevo telefono; opcional (10..15 digitos).
 * @param nombreComercial   nombre comercial (marca); opcional (max. 200).
 * @param tipoPersona       tipo de persona ('fisica' | 'moral'); opcional.
 * @param telefonoAdicional telefono secundario; opcional (10..15 digitos).
 * @param direccionCalle    calle y numero; opcional (max. 200).
 * @param direccionCiudad   ciudad; opcional (max. 120).
 * @param direccionEstado   estado/provincia; opcional (max. 120).
 * @param direccionCp       codigo postal; opcional (max. 10).
 * @param direccionPais     pais; opcional (max. 80).
 * @param notas             notas libres; opcional (max. 1000).
 */
public record ActualizarClienteRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(min = 12, max = 13) String rfc,
        @Size(max = 254) String email,
        @Size(max = 20) String telefono,
        @Size(max = 200) String nombreComercial,
        @Pattern(regexp = "fisica|moral") String tipoPersona,
        @Pattern(regexp = "\\d{10,15}") String telefonoAdicional,
        @Size(max = 200) String direccionCalle,
        @Size(max = 120) String direccionCiudad,
        @Size(max = 120) String direccionEstado,
        @Size(max = 10) String direccionCp,
        @Size(max = 80) String direccionPais,
        @Size(max = 1000) String notas) {
}

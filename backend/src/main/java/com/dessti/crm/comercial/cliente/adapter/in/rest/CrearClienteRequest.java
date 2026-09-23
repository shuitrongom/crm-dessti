package com.dessti.crm.comercial.cliente.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Cliente (Req 5.1).
 *
 * <p>DTO de entrada del contrato REST, <strong>distinto</strong> de la entidad
 * de persistencia y del comando de aplicacion
 * {@link com.dessti.crm.comercial.cliente.application.CrearClienteCommand}
 * (Req 12.2). El controlador lo traduce al comando antes de invocar el servicio.</p>
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id} NO se
 * acepta en la peticion; se deriva del contexto autenticado.</p>
 *
 * <p><strong>Alcance de la validacion de campo (Req 5.2, 8):</strong> aqui solo
 * se comprueba la <em>presencia</em> y unos limites de longitud/formato amplios
 * (Bean Validation -&gt; 400). El <em>formato</em> del RFC y del email lo valida
 * el dominio en la capa de aplicacion (que responde 422), por lo que no se
 * duplica aqui una expresion regular estricta que alteraria el codigo de error
 * esperado. Para los campos <em>opcionales</em> nuevos si se aplican patrones
 * laxos que rechazan de forma temprana un formato claramente invalido. Todos los
 * patrones toleran el valor ausente ({@code null}); la cadena vacia se rechaza si
 * choca con el patron, por lo que el frontend debe enviar {@code null} para
 * "sin dato".</p>
 *
 * @param nombre            razon social o nombre; obligatorio (1..200).
 * @param rfc               identificador fiscal; obligatorio (12..13).
 * @param email             correo electronico; opcional.
 * @param telefono          telefono; opcional (10..15 digitos).
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
public record CrearClienteRequest(
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

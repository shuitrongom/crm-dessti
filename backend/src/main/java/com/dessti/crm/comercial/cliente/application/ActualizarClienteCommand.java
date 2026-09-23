package com.dessti.crm.comercial.cliente.application;

/**
 * Comando de actualizacion de un {@link com.dessti.crm.comercial.cliente.domain.Cliente}
 * (Req 5.4). Distinto de la entidad; el {@code tenant_id} se deriva del contexto
 * (Req 23.4) y no viaja en el comando.
 *
 * @param nombre            nuevo nombre; obligatorio (1..200).
 * @param rfc               nuevo identificador fiscal; obligatorio (12..13, formato valido).
 * @param email             nuevo correo electronico; opcional (valido si se proporciona).
 * @param telefono          nuevo telefono; opcional (10..15 digitos si se proporciona).
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
public record ActualizarClienteCommand(
        String nombre,
        String rfc,
        String email,
        String telefono,
        String nombreComercial,
        String tipoPersona,
        String telefonoAdicional,
        String direccionCalle,
        String direccionCiudad,
        String direccionEstado,
        String direccionCp,
        String direccionPais,
        String notas) {
}

package com.dessti.crm.compras.proveedor.application;

/**
 * Comando de actualizacion de un {@link com.dessti.crm.compras.proveedor.domain.Proveedor}
 * (Req 29.4). Distinto de la entidad; el {@code tenant_id} se deriva del contexto
 * (Req 23.4) y no viaja en el comando.
 *
 * @param nombre           nuevo nombre; obligatorio (1..200).
 * @param rfc              nuevo identificador fiscal; obligatorio (12..13, formato valido).
 * @param email            nuevo correo electronico; opcional (valido si se proporciona).
 * @param telefono         nuevo telefono; opcional (10..15 digitos si se proporciona).
 * @param personaContacto  nombre de la persona de contacto; opcional (Req 29, V89).
 * @param regimenFiscal    clave del regimen fiscal (SAT); opcional (Req 29, V89).
 * @param diasCredito      dias de credito; opcional, {@code null} = de contado (Req 29, V89).
 * @param domicilioCalle   calle y numero del domicilio fiscal; opcional (Req 29, V89).
 * @param domicilioCiudad  ciudad/municipio; opcional (Req 29, V89).
 * @param domicilioEstado  estado/entidad; opcional (Req 29, V89).
 * @param codigoPostal     codigo postal (5 digitos); opcional (Req 29, V89).
 */
public record ActualizarProveedorCommand(
        String nombre,
        String rfc,
        String email,
        String telefono,
        String personaContacto,
        String regimenFiscal,
        Integer diasCredito,
        String domicilioCalle,
        String domicilioCiudad,
        String domicilioEstado,
        String codigoPostal) {
}

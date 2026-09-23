package com.dessti.crm.compras.proveedor.application;

/**
 * Comando de actualizacion de un {@link com.dessti.crm.compras.proveedor.domain.Proveedor}
 * (Req 29.4). Distinto de la entidad; el {@code tenant_id} se deriva del contexto
 * (Req 23.4) y no viaja en el comando.
 *
 * @param nombre   nuevo nombre; obligatorio (1..200).
 * @param rfc      nuevo identificador fiscal; obligatorio (12..13, formato valido).
 * @param email    nuevo correo electronico; opcional (valido si se proporciona).
 * @param telefono nuevo telefono; opcional (10..15 digitos si se proporciona).
 */
public record ActualizarProveedorCommand(
        String nombre,
        String rfc,
        String email,
        String telefono) {
}

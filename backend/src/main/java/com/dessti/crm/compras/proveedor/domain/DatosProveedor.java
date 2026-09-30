package com.dessti.crm.compras.proveedor.domain;

/**
 * Agrupa los datos editables de un {@link Proveedor} (Req 29, V89) para pasarlos
 * de una sola vez a la fabrica {@link Proveedor#crear(DatosProveedor, String)} y a
 * {@link Proveedor#actualizar(DatosProveedor, String)}, evitando constructores con
 * muchos parametros posicionales y facilitando anadir campos sin romper firmas.
 *
 * <p>Todos los campos salvo {@code nombre} y {@code rfc} son opcionales; el
 * dominio ({@link ProveedorValidaciones}) normaliza y valida cada uno. No conlleva
 * logica: es un simple transporte inmutable dentro del dominio.</p>
 *
 * @param nombre           razon social o nombre; obligatorio (1..200).
 * @param rfc              identificador fiscal; obligatorio (12..13, formato valido).
 * @param email            correo electronico; opcional.
 * @param telefono         telefono; opcional (10..15 digitos).
 * @param personaContacto  nombre de la persona de contacto; opcional (<=200).
 * @param regimenFiscal    clave del regimen fiscal (SAT); opcional (<=10).
 * @param diasCredito      dias de credito; opcional, {@code null} = de contado (0..365).
 * @param domicilioCalle   calle y numero del domicilio fiscal; opcional (<=300).
 * @param domicilioCiudad  ciudad/municipio; opcional (<=150).
 * @param domicilioEstado  estado/entidad; opcional (<=150).
 * @param codigoPostal     codigo postal; opcional (5 digitos).
 */
public record DatosProveedor(
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

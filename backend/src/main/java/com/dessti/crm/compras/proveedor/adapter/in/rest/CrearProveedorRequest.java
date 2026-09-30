package com.dessti.crm.compras.proveedor.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Proveedor (Req 29.1).
 *
 * <p>DTO de entrada del contrato REST, <strong>distinto</strong> de la entidad
 * de persistencia y del comando de aplicacion
 * {@link com.dessti.crm.compras.proveedor.application.CrearProveedorCommand}
 * (Req 12.2). El controlador lo traduce al comando antes de invocar el servicio.</p>
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id} NO se
 * acepta en la peticion; se deriva del contexto autenticado.</p>
 *
 * <p><strong>Alcance de la validacion de campo:</strong> aqui solo se comprueba la
 * <em>presencia</em> y unos limites de longitud amplios (Bean Validation -&gt;
 * 400). El <em>formato</em> del RFC, del email y del telefono lo valida el dominio
 * en la capa de aplicacion (que responde 422), por lo que no se duplica aqui una
 * expresion regular estricta que alteraria el codigo de error esperado.</p>
 *
 * @param nombre           razon social o nombre; obligatorio (1..200).
 * @param rfc              identificador fiscal; obligatorio (12..13).
 * @param email            correo electronico; opcional.
 * @param telefono         telefono; opcional.
 * @param personaContacto  persona de contacto; opcional (V89).
 * @param regimenFiscal    clave del regimen fiscal; opcional (V89).
 * @param diasCredito      dias de credito; opcional (V89).
 * @param domicilioCalle   calle del domicilio fiscal; opcional (V89).
 * @param domicilioCiudad  ciudad; opcional (V89).
 * @param domicilioEstado  estado; opcional (V89).
 * @param codigoPostal     codigo postal; opcional (V89).
 */
public record CrearProveedorRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(min = 12, max = 13) String rfc,
        @Size(max = 254) String email,
        @Size(max = 20) String telefono,
        @Size(max = 200) String personaContacto,
        @Size(max = 10) String regimenFiscal,
        Integer diasCredito,
        @Size(max = 300) String domicilioCalle,
        @Size(max = 150) String domicilioCiudad,
        @Size(max = 150) String domicilioEstado,
        @Size(max = 5) String codigoPostal) {
}

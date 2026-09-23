package com.dessti.crm.tesoreria.application;

/**
 * Comando de creacion de una
 * {@link com.dessti.crm.tesoreria.domain.CuentaBancaria} (Req 43.1). Objeto de
 * entrada de la capa de aplicacion, distinto de la entidad. No incluye el
 * {@code tenant_id} (se deriva del contexto, Req 23.4).
 *
 * @param nombre nombre descriptivo; obligatorio y no vacio.
 * @param banco  banco de la cuenta; obligatorio y no vacio.
 * @param clabe  CLABE interbancaria (18 digitos); opcional.
 * @param moneda moneda ISO 4217 (3 letras); {@code null} o vacio usa {@code MXN}.
 */
public record CrearCuentaBancariaCommand(
        String nombre,
        String banco,
        String clabe,
        String moneda) {
}

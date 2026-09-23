package com.dessti.crm.contabilidad.polizas.application;

import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;

/**
 * Comando de creacion de una
 * {@link com.dessti.crm.contabilidad.polizas.domain.CuentaContable} (Req 38.1).
 * Objeto de entrada de la capa de aplicacion, distinto de la entidad. No incluye el
 * {@code tenant_id} (se deriva del contexto, Req 23.4).
 *
 * @param codigo     codigo de la cuenta; obligatorio y no vacio (unico por tenant).
 * @param nombre     nombre descriptivo; obligatorio y no vacio.
 * @param tipo       tipo contable; obligatorio.
 * @param naturaleza naturaleza del saldo; obligatoria.
 */
public record CrearCuentaContableCommand(
        String codigo,
        String nombre,
        TipoCuentaContable tipo,
        NaturalezaCuenta naturaleza) {
}

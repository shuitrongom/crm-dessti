package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando que describe la aplicacion de una parte de un {@link RegistrarPagoClienteCommand}
 * a una Factura concreta (Req 36.2). El servicio localiza la Cuenta_Por_Cobrar por
 * la Factura y aplica el monto acotado por su saldo (Property 13, Req 36.3).
 *
 * @param facturaId Factura a la que se aplica la porcion de pago; obligatoria.
 * @param monto     monto aplicado a esa Factura; positivo y acotado por el saldo (Req 36.3).
 */
public record AplicacionPagoCommand(UUID facturaId, BigDecimal monto) {
}

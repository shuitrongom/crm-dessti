package com.dessti.crm.contabilidad.cxc.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comando de registro de un {@link com.dessti.crm.contabilidad.cxc.domain.PagoCliente}
 * y su aplicacion a una o varias Facturas (Req 36.2, 36.3, 36.4). Objeto de entrada
 * de la capa de aplicacion, distinto de la entidad. No incluye el {@code tenant_id}
 * (se deriva del contexto, Req 23.4).
 *
 * @param clienteId     Cliente que paga; obligatorio (Req 36.2).
 * @param monto         monto total del pago; positivo (Req 36.2).
 * @param formaPago     forma de pago (clave del catalogo del SAT); opcional.
 * @param esParcialidad {@code true} si es parcialidad/diferido: exige y timbra un
 *                      Complemento_Pago via PAC (Req 36.4).
 * @param aplicaciones  desglose de la aplicacion del pago por Factura; obligatorio
 *                      y no vacio. La suma de sus montos no puede exceder el monto
 *                      del pago (Req 36.2).
 */
public record RegistrarPagoClienteCommand(
        UUID clienteId,
        BigDecimal monto,
        String formaPago,
        boolean esParcialidad,
        List<AplicacionPagoCommand> aplicaciones) {
}

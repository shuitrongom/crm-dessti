package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de aplicacion para registrar una Transferencia_Bancaria entre dos
 * Cuentas_Bancarias de la Empresa (Req 43).
 *
 * @param cuentaOrigenId  Cuenta_Bancaria de origen; obligatoria.
 * @param cuentaDestinoId Cuenta_Bancaria de destino; obligatoria y distinta del origen.
 * @param monto           monto a transferir; obligatorio y estrictamente positivo.
 * @param fecha           fecha del traspaso; obligatoria.
 * @param concepto        concepto descriptivo; opcional.
 */
public record RegistrarTransferenciaCommand(
        UUID cuentaOrigenId,
        UUID cuentaDestinoId,
        BigDecimal monto,
        LocalDate fecha,
        String concepto) {
}

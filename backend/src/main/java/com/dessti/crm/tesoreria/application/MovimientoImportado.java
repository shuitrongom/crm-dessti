package com.dessti.crm.tesoreria.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linea de un estado de cuenta bancario importada por el {@link ImportacionBancariaPort}
 * (Req 43.2). Record inmutable del contrato del puerto.
 *
 * <p>El {@code monto} viaja <strong>con signo</strong>: positivo para un deposito y
 * negativo para un retiro, coherente con el {@code Movimiento_Bancario} del dominio.</p>
 *
 * @param fecha       fecha del movimiento bancario; obligatoria.
 * @param monto       monto con signo (+ deposito / - retiro); obligatorio.
 * @param referencia  referencia del movimiento (folio, clave de rastreo); opcional.
 * @param descripcion descripcion/concepto del movimiento; opcional.
 */
public record MovimientoImportado(
        LocalDate fecha,
        BigDecimal monto,
        String referencia,
        String descripcion) {
}

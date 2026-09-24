package com.dessti.crm.contabilidad.electronica.domain.modelo;

import java.time.LocalDate;
import java.util.List;

/**
 * Modelo plano de una poliza para el Polizas_XML del SAT (Anexo 24). Es la entrada
 * al {@code GeneradorPolizasXml}.
 *
 * @param numUnIdenPol identificador unico de la poliza ({@code NumUnIdenPol}).
 * @param fecha        fecha de la poliza ({@code Fecha}, formato AAAA-MM-DD).
 * @param concepto     concepto de la poliza ({@code Concepto}).
 * @param transacciones renglones de la poliza (cargos/abonos por cuenta).
 */
public record PolizaSat(
        String numUnIdenPol,
        LocalDate fecha,
        String concepto,
        List<TransaccionSat> transacciones) {
}

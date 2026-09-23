package com.dessti.crm.platform.empresas.offboarding;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de salida de la <strong>eliminacion definitiva</strong> de los datos de
 * negocio de una Empresa tras el Periodo_Gracia (Req 69.3), distinto de cualquier
 * entidad de persistencia.
 *
 * <p>Refleja el <em>alcance</em> de la operacion (Req 69.6): que recursos se
 * eliminaron/anonimizaron y cuantos registros afecto cada uno
 * ({@link #eliminadosPorRecurso}), y que recursos se <strong>preservaron</strong>
 * por ser comprobantes fiscales bajo retencion ({@link #preservadosFiscales},
 * Req 69.4). La operacion afecta unicamente al {@code tenantId} objetivo
 * (Req 69.5).</p>
 *
 * @param tenantId             identificador de la Empresa (su {@code tenant_id}).
 * @param ejecutadoEn          instante de la eliminacion en UTC (Req 69.6).
 * @param eliminadosPorRecurso conteo de registros eliminados/anonimizados por
 *                             recurso (clave = nombre del recurso). Puede estar
 *                             vacio si no hay modulos de negocio registrados.
 * @param preservadosFiscales  nombres de recursos preservados por retencion
 *                             fiscal (Factura CFDI, Recibo_Nomina, Poliza_Contable).
 */
public record ResultadoEliminacionTenantDto(
        UUID tenantId,
        Instant ejecutadoEn,
        Map<String, Long> eliminadosPorRecurso,
        java.util.List<String> preservadosFiscales) {
}

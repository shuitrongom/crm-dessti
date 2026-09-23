package com.dessti.crm.calidad.application;

/**
 * DTO de salida de las agregaciones de <strong>solo lectura</strong> de los indicadores
 * de cultura de calidad del tenant (Req 70.6, 70.7). Todos los valores se derivan de los
 * datos del propio Sistema sin modificar los datos de origen (Req 22). Estos indicadores
 * pueden surfacearse posteriormente en el Tablero (Req 22, 48).
 *
 * @param noConformidadesAbiertas       No_Conformidades en estado 'abierta'.
 * @param noConformidadesEnTratamiento  No_Conformidades en estado 'en_tratamiento'.
 * @param noConformidadesCerradas       No_Conformidades en estado 'cerrada'.
 * @param accionesCorrectivasAbiertas   Acciones_Correctivas aun no cerradas.
 * @param accionesCorrectivasCerradas   Acciones_Correctivas cerradas.
 * @param tiempoMedioCierreDias         tiempo medio de cierre de Accion_Correctiva en
 *                                      dias (redondeado a 2 decimales); {@code null} si
 *                                      no hay acciones cerradas.
 * @param tasaReincidencia              tasa de reincidencia de una misma No_Conformidad
 *                                      (0..1, 2 decimales); {@code null} si no aplica.
 * @param quejasRegistradas             Quejas en estado 'registrada'.
 * @param quejasVinculadas              Quejas en estado 'vinculada'.
 * @param quejasAtendidas               Quejas en estado 'atendida' (atendidas en tiempo).
 */
public record IndicadoresCalidadDto(
        long noConformidadesAbiertas,
        long noConformidadesEnTratamiento,
        long noConformidadesCerradas,
        long accionesCorrectivasAbiertas,
        long accionesCorrectivasCerradas,
        Double tiempoMedioCierreDias,
        Double tasaReincidencia,
        long quejasRegistradas,
        long quejasVinculadas,
        long quejasAtendidas) {
}

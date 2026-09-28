package com.dessti.crm.reportesbi.application.ia;

/**
 * Puerto de salida hacia el <strong>generador de insights ejecutivos</strong> de la
 * Inteligencia de Negocio (Req 48, suite BI+IA). Es la frontera hexagonal que
 * <em>desacopla</em> la aplicacion del proveedor concreto de IA (LLM por HTTP), de modo
 * que el adaptador real pueda intercambiarse sin tocar el servicio ni el controlador.
 *
 * <h2>Contrato</h2>
 * <p>{@link #generar(SolicitudInsights)} recibe el consolidado del periodo (indicadores
 * con sus comparativos) y devuelve un {@link ResultadoInsights} con un resumen ejecutivo
 * y hallazgos en espanol de Mexico. El resultado indica su procedencia
 * ({@link ResultadoInsights#generadoPorIa()}).</p>
 *
 * <h2>Degradacion gracil (patron del proyecto)</h2>
 * <p>Toda implementacion <strong>debe</strong> devolver siempre un resultado util y
 * nunca propagar excepciones de red al llamador: si el proveedor de IA no esta
 * configurado ({@code crm.ia.*} vacio), falla o agota el tiempo, el adaptador degrada de
 * forma gracil a un narrativo heuristico determinista. Asi la suite BI nunca se rompe
 * por la IA, replicando el enfoque ya usado en la poliza contable automatica.</p>
 *
 * <h2>Portabilidad y secretos (Req 11)</h2>
 * <p>El contrato usa <em>records</em> inmutables de solicitud/resultado, sin tipos de
 * dominio ni de persistencia. Las credenciales del proveedor se resuelven
 * <strong>exclusivamente</strong> desde {@code crm.ia.*} sobre variables de entorno y
 * <strong>nunca</strong> se embeben en el codigo ni se escriben en logs.</p>
 */
public interface GeneradorInsightsPort {

    /**
     * Genera el resumen ejecutivo y los hallazgos del periodo a partir del consolidado.
     * Nunca lanza excepciones de integracion: ante cualquier fallo del proveedor,
     * degrada de forma gracil a un narrativo heuristico.
     *
     * @param solicitud contexto del periodo e indicadores agregados; obligatorio.
     * @return el resultado con el narrativo en es-MX y su procedencia.
     */
    ResultadoInsights generar(SolicitudInsights solicitud);
}

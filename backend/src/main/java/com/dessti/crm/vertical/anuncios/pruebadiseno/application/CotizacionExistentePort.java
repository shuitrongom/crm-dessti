package com.dessti.crm.vertical.anuncios.pruebadiseno.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de una Cotizacion dentro del tenant
 * vigente, requerido al generar una Prueba_Diseno (Req 15.1).
 *
 * <p>Se introduce un puerto propio del submodulo de Pruebas de Diseno —en lugar de
 * inyectar directamente el repositorio de Cotizaciones— para <strong>desacoplar</strong>
 * este flujo del vertical del Nucleo: la aplicacion de Pruebas de Diseno solo
 * necesita saber si una Cotizacion existe, no conocer su modelo de persistencia.
 * El adaptador {@code CotizacionExistenteAdapter} implementa este puerto delegando
 * en el puerto del Nucleo
 * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort#existe(UUID)}
 * (Req 10.3), preservando la portabilidad del Nucleo e invirtiendo la dependencia
 * vertical&rarr;persistencia-del-nucleo (Req 10.5).</p>
 */
public interface CotizacionExistentePort {

    /**
     * Indica si existe una Cotizacion con el identificador dado en el tenant
     * vigente (Req 15.1). El aislamiento por tenant lo garantizan el filtro global
     * de Hibernate y la RLS (Req 23), de modo que una Cotizacion de otro tenant no
     * se considera existente.
     *
     * @param cotizacionId identificador de la Cotizacion a verificar.
     * @return {@code true} si la Cotizacion existe en el tenant.
     */
    boolean existeCotizacion(UUID cotizacionId);
}

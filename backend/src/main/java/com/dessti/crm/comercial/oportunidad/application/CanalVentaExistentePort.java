package com.dessti.crm.comercial.oportunidad.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Canal_Venta
 * <strong>activo</strong> dentro del tenant vigente, requerido al clasificar una
 * Oportunidad por canal de venta (Req 63.1).
 *
 * <p>Se introduce un puerto propio —en lugar de inyectar directamente el
 * repositorio de Canales— para <strong>desacoplar</strong> el submodulo de
 * Oportunidades del de Canales de venta: la aplicacion de Oportunidades solo
 * necesita saber si un canal existe, no conocer su modelo de persistencia. El
 * adaptador {@code CanalVentaExistenteAdapter} implementa este puerto delegando
 * en {@code CanalVentaRepository.findByIdAndActivoTrue} (ambos en el mismo modulo
 * comercial-crm), preservando la portabilidad del nucleo, del mismo modo que
 * {@link ClienteExistentePort}.</p>
 */
public interface CanalVentaExistentePort {

    /**
     * Indica si existe un Canal_Venta activo con el identificador dado en el
     * tenant vigente (Req 63.1). El aislamiento por tenant lo garantiza el filtro
     * global de Hibernate y la RLS (Req 23).
     *
     * @param canalVentaId identificador del Canal_Venta a verificar.
     * @return {@code true} si el canal existe y esta activo en el tenant.
     */
    boolean existeCanalVentaActivo(UUID canalVentaId);
}

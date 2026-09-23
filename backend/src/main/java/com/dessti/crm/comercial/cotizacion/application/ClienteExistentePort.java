package com.dessti.crm.comercial.cotizacion.application;

import java.util.UUID;

/**
 * Puerto de salida que verifica la existencia de un Cliente <strong>activo</strong>
 * dentro del tenant vigente, requerido al crear una Cotizacion (Req 6.1, 6.2).
 *
 * <p>Se introduce un puerto propio del submodulo de Cotizaciones —en lugar de
 * inyectar directamente el repositorio de Clientes— para <strong>desacoplar</strong>
 * este submodulo del de Clientes: la aplicacion de Cotizaciones solo necesita
 * saber si un Cliente existe, no conocer su modelo de persistencia. El adaptador
 * {@code ClienteExistenteAdapter} implementa este puerto delegando en
 * {@code ClienteRepository.findByIdAndActivoTrue} (ambos en el mismo modulo
 * comercial-crm), preservando la portabilidad del nucleo. Sigue el mismo patron
 * que el puerto homonimo del submodulo de Oportunidades (tarea 17.1).</p>
 */
public interface ClienteExistentePort {

    /**
     * Indica si existe un Cliente activo con el identificador dado en el tenant
     * vigente (Req 6.1). El aislamiento por tenant lo garantiza el filtro global
     * de Hibernate y la RLS (Req 23).
     *
     * @param clienteId identificador del Cliente a verificar.
     * @return {@code true} si el Cliente existe y esta activo en el tenant.
     */
    boolean existeClienteActivo(UUID clienteId);
}

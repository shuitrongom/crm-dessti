package com.dessti.crm.portalcliente.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto que el <strong>Portal del Cliente</strong> (Nucleo) publica para consumir
 * los Ticket_Servicio del Modulo-Vertical de anuncios (parte de Mantenimiento)
 * <em>por puerto</em>, sin depender de sus clases concretas (repositorio, entidad
 * ni DTO del vertical). Invierte la dependencia Nucleo&rarr;vertical: el Portal
 * define el puerto y el vertical lo implementa (Req 10.5, 4.5).
 */
public interface ResumenTicketsPort {

    /**
     * Lista de forma paginada los Ticket_Servicio del Cliente (Req 45.1).
     *
     * @param clienteId Cliente cuyos tickets se listan; obligatorio.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de resumenes de Ticket_Servicio del Cliente.
     */
    Page<TicketServicioResumen> listarPorCliente(UUID clienteId, Pageable pageable);
}

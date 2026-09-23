package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.mantenimiento.domain.TicketServicio;
import com.dessti.crm.portalcliente.application.ResumenTicketsPort;
import com.dessti.crm.portalcliente.application.TicketServicioResumen;

/**
 * Adaptador que implementa el puerto {@link ResumenTicketsPort} <em>definido por
 * el Nucleo (el Portal del Cliente)</em>, delegando en el
 * {@link TicketServicioRepository} del flujo de Mantenimiento del vertical de
 * anuncios. Invierte la dependencia Nucleo&rarr;vertical (Req 10.5): el Portal ya
 * no conoce las clases concretas del vertical; es este adaptador —que reside con
 * el vertical y viajara con el en la tarea 8.3— quien las traduce a la forma que
 * el Portal publica.
 *
 * <p>La consulta {@code buscarConFiltros} queda acotada al tenant vigente por el
 * filtro global de Hibernate y por la RLS (Req 23), y filtra por el Cliente del
 * Portal.</p>
 */
@Component("ticketServicioResumenPortalAdapter")
public class ResumenTicketsPortalAdapter implements ResumenTicketsPort {

    private final TicketServicioRepository ticketServicioRepository;

    public ResumenTicketsPortalAdapter(TicketServicioRepository ticketServicioRepository) {
        this.ticketServicioRepository = ticketServicioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketServicioResumen> listarPorCliente(UUID clienteId, Pageable pageable) {
        return ticketServicioRepository.buscarConFiltros(null, clienteId, pageable)
                .map(ResumenTicketsPortalAdapter::proyectar);
    }

    private static TicketServicioResumen proyectar(TicketServicio ticket) {
        return new TicketServicioResumen(
                ticket.getId(),
                ticket.getContratoMantenimientoId(),
                ticket.getClienteId(),
                ticket.getOrigen().valorBd(),
                ticket.getEstado().valorBd(),
                ticket.getAsignadoTipo() == null ? null : ticket.getAsignadoTipo().valorBd(),
                ticket.getAsignadoId(),
                ticket.getAbiertoEn(),
                ticket.getResueltoEn(),
                ticket.getSlaRespuestaCumplido(),
                ticket.getSlaResolucionCumplido(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}

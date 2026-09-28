package com.dessti.crm.calidad.adapter.out.portal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.dessti.crm.calidad.adapter.out.persistence.QuejaClienteRepository;
import com.dessti.crm.calidad.application.RegistrarQuejaClienteCommand;
import com.dessti.crm.calidad.application.ServicioQuejasCliente;
import com.dessti.crm.calidad.domain.OrigenQueja;
import com.dessti.crm.calidad.domain.QuejaCliente;
import com.dessti.crm.portalcliente.application.QuejaPortalResumen;
import com.dessti.crm.portalcliente.application.RegistroQuejaPortalPort;

/**
 * Adaptador de salida que implementa el {@link RegistroQuejaPortalPort} del Portal del
 * Cliente (Req 45, 70.1, 70.8), delegando en {@link ServicioQuejasCliente} para el
 * alta (reutilizando su validacion y auditoria) y en el {@link QuejaClienteRepository}
 * para el listado acotado por Cliente. Invierte la dependencia: el Portal (Nucleo)
 * define el puerto y este adaptador del modulo {@code calidad} lo implementa.
 *
 * <p>La queja se registra con {@link OrigenQueja#PORTAL} y el {@code clienteId} que el
 * Portal ya resolvio desde el usuario autenticado; el {@code canalSocialId} es
 * {@code null} (solo aplica a quejas de origen social).</p>
 */
@Component
public class RegistroQuejaPortalAdapter implements RegistroQuejaPortalPort {

    private final ServicioQuejasCliente servicioQuejasCliente;
    private final QuejaClienteRepository quejaClienteRepository;

    public RegistroQuejaPortalAdapter(ServicioQuejasCliente servicioQuejasCliente,
                                      QuejaClienteRepository quejaClienteRepository) {
        this.servicioQuejasCliente = servicioQuejasCliente;
        this.quejaClienteRepository = quejaClienteRepository;
    }

    @Override
    public QuejaPortalResumen registrarDesdePortal(UUID clienteId, String descripcion) {
        var dto = servicioQuejasCliente.registrar(new RegistrarQuejaClienteCommand(
                clienteId, OrigenQueja.PORTAL, null, descripcion));
        return new QuejaPortalResumen(
                dto.id(), dto.descripcion(), dto.estado(), dto.registradaEn());
    }

    @Override
    public Page<QuejaPortalResumen> listarPorCliente(UUID clienteId, Pageable pageable) {
        // Acota por Cliente (origen/estado sin filtrar); el listado del Portal solo
        // muestra las quejas del propio Cliente.
        Page<QuejaCliente> pagina =
                quejaClienteRepository.buscar(null, clienteId, null, pageable);
        return pagina.map(q -> new QuejaPortalResumen(
                q.getId(), q.getDescripcion(), q.getEstado().valorBd(), q.getRegistradaEn()));
    }
}

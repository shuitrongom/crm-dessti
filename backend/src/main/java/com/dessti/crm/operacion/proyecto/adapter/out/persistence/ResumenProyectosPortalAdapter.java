package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.proyecto.application.ProyectoDto;
import com.dessti.crm.operacion.proyecto.application.ServicioProyectos;
import com.dessti.crm.operacion.proyecto.application.SitioAvanceDto;
import com.dessti.crm.portalcliente.application.ProyectoResumen;
import com.dessti.crm.portalcliente.application.ResumenProyectosPort;
import com.dessti.crm.portalcliente.application.SitioAvanceResumen;
import com.dessti.crm.portalcliente.application.SitioResumen;

/**
 * Adaptador que implementa el puerto {@link ResumenProyectosPort} <em>definido por
 * el Nucleo (el Portal del Cliente)</em>, delegando en el {@link ServicioProyectos}
 * del flujo de Proyecto/Sitio del vertical de anuncios. Invierte la dependencia
 * Nucleo&rarr;vertical (Req 10.5): el Portal ya no conoce las clases concretas del
 * vertical; es este adaptador —que reside con el vertical y viaja con el en la
 * tarea 8.3— quien traduce el {@code ProyectoDto} del vertical a la forma que el
 * Portal publica.
 *
 * <p>El {@link ServicioProyectos} ya aplica el aislamiento por tenant (filtro
 * global de Hibernate + RLS, Req 23) y traduce a 404 el Proyecto no accesible
 * (Req 23.3). La guarda de propiedad por Cliente la aplica el Portal comparando el
 * {@code clienteId} del resumen (Req 45.3).</p>
 */
@Component("proyectoResumenPortalAdapter")
public class ResumenProyectosPortalAdapter implements ResumenProyectosPort {

    private final ServicioProyectos servicioProyectos;

    public ResumenProyectosPortalAdapter(ServicioProyectos servicioProyectos) {
        this.servicioProyectos = servicioProyectos;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProyectoResumen> listarPorCliente(UUID clienteId, Pageable pageable) {
        return servicioProyectos.listar(clienteId, pageable)
                .map(ResumenProyectosPortalAdapter::proyectar);
    }

    @Override
    @Transactional(readOnly = true)
    public ProyectoResumen consultar(UUID proyectoId) {
        return proyectar(servicioProyectos.consultar(proyectoId));
    }

    private static ProyectoResumen proyectar(ProyectoDto dto) {
        return new ProyectoResumen(
                dto.id(),
                dto.clienteId(),
                dto.nombre(),
                dto.estadoConsolidado(),
                dto.sitios().stream()
                        .map(ResumenProyectosPortalAdapter::proyectar)
                        .toList(),
                dto.version(),
                dto.createdAt(),
                dto.updatedAt());
    }

    private static SitioAvanceResumen proyectar(SitioAvanceDto avance) {
        return new SitioAvanceResumen(
                new SitioResumen(
                        avance.sitio().id(),
                        avance.sitio().proyectoId(),
                        avance.sitio().nombre(),
                        avance.sitio().direccion(),
                        avance.sitio().version(),
                        avance.sitio().createdAt(),
                        avance.sitio().updatedAt()),
                avance.tieneLevantamientoCompletado(),
                avance.tienePermisoAprobado(),
                avance.tieneOrdenFabricacionTerminada(),
                avance.tieneInstalacionCompletada());
    }
}

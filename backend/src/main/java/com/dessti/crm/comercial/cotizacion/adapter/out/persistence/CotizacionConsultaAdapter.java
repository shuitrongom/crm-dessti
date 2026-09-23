package com.dessti.crm.comercial.cotizacion.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.CotizacionConsulta;
import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;

/**
 * Adaptador de salida <strong>del Nucleo</strong> que implementa el
 * {@link CotizacionConsultaPort} delegando en el {@link CotizacionRepository} del
 * propio submodulo de Cotizaciones (Req 4.5, 10.3). Publica la vista de consulta
 * de una Cotizacion para que los Modulos-Vertical la consuman por puerto, sin
 * exponerles la entidad JPA {@code Cotizacion} ni su repositorio.
 *
 * <p>Ambas consultas {@code findById} ya estan acotadas al tenant vigente por el
 * filtro global de Hibernate y por la RLS (Req 23), de modo que una Cotizacion de
 * otro tenant no se considera accesible: {@link #existe(UUID)} devuelve
 * {@code false} y {@link #buscar(UUID)} devuelve {@link Optional#empty()}.</p>
 */
@Component("cotizacionConsultaAdapter")
public class CotizacionConsultaAdapter implements CotizacionConsultaPort {

    private final CotizacionRepository cotizacionRepository;

    public CotizacionConsultaAdapter(CotizacionRepository cotizacionRepository) {
        this.cotizacionRepository = cotizacionRepository;
    }

    @Override
    public boolean existe(UUID cotizacionId) {
        if (cotizacionId == null) {
            return false;
        }
        return cotizacionRepository.findById(cotizacionId).isPresent();
    }

    @Override
    public Optional<CotizacionConsulta> buscar(UUID cotizacionId) {
        if (cotizacionId == null) {
            return Optional.empty();
        }
        return cotizacionRepository.findById(cotizacionId)
                .map(CotizacionConsultaAdapter::proyectar);
    }

    private static CotizacionConsulta proyectar(Cotizacion cotizacion) {
        return new CotizacionConsulta(
                cotizacion.getId(),
                cotizacion.getEstado().valorBd(),
                cotizacion.getClienteId(),
                cotizacion.getTotal());
    }
}

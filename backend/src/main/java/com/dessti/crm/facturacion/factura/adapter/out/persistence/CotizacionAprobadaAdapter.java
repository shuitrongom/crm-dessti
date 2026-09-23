package com.dessti.crm.facturacion.factura.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;
import com.dessti.crm.facturacion.factura.application.CotizacionAprobadaPort;
import com.dessti.crm.facturacion.factura.application.CotizacionParaFactura;

/**
 * Adaptador de salida que implementa {@link CotizacionAprobadaPort} delegando en el
 * {@link CotizacionRepository} del modulo comercial-crm (Req 34.1). Aisla la
 * dependencia hacia el submodulo de Cotizaciones en la capa de infraestructura,
 * dejando la aplicacion de facturacion libre de acoplamiento a su persistencia.
 * Sigue el patron de {@code CotizacionParaFabricacionAdapter} del modulo de
 * produccion.
 *
 * <p>La consulta {@code findById} ya esta acotada al tenant vigente por el filtro
 * global de Hibernate y por la RLS (Req 23): una Cotizacion de otro tenant no se
 * considera accesible (se devuelve {@link Optional#empty()}).</p>
 */
@Component("facturaCotizacionAprobadaAdapter")
public class CotizacionAprobadaAdapter implements CotizacionAprobadaPort {

    private final CotizacionRepository cotizacionRepository;

    public CotizacionAprobadaAdapter(CotizacionRepository cotizacionRepository) {
        this.cotizacionRepository = cotizacionRepository;
    }

    @Override
    public Optional<CotizacionParaFactura> buscarParaFactura(UUID cotizacionId) {
        if (cotizacionId == null) {
            return Optional.empty();
        }
        return cotizacionRepository.findById(cotizacionId)
                .map(CotizacionAprobadaAdapter::proyectar);
    }

    private static CotizacionParaFactura proyectar(Cotizacion cotizacion) {
        return new CotizacionParaFactura(
                cotizacion.getId(),
                cotizacion.getEstado().valorBd(),
                cotizacion.getClienteId(),
                cotizacion.getTotal());
    }
}

package com.dessti.crm.facturacion.notacredito.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.facturacion.factura.domain.Factura;
import com.dessti.crm.facturacion.notacredito.application.FacturaReferenciada;
import com.dessti.crm.facturacion.notacredito.application.FacturaReferenciadaPort;

/**
 * Adaptador de salida que implementa {@link FacturaReferenciadaPort} delegando en
 * el {@link FacturaRepository} del submodulo de Facturas (Req 37.1, 37.2). Aisla la
 * dependencia hacia la persistencia de la Factura, dejando la aplicacion de notas
 * de credito libre de acoplamiento a ella.
 *
 * <p>La consulta {@code findById} esta acotada al tenant vigente por el filtro
 * global de Hibernate y por la RLS (Req 23), de modo que una Factura de otro tenant
 * no se considera accesible.</p>
 */
@Component("notaCreditoFacturaReferenciadaAdapter")
public class FacturaReferenciadaAdapter implements FacturaReferenciadaPort {

    private final FacturaRepository facturaRepository;

    public FacturaReferenciadaAdapter(FacturaRepository facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    @Override
    public Optional<FacturaReferenciada> buscar(UUID facturaId) {
        if (facturaId == null) {
            return Optional.empty();
        }
        return facturaRepository.findById(facturaId).map(FacturaReferenciadaAdapter::proyectar);
    }

    private static FacturaReferenciada proyectar(Factura factura) {
        return new FacturaReferenciada(
                factura.getId(),
                factura.getEstado().valorBd(),
                factura.getClienteId(),
                factura.getTotal());
    }
}

package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.facturacion.factura.application.OrdenFabricacionParaFactura;
import com.dessti.crm.facturacion.factura.application.OrdenFabricacionParaFacturaPort;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;

/**
 * Adaptador de salida que implementa el puerto del Nucleo de facturacion
 * {@link OrdenFabricacionParaFacturaPort}, provisto desde el <strong>vertical de
 * anuncios</strong> (Req 10.5). Al residir el adaptador en el vertical, el Nucleo
 * (modulo de facturacion) deja de importar clases de {@code ordenfabricacion} y
 * solo depende de su propio puerto, mientras que el vertical aporta la
 * implementacion. Delega en el {@link OrdenFabricacionRepository} (Cliente y
 * Cotizacion de origen de la OF, ya en el vertical) y en el puerto de consulta del
 * Nucleo {@link CotizacionConsultaPort#buscar(UUID)} para el importe base
 * (Req 34.1, 10.3), sin acoplarse al {@code CotizacionRepository} interno del
 * Nucleo.
 *
 * <p>Ambas consultas estan acotadas al tenant vigente por el filtro global de
 * Hibernate y por la RLS (Req 23), de modo que una OF o una Cotizacion de otro
 * tenant no se consideran accesibles. Si la OF existe pero su Cotizacion de origen
 * no es accesible, el importe base se reporta como {@link BigDecimal#ZERO} (caso
 * defensivo; en la practica la OF siempre proviene de una Cotizacion del mismo
 * tenant).</p>
 */
@Component("facturaOrdenFabricacionParaFacturaAdapter")
public class OrdenFabricacionParaFacturaAdapter implements OrdenFabricacionParaFacturaPort {

    private final OrdenFabricacionRepository ordenFabricacionRepository;
    private final CotizacionConsultaPort cotizacionConsulta;

    public OrdenFabricacionParaFacturaAdapter(OrdenFabricacionRepository ordenFabricacionRepository,
                                              CotizacionConsultaPort cotizacionConsulta) {
        this.ordenFabricacionRepository = ordenFabricacionRepository;
        this.cotizacionConsulta = cotizacionConsulta;
    }

    @Override
    public Optional<OrdenFabricacionParaFactura> buscarParaFactura(UUID ordenFabricacionId) {
        if (ordenFabricacionId == null) {
            return Optional.empty();
        }
        return ordenFabricacionRepository.findById(ordenFabricacionId)
                .map(this::proyectar);
    }

    private OrdenFabricacionParaFactura proyectar(OrdenFabricacion orden) {
        BigDecimal base = cotizacionConsulta.buscar(orden.getCotizacionId())
                .map(cotizacion -> cotizacion.total())
                .orElse(BigDecimal.ZERO);
        return new OrdenFabricacionParaFactura(orden.getId(), orden.getClienteId(), base);
    }
}

package com.dessti.crm.comercial.cotizacion.adapter.out.portal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.CotizacionDto;
import com.dessti.crm.comercial.cotizacion.application.ServicioCotizaciones;
import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;
import com.dessti.crm.portalcliente.application.DecisionCotizacionPort;

/**
 * Adaptador de salida que implementa el {@link DecisionCotizacionPort} del Portal del
 * Cliente (Req 45.2), delegando en {@link ServicioCotizaciones#cambiarEstado} del
 * Nucleo comercial. Traduce la decision del Cliente (aprobar/rechazar) a la
 * transicion de la maquina de estados de la Cotizacion ({@code enviada ->
 * aprobada|rechazada}, Req 6.6), reutilizando toda la logica y auditoria existentes.
 *
 * <p>Invierte la dependencia: el Portal (Nucleo) define el puerto y este adaptador
 * del modulo comercial lo implementa, sin que el Portal conozca la persistencia ni
 * la maquina de estados de la Cotizacion. La guarda de propiedad (Cotizacion del
 * Cliente del Portal) la aplica {@code ServicioPortalCliente} antes de invocar aqui.</p>
 */
@Component
public class DecisionCotizacionPortalAdapter implements DecisionCotizacionPort {

    private final ServicioCotizaciones servicioCotizaciones;

    public DecisionCotizacionPortalAdapter(ServicioCotizaciones servicioCotizaciones) {
        this.servicioCotizaciones = servicioCotizaciones;
    }

    @Override
    public CotizacionDto aprobar(UUID cotizacionId) {
        return servicioCotizaciones.cambiarEstado(cotizacionId, EstadoCotizacion.APROBADA.valorBd());
    }

    @Override
    public CotizacionDto rechazar(UUID cotizacionId) {
        return servicioCotizaciones.cambiarEstado(cotizacionId, EstadoCotizacion.RECHAZADA.valorBd());
    }
}

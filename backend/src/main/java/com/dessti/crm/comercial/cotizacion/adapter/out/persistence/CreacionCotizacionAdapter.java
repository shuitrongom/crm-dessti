package com.dessti.crm.comercial.cotizacion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.ServicioCotizaciones;
import com.dessti.crm.comercial.oportunidad.application.CreacionCotizacionPort;

/**
 * Adaptador que implementa el {@link CreacionCotizacionPort} declarado por el
 * submodulo de Oportunidades (Req 14.5), satisfaciendo la dependencia opcional de
 * {@code ServicioOportunidades} (que a partir de la tarea 17.2 pasa a estar
 * <strong>presente</strong>). Delega en {@link ServicioCotizaciones}.
 *
 * <p><strong>Semantica de la conversion (Req 14.5, 6.1):</strong> crea un
 * <em>cascaron</em> de Cotizacion en estado {@code borrador} vinculado al mismo
 * Cliente y a la Oportunidad de origen, y devuelve su identificador. La Cotizacion
 * asi creada no tiene partidas todavia: las partidas se agregan despues por los
 * endpoints de partida. La regla de >=1 Partida_Cotizacion del Req 6.1/6.2 aplica
 * al camino de alta manual y al envio ({@code borrador -> enviada} exige >=1
 * partida), de modo que un cascaron vacio no puede enviarse ni aprobarse. Esta
 * decision se documenta tambien en la migracion V14 (DECISION 4) y en el dominio
 * {@code Cotizacion.crearCascaronConversion}.</p>
 *
 * <p>Se ubica en el submodulo de Cotizaciones (no en el de Oportunidades) para que
 * la dependencia apunte del implementador al puerto, coherente con la direccion de
 * dependencias de la arquitectura hexagonal.</p>
 */
@Component
public class CreacionCotizacionAdapter implements CreacionCotizacionPort {

    private final ServicioCotizaciones servicioCotizaciones;

    public CreacionCotizacionAdapter(ServicioCotizaciones servicioCotizaciones) {
        this.servicioCotizaciones = servicioCotizaciones;
    }

    @Override
    public UUID crearDesdeOportunidad(UUID oportunidadId, UUID clienteId, String actor) {
        return servicioCotizaciones.crearDesdeOportunidad(oportunidadId, clienteId, actor);
    }
}

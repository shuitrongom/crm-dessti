package com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.CotizacionExistentePort;

/**
 * Adaptador de salida que implementa {@link CotizacionExistentePort} delegando en
 * el puerto de consulta del Nucleo
 * {@link CotizacionConsultaPort#existe(UUID)} (submodulo de Cotizaciones,
 * {@code comercial.cotizacion}). Al consumir el <strong>puerto del Nucleo</strong>
 * en lugar del {@code CotizacionRepository} interno, el vertical de anuncios accede
 * a la Cotizacion exclusivamente por contrato, invirtiendo la dependencia
 * vertical&rarr;persistencia-del-nucleo (Req 10.3, 10.5, 4.5).
 *
 * <p>La consulta {@code existe} ya esta acotada al tenant vigente por el filtro
 * global de Hibernate y por la RLS del Nucleo (Req 23), de modo que una Cotizacion
 * de otro tenant no se considera existente. Este adaptador aisla la dependencia
 * hacia el Nucleo en la capa de infraestructura, dejando la aplicacion de Pruebas
 * de Diseno libre de acoplamiento a su persistencia.</p>
 */
@Component("pruebaDisenoCotizacionExistenteAdapter")
public class CotizacionExistenteAdapter implements CotizacionExistentePort {

    private final CotizacionConsultaPort cotizacionConsulta;

    public CotizacionExistenteAdapter(CotizacionConsultaPort cotizacionConsulta) {
        this.cotizacionConsulta = cotizacionConsulta;
    }

    @Override
    public boolean existeCotizacion(UUID cotizacionId) {
        if (cotizacionId == null) {
            return false;
        }
        return cotizacionConsulta.existe(cotizacionId);
    }
}

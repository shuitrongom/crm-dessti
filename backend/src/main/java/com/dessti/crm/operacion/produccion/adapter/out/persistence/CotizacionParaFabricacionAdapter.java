package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.CotizacionConsulta;
import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.operacion.produccion.application.CotizacionParaFabricacion;
import com.dessti.crm.operacion.produccion.application.CotizacionParaFabricacionPort;

/**
 * Adaptador de salida que implementa {@link CotizacionParaFabricacionPort}
 * delegando en el puerto de consulta del Nucleo
 * {@link CotizacionConsultaPort#buscar(UUID)} (submodulo de Cotizaciones,
 * {@code comercial.cotizacion}) (Req 7.1, 7.2, 7.9, 10.3, 10.5). Al consumir el
 * <strong>puerto del Nucleo</strong> en lugar del {@code CotizacionRepository}
 * interno y de la entidad {@code Cotizacion}, el vertical de anuncios accede a la
 * Cotizacion sin acoplarse a la persistencia del Nucleo, invirtiendo la dependencia
 * vertical&rarr;persistencia-del-nucleo. Sigue el patron de
 * {@code CotizacionExistenteAdapter} del submodulo de Pruebas de Diseno.
 *
 * <p>La consulta del puerto ya esta acotada al tenant vigente por el filtro global
 * de Hibernate y por la RLS (Req 23), de modo que una Cotizacion de otro tenant no
 * se considera accesible (se devuelve {@link Optional#empty()}).</p>
 */
@Component("ordenFabricacionCotizacionParaFabricacionAdapter")
public class CotizacionParaFabricacionAdapter implements CotizacionParaFabricacionPort {

    private final CotizacionConsultaPort cotizacionConsulta;

    public CotizacionParaFabricacionAdapter(CotizacionConsultaPort cotizacionConsulta) {
        this.cotizacionConsulta = cotizacionConsulta;
    }

    @Override
    public Optional<CotizacionParaFabricacion> buscarParaFabricacion(UUID cotizacionId) {
        if (cotizacionId == null) {
            return Optional.empty();
        }
        return cotizacionConsulta.buscar(cotizacionId)
                .map(CotizacionParaFabricacionAdapter::proyectar);
    }

    private static CotizacionParaFabricacion proyectar(CotizacionConsulta cotizacion) {
        return new CotizacionParaFabricacion(
                cotizacion.id(),
                cotizacion.estado(),
                cotizacion.clienteId());
    }
}

package com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionExistentePort;
import com.dessti.crm.vertical.anuncios.levantamiento.application.EnlacesLevantamientoPort;

/**
 * Adaptador de salida que implementa {@link EnlacesLevantamientoPort} delegando en
 * puertos de consulta del Nucleo: {@link CotizacionConsultaPort#existe(UUID)}
 * (submodulo de Cotizaciones, {@code comercial.cotizacion}) y
 * {@link OrdenFabricacionExistentePort#existePorId(UUID)} (Nucleo
 * {@code operacion.produccion}), para verificar la existencia de los vinculos
 * opcionales de un Levantamiento_Sitio (Req 16.2).
 *
 * <p>Tras mover la Orden_Fabricacion al Nucleo, el vertical de anuncios accede a
 * ella <strong>por PUERTO</strong> ({@code ..application..}) y ya no por el
 * {@code OrdenFabricacionRepository} interno, respetando la regla de que un vertical
 * no se acopla a la persistencia interna de otro modulo (Req 4.5, 10.3). Igual que
 * con la Cotizacion, se invierte la dependencia vertical&rarr;persistencia-del-nucleo
 * hacia una interfaz estable.</p>
 *
 * <p>Ambas consultas ya estan acotadas al tenant vigente por el filtro global de
 * Hibernate y por la RLS (Req 23), de modo que un recurso de otro tenant no se
 * considera existente (la aplicacion lo traduce a 404, Req 23.3).</p>
 */
@Component("levantamientoEnlacesAdapter")
public class EnlacesLevantamientoAdapter implements EnlacesLevantamientoPort {

    private final CotizacionConsultaPort cotizacionConsulta;
    private final OrdenFabricacionExistentePort ordenFabricacionExistente;

    public EnlacesLevantamientoAdapter(CotizacionConsultaPort cotizacionConsulta,
                                       OrdenFabricacionExistentePort ordenFabricacionExistente) {
        this.cotizacionConsulta = cotizacionConsulta;
        this.ordenFabricacionExistente = ordenFabricacionExistente;
    }

    @Override
    public boolean existeCotizacion(UUID cotizacionId) {
        if (cotizacionId == null) {
            return false;
        }
        return cotizacionConsulta.existe(cotizacionId);
    }

    @Override
    public boolean existeOrdenFabricacion(UUID ordenFabricacionId) {
        if (ordenFabricacionId == null) {
            return false;
        }
        return ordenFabricacionExistente.existePorId(ordenFabricacionId);
    }
}
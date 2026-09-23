package com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.produccion.application.PruebaDisenoAprobadaPort;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.EstadoPruebaDiseno;

/**
 * Adaptador de salida que implementa {@link PruebaDisenoAprobadaPort} delegando en
 * el {@link PruebaDisenoRepository}. Expone la precondicion de diseno de la
 * Orden_Fabricacion (Req 15.5): la Cotizacion tiene al menos una Prueba_Diseno
 * {@code aprobada}. Lo consumira el bloque 19 (tarea 19.1, Property 7).
 *
 * <p>La consulta {@code existsByCotizacionIdAndEstado} ya esta acotada al tenant
 * vigente por el filtro global de Hibernate y por la RLS (Req 23).</p>
 */
@Component("pruebaDisenoAprobadaAdapter")
public class PruebaDisenoAprobadaAdapter implements PruebaDisenoAprobadaPort {

    private final PruebaDisenoRepository pruebaDisenoRepository;

    public PruebaDisenoAprobadaAdapter(PruebaDisenoRepository pruebaDisenoRepository) {
        this.pruebaDisenoRepository = pruebaDisenoRepository;
    }

    @Override
    public boolean tieneAprobadaPorCotizacion(UUID cotizacionId) {
        if (cotizacionId == null) {
            return false;
        }
        return pruebaDisenoRepository.existsByCotizacionIdAndEstado(
                cotizacionId, EstadoPruebaDiseno.APROBADA);
    }
}

package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.produccion.application.OrdenFabricacionTerminadaPort;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;

/**
 * Adaptador de salida que implementa {@link OrdenFabricacionTerminadaPort}
 * delegando en el {@link OrdenFabricacionRepository}. Expone la guarda de creacion
 * de la Orden_Trabajo_Instalacion del Req 19.2 (la Orden_Fabricacion esta
 * {@code terminada}) y la denormalizacion del Cliente para el filtro del Req 19.7.
 * Lo consume el bloque 22 (instalacion, tarea 22.1).
 *
 * <p>Las consultas {@code existsByIdAndEstado} / {@code findById} ya estan acotadas
 * al tenant vigente por el filtro global de Hibernate y por la RLS (Req 23), de
 * modo que una Orden_Fabricacion de otro tenant no se considera accesible. Sigue el
 * patron de {@code PermisoAprobadoAdapter} y {@code CotizacionParaFabricacionAdapter}.</p>
 */
@Component("ordenFabricacionTerminadaAdapter")
public class OrdenFabricacionTerminadaAdapter implements OrdenFabricacionTerminadaPort {

    private final OrdenFabricacionRepository ordenFabricacionRepository;

    public OrdenFabricacionTerminadaAdapter(OrdenFabricacionRepository ordenFabricacionRepository) {
        this.ordenFabricacionRepository = ordenFabricacionRepository;
    }

    @Override
    public boolean estaTerminada(UUID ordenFabricacionId) {
        if (ordenFabricacionId == null) {
            return false;
        }
        return ordenFabricacionRepository.existsByIdAndEstado(
                ordenFabricacionId, EstadoOrdenFabricacion.TERMINADA);
    }

    @Override
    public Optional<UUID> clienteDeOrden(UUID ordenFabricacionId) {
        if (ordenFabricacionId == null) {
            return Optional.empty();
        }
        return ordenFabricacionRepository.findById(ordenFabricacionId)
                .map(OrdenFabricacion::getClienteId);
    }
}

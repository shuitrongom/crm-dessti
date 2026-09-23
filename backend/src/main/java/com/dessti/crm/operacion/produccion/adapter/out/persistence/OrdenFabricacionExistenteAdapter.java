package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.produccion.application.OrdenFabricacionExistentePort;

/**
 * Adaptador de salida <strong>del Nucleo</strong> que implementa
 * {@link OrdenFabricacionExistentePort} delegando en el
 * {@link OrdenFabricacionRepository}. Expone la existencia de una Orden_Fabricacion
 * por identificador para que los verticales que la enlazan (p. ej. el
 * Levantamiento_Sitio de anuncios, Req 16.2) validen el vinculo por PUERTO, sin
 * tocar la persistencia interna del Nucleo (Req 4.5, 10.3).
 *
 * <p>La consulta ya esta acotada al tenant vigente por el filtro global de
 * Hibernate y por la RLS (Req 23), de modo que una OF de otro tenant no se
 * considera existente. Sigue el patron de {@code OrdenFabricacionTerminadaAdapter}.</p>
 */
@Component("ordenFabricacionExistenteAdapter")
public class OrdenFabricacionExistenteAdapter implements OrdenFabricacionExistentePort {

    private final OrdenFabricacionRepository ordenFabricacionRepository;

    public OrdenFabricacionExistenteAdapter(OrdenFabricacionRepository ordenFabricacionRepository) {
        this.ordenFabricacionRepository = ordenFabricacionRepository;
    }

    @Override
    public boolean existePorId(UUID ordenFabricacionId) {
        if (ordenFabricacionId == null) {
            return false;
        }
        return ordenFabricacionRepository.findById(ordenFabricacionId).isPresent();
    }
}
package com.dessti.crm.comercial.actividad.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.actividad.application.OportunidadDeClientePort;
import com.dessti.crm.comercial.oportunidad.adapter.out.persistence.OportunidadRepository;

/**
 * Adaptador de salida que implementa {@link OportunidadDeClientePort} delegando en
 * el {@link OportunidadRepository} del submodulo de Oportunidades (ambos dentro
 * del modulo comercial-crm).
 *
 * <p>La consulta {@code findById} ya esta acotada al tenant vigente por el filtro
 * global de Hibernate y por la RLS (Req 23), de modo que una Oportunidad de otro
 * tenant no se considera existente. Ademas de la existencia, se comprueba que la
 * Oportunidad pertenezca al Cliente indicado, para impedir vincular una Actividad
 * a una Oportunidad de otro Cliente. Este adaptador aisla la dependencia hacia el
 * submodulo de Oportunidades en la capa de infraestructura, dejando la aplicacion
 * de Actividades libre de acoplamiento a su persistencia.</p>
 */
@Component
public class OportunidadDeClienteAdapter implements OportunidadDeClientePort {

    private final OportunidadRepository oportunidadRepository;

    public OportunidadDeClienteAdapter(OportunidadRepository oportunidadRepository) {
        this.oportunidadRepository = oportunidadRepository;
    }

    @Override
    public boolean existeOportunidadDeCliente(UUID oportunidadId, UUID clienteId) {
        if (oportunidadId == null || clienteId == null) {
            return false;
        }
        return oportunidadRepository.findById(oportunidadId)
                .map(oportunidad -> clienteId.equals(oportunidad.getClienteId()))
                .orElse(false);
    }
}

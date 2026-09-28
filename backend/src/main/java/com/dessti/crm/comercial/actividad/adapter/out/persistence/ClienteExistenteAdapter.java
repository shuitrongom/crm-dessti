package com.dessti.crm.comercial.actividad.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.actividad.application.ClienteExistentePort;
import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;

/**
 * Adaptador de salida que implementa {@link ClienteExistentePort} delegando en el
 * {@link ClienteRepository} del submodulo de Clientes (ambos dentro del modulo
 * comercial-crm).
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate (Capa 1) y por la RLS de PostgreSQL (Capa 2),
 * de modo que un Cliente de otro tenant no se considera existente. Este adaptador
 * aisla la dependencia hacia el submodulo de Clientes en la capa de
 * infraestructura, dejando la aplicacion de Actividades libre de acoplamiento a
 * su persistencia, del mismo modo que el {@code ClienteExistenteAdapter} del
 * submodulo de Oportunidades.</p>
 */
@Component("clienteExistenteActividadAdapter")
public class ClienteExistenteAdapter implements ClienteExistentePort {

    private final ClienteRepository clienteRepository;

    public ClienteExistenteAdapter(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public boolean existeClienteActivo(UUID clienteId) {
        if (clienteId == null) {
            return false;
        }
        return clienteRepository.findByIdAndActivoTrue(clienteId).isPresent();
    }
}

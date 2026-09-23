package com.dessti.crm.comercial.oportunidad.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.oportunidad.application.ClienteExistentePort;

/**
 * Adaptador de salida que implementa {@link ClienteExistentePort} delegando en
 * el {@link ClienteRepository} del submodulo de Clientes (ambos dentro del
 * modulo comercial-crm, Req 14.1, 5).
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate y por la RLS (Req 23), de modo que un Cliente
 * de otro tenant no se considera existente. Este adaptador aisla la dependencia
 * hacia el submodulo de Clientes en la capa de infraestructura, dejando la
 * aplicacion de Oportunidades libre de acoplamiento a su persistencia.</p>
 */
@Component
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

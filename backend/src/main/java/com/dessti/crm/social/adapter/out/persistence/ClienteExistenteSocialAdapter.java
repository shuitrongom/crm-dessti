package com.dessti.crm.social.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.social.application.ClienteExistenteSocialPort;

/**
 * Adaptador de salida que implementa {@link ClienteExistenteSocialPort} delegando
 * en el {@link ClienteRepository} del submodulo de Clientes (Req 64.4, 5).
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate y por la RLS (Req 23), de modo que un Cliente
 * de otra Empresa no se considera existente. Este adaptador aisla en la capa de
 * infraestructura la dependencia de la Bandeja_Unificada hacia la persistencia de
 * Clientes, dejando la aplicacion social libre de acoplamiento.</p>
 */
@Component
public class ClienteExistenteSocialAdapter implements ClienteExistenteSocialPort {

    private final ClienteRepository clienteRepository;

    public ClienteExistenteSocialAdapter(ClienteRepository clienteRepository) {
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

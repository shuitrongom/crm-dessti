package com.dessti.crm.comercial.cliente.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.platform.security.usuarios.ClienteExistentePort;

/**
 * Adaptador de salida que implementa el {@link ClienteExistentePort} de la gestion
 * de Usuarios (Req 45), delegando en el {@link ClienteRepository} del submodulo de
 * Clientes. Permite validar, al dar de alta un Usuario del Portal, que el Cliente
 * asociado exista y este activo en el tenant, sin que {@code platform.security}
 * dependa de la persistencia de {@code comercial}.
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente por
 * el filtro global de Hibernate y la RLS (Req 23): un Cliente de otro tenant no se
 * considera existente. Replica el patron de los {@code ClienteExistenteAdapter} de
 * Cotizaciones y Oportunidades.</p>
 */
@Component("usuarioClienteExistenteAdapter")
public class UsuarioClienteExistenteAdapter implements ClienteExistentePort {

    private final ClienteRepository clienteRepository;

    public UsuarioClienteExistenteAdapter(ClienteRepository clienteRepository) {
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

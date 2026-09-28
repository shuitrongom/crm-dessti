package com.dessti.crm.comercial.cliente.adapter.out.portal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.portalcliente.application.PerfilClientePortalPort;
import com.dessti.crm.portalcliente.application.PerfilClienteResumen;

/**
 * Adaptador de salida que implementa el {@link PerfilClientePortalPort} del Portal del
 * Cliente (Req 45.1), delegando en el {@link ClienteRepository} del submodulo de
 * Clientes. Proyecta el Cliente activo del tenant a un {@link PerfilClienteResumen}
 * con solo los datos que el Cliente puede ver de si mismo. Invierte la dependencia:
 * el Portal (Nucleo) define el puerto y este adaptador del modulo comercial lo
 * implementa.
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente por
 * el filtro global de Hibernate y la RLS (Req 23): un Cliente de otro tenant no se
 * considera accesible.</p>
 */
@Component
public class PerfilClientePortalAdapter implements PerfilClientePortalPort {

    private final ClienteRepository clienteRepository;

    public PerfilClientePortalAdapter(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Optional<PerfilClienteResumen> buscarPerfil(UUID clienteId) {
        if (clienteId == null) {
            return Optional.empty();
        }
        return clienteRepository.findByIdAndActivoTrue(clienteId)
                .map(c -> new PerfilClienteResumen(
                        c.getId(),
                        c.getNombre(),
                        c.getNombreComercial(),
                        c.getRfc(),
                        c.getEmail(),
                        c.getTelefono(),
                        c.getDireccionCiudad(),
                        c.getDireccionEstado(),
                        c.getDireccionCp()));
    }
}

package com.dessti.crm.comercial.cotizacion.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.comercial.cotizacion.application.DatosClientePort;

/**
 * Adaptador de salida que implementa {@link DatosClientePort} delegando en el
 * {@link ClienteRepository} del submodulo de Clientes (mismo modulo comercial-crm,
 * Req 5, 6). Resuelve los datos visibles del Cliente (nombre, RFC, correo) para
 * el DTO, el PDF y el envio por correo de la Cotizacion (V60).
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate y por la RLS (Req 23): un Cliente de otro
 * tenant se trata como inexistente ({@link Optional#empty()}).</p>
 */
@Component("cotizacionDatosClienteAdapter")
public class DatosClienteAdapter implements DatosClientePort {

    private final ClienteRepository clienteRepository;

    public DatosClienteAdapter(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Optional<DatosCliente> buscarPorId(UUID clienteId) {
        if (clienteId == null) {
            return Optional.empty();
        }
        return clienteRepository.findByIdAndActivoTrue(clienteId)
                .map(DatosClienteAdapter::proyectar);
    }

    private static DatosCliente proyectar(Cliente cliente) {
        return new DatosCliente(
                cliente.getId(), cliente.getNombre(), cliente.getRfc(), cliente.getEmail());
    }
}

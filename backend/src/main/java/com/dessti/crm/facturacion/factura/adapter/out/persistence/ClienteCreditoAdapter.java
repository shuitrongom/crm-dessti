package com.dessti.crm.facturacion.factura.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.facturacion.factura.application.ClienteCreditoPort;

/**
 * Adaptador de salida que implementa {@link ClienteCreditoPort} delegando en el
 * {@link ClienteRepository} del modulo comercial-crm (Req 36.5). Aisla la
 * dependencia hacia el submodulo de Clientes en la capa de infraestructura,
 * dejando la aplicacion de facturacion libre de acoplamiento a su persistencia.
 * Sigue el patron de {@code CotizacionAprobadaAdapter} (mismo paquete).
 *
 * <p>La consulta {@code findById} ya esta acotada al tenant vigente por el filtro
 * global de Hibernate y por la RLS (Req 23): un Cliente de otro tenant no se
 * considera accesible (se devuelve {@link Optional#empty()}).</p>
 */
@Component("facturaClienteCreditoAdapter")
public class ClienteCreditoAdapter implements ClienteCreditoPort {

    private final ClienteRepository clienteRepository;

    public ClienteCreditoAdapter(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Optional<Integer> diasCreditoDe(UUID clienteId) {
        if (clienteId == null) {
            return Optional.empty();
        }
        return clienteRepository.findById(clienteId).map(Cliente::getDiasCredito);
    }
}

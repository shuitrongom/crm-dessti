package com.dessti.crm.facturacion.factura.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;

/**
 * Pruebas del {@link ClienteCreditoAdapter}: delega en el {@link ClienteRepository}
 * del modulo comercial. Cliente presente ⇒ {@code Optional.of(dias)}; Cliente
 * ausente o {@code clienteId} nulo ⇒ {@code Optional.empty()} (el servicio degrada
 * a contado). Sin Spring ni base de datos.
 */
@DisplayName("ClienteCreditoAdapter.diasCreditoDe (Req 36.5)")
class ClienteCreditoAdapterTest {

    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ClienteRepository clienteRepository = mock(ClienteRepository.class);
    private final ClienteCreditoAdapter adaptador = new ClienteCreditoAdapter(clienteRepository);

    @Test
    @DisplayName("cliente presente devuelve sus dias de credito")
    void clientePresente() {
        Cliente cliente = mock(Cliente.class);
        when(cliente.getDiasCredito()).thenReturn(30);
        when(clienteRepository.findById(CLIENTE)).thenReturn(Optional.of(cliente));

        assertThat(adaptador.diasCreditoDe(CLIENTE)).contains(30);
        verify(clienteRepository).findById(CLIENTE);
    }

    @Test
    @DisplayName("cliente ausente devuelve vacio")
    void clienteAusente() {
        when(clienteRepository.findById(CLIENTE)).thenReturn(Optional.empty());

        assertThat(adaptador.diasCreditoDe(CLIENTE)).isEmpty();
    }

    @Test
    @DisplayName("clienteId nulo devuelve vacio sin consultar")
    void clienteIdNulo() {
        assertThat(adaptador.diasCreditoDe(null)).isEmpty();
        verify(clienteRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }
}

package com.dessti.crm.comercial.cliente.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.adapter.out.persistence.ContactoRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias enfocadas en la unicidad del RFC entre Clientes activos
 * (Req 5.3) a nivel de {@link ServicioClientes}, con dobles de Mockito.
 *
 * <p>Complementan {@code ServicioClientesTest} sin duplicar sus casos: aqui se
 * verifica que (1) un RFC de un Cliente dado de baja logica puede reutilizarse,
 * (2) la validacion de formato del RFC (422) precede a la comprobacion de
 * unicidad (409) y (3) la deteccion de duplicado es insensible a mayusculas al
 * normalizarse el RFC antes de la consulta.</p>
 */
class ClienteRfcDuplicadoTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String RFC = "ABC120101AB1";

    private ClienteRepository clienteRepository;
    private ContactoRepository contactoRepository;
    private AuditoriaPort auditoria;
    private ServicioClientes servicio;

    @BeforeEach
    void setUp() {
        clienteRepository = mock(ClienteRepository.class);
        contactoRepository = mock(ContactoRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioClientes(clienteRepository, contactoRepository, auditoria);
        // El servicio deriva el tenant del contexto para auditar (Req 23.4).
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("crearCliente reutiliza el RFC de un Cliente dado de baja logica (Req 5.3, 5.9)")
    void rfcReutilizableTrasBajaLogica() {
        // El indice unico parcial solo cuenta activos: si no hay activo con el RFC,
        // existsByRfcAndActivoTrue devuelve false y el alta procede.
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(false);
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteDto dto = servicio.crearCliente(
                new CrearClienteCommand("Acme Renovada", RFC, "acme@example.com", null,
                        null, null, null, null, null, null, null, null, null));

        assertThat(dto.rfc()).isEqualTo(RFC);
        assertThat(dto.activo()).isTrue();
        verify(clienteRepository).saveAndFlush(any(Cliente.class));
    }

    @Test
    @DisplayName("crearCliente valida el formato del RFC (422) antes de consultar unicidad (409) (Req 5.2, 5.3)")
    void formatoInvalidoPrecedeUnicidad() {
        assertThatThrownBy(() -> servicio.crearCliente(
                new CrearClienteCommand("Acme S.A.", "1234567890AB", "acme@example.com", null,
                        null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .isNotInstanceOf(ConflictoUnicidadException.class);

        // No debe llegarse a la comprobacion de unicidad ni a la persistencia.
        verify(clienteRepository, never()).existsByRfcAndActivoTrue(any());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crearCliente rechaza con 409 un RFC en minusculas que colisiona con un activo (Req 5.3)")
    void duplicadoInsensibleAMayusculas() {
        // El RFC se normaliza a mayusculas antes de la consulta de unicidad, de modo
        // que un duplicado ingresado en minusculas se detecta como colision.
        when(clienteRepository.existsByRfcAndActivoTrue(RFC)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearCliente(
                new CrearClienteCommand("Acme S.A.", "abc120101ab1", "acme@example.com", null,
                        null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(clienteRepository).existsByRfcAndActivoTrue(RFC);
        verify(clienteRepository, never()).saveAndFlush(any());
    }
}

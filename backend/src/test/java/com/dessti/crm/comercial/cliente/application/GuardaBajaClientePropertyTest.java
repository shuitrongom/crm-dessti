package com.dessti.crm.comercial.cliente.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.comercial.cliente.adapter.out.persistence.ClienteRepository;
import com.dessti.crm.comercial.cliente.adapter.out.persistence.ContactoRepository;
import com.dessti.crm.comercial.cliente.domain.Cliente;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 8-bis: Guarda de baja
 * de Cliente con actividad comercial abierta</strong> (Req 5.10).
 *
 * <p>Para cualquier combinacion de (Oportunidades abiertas, Cotizaciones abiertas), la
 * baja logica de un Cliente activo se acepta <em>si y solo si</em> no tiene ninguna de
 * las dos; si tiene al menos una, se rechaza con {@link ReglaNegocioException} (422),
 * el Cliente se conserva activo y no se persiste. Se ejerce el servicio real
 * {@link ServicioClientes} con dobles Mockito; el {@link PipelineClientePort} devuelve
 * exactamente los valores generados.</p>
 */
class GuardaBajaClientePropertyTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String RFC = "ABC120101AB1";

    @AfterTry
    void limpiarTenant() {
        TenantContext.clear();
    }

    // Feature: crm-anuncios-luminosos, Property 8-bis: la baja de un Cliente se acepta sii no tiene Oportunidades ni Cotizaciones abiertas; en otro caso 422 y el Cliente se conserva activo.
    @Property(tries = 40)
    void bajaAceptadaSiiSinActividadAbierta(
            @ForAll boolean tieneOportunidades,
            @ForAll boolean tieneCotizaciones) {

        TenantContext.set(TENANT);
        ClienteRepository clienteRepository = mock(ClienteRepository.class);
        ContactoRepository contactoRepository = mock(ContactoRepository.class);
        UsuarioExistentePort usuarioExistente = mock(UsuarioExistentePort.class);
        PipelineClientePort pipelineCliente = mock(PipelineClientePort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        ServicioClientes servicio = new ServicioClientes(
                clienteRepository, contactoRepository, usuarioExistente, pipelineCliente, auditoria);

        Cliente cliente = Cliente.crear("Acme", RFC, "a@b.com", null, "ventas");
        when(clienteRepository.findByIdAndActivoTrue(cliente.getId())).thenReturn(Optional.of(cliente));
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pipelineCliente.clienteTieneOportunidadesAbiertas(cliente.getId()))
                .thenReturn(tieneOportunidades);
        lenient().when(pipelineCliente.clienteTieneCotizacionesAbiertas(cliente.getId()))
                .thenReturn(tieneCotizaciones);

        Throwable error = catchThrowable(() -> servicio.desactivarCliente(cliente.getId()));

        boolean debePermitirse = !tieneOportunidades && !tieneCotizaciones;
        if (debePermitirse) {
            assertThat(error).as("sin actividad abierta, la baja procede").isNull();
            assertThat(cliente.isActivo()).isFalse();
        } else {
            assertThat(error).isInstanceOf(ReglaNegocioException.class);
            assertThat(cliente.isActivo())
                    .as("con actividad abierta, el Cliente se conserva activo")
                    .isTrue();
            verify(clienteRepository, never()).save(any(Cliente.class));
        }
    }
}

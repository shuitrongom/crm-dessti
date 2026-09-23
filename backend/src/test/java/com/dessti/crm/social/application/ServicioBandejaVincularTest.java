package com.dessti.crm.social.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.social.adapter.out.meta.MetaProperties;
import com.dessti.crm.social.adapter.out.persistence.ConsentimientoCanalRepository;
import com.dessti.crm.social.adapter.out.persistence.ConversacionRepository;
import com.dessti.crm.social.adapter.out.persistence.CuentaCanalSocialRepository;
import com.dessti.crm.social.adapter.out.persistence.MensajeSocialRepository;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.Conversacion;

/**
 * Pruebas del caso de uso {@code ServicioBandeja.vincular} (tarea 4.3, Req 5.1,
 * 5.2, 64.4). Se ejercita con dobles Mockito, sin Spring ni BD: verifica que carga
 * la Conversacion, valida el Cliente del tenant via el puerto, invoca el dominio,
 * persiste y audita; y que responde 404 cuando el Cliente no existe en el tenant.
 */
class ServicioBandejaVincularTest {

    private static final UUID CONVERSACION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUENTA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ConversacionRepository conversacionRepository;
    private ClienteExistenteSocialPort clienteExistente;
    private AuditoriaPort auditoria;
    private ServicioBandeja servicio;

    @BeforeEach
    void preparar() {
        conversacionRepository = mock(ConversacionRepository.class);
        MensajeSocialRepository mensajeRepository = mock(MensajeSocialRepository.class);
        CuentaCanalSocialRepository cuentaRepository = mock(CuentaCanalSocialRepository.class);
        ConsentimientoCanalRepository consentimientoRepository = mock(ConsentimientoCanalRepository.class);
        MensajeriaSocialPort mensajeriaSocial = mock(MensajeriaSocialPort.class);
        CaptacionLeadPort captacionLead = mock(CaptacionLeadPort.class);
        clienteExistente = mock(ClienteExistenteSocialPort.class);
        auditoria = mock(AuditoriaPort.class);
        MetaProperties propiedades = mock(MetaProperties.class);

        servicio = new ServicioBandeja(conversacionRepository, mensajeRepository, cuentaRepository,
                consentimientoRepository, mensajeriaSocial, captacionLead, clienteExistente,
                auditoria, Clock.systemUTC(), propiedades);

        TenantContext.set(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Conversacion conversacionAbierta() {
        return Conversacion.abrir(CUENTA, CanalSocial.WHATSAPP, "5215500000000", null, null, "sistema");
    }

    @Test
    void vincular_validaClienteInvocaDominioPersisteYAudita() {
        Conversacion conversacion = conversacionAbierta();
        when(conversacionRepository.findById(CONVERSACION)).thenReturn(Optional.of(conversacion));
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(true);
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversacionDto dto = servicio.vincular(CONVERSACION, CLIENTE);

        assertThat(dto.clienteId()).isEqualTo(CLIENTE);
        verify(clienteExistente).existeClienteActivo(CLIENTE);
        verify(conversacionRepository).save(conversacion);
        verify(auditoria).registrar(any());
    }

    @Test
    void vincular_lanza404_cuandoClienteNoExisteEnTenant() {
        Conversacion conversacion = conversacionAbierta();
        when(conversacionRepository.findById(CONVERSACION)).thenReturn(Optional.of(conversacion));
        when(clienteExistente.existeClienteActivo(CLIENTE)).thenReturn(false);

        assertThatThrownBy(() -> servicio.vincular(CONVERSACION, CLIENTE))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Cliente");

        verify(conversacionRepository, never()).save(any(Conversacion.class));
    }

    @Test
    void vincular_lanza404_cuandoConversacionNoAccesible() {
        when(conversacionRepository.findById(CONVERSACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.vincular(CONVERSACION, CLIENTE))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(clienteExistente, never()).existeClienteActivo(eq(CLIENTE));
    }
}

package com.dessti.crm.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pruebas unitarias de la deteccion de patrones y emision de alertas de
 * auditoria (Req 10.11). Usan repositorios y notificador simulados (Mockito):
 * verifican que al superar el umbral en la ventana se dispara la Notificacion,
 * y que por debajo del umbral no se emite.
 */
class ServicioAlertasAuditoriaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RegistroAuditoriaView eventoAccesoDenegado() {
        return new RegistroAuditoriaView(
                10L, TENANT, "juan", PatronAlerta.ACCESOS_DENEGADOS.accionAsociada(),
                "sesion", "detalle", null, null, "trace", Instant.now(),
                "0".repeat(64), "a".repeat(64));
    }

    private AlertaAuditoria reglaAccesosDenegados(int umbral) {
        return new AlertaAuditoria(
                TENANT, PatronAlerta.ACCESOS_DENEGADOS, umbral,
                Duration.ofMinutes(15), "seguridad@empresa.com", true);
    }

    @Test
    @DisplayName("Supera el umbral en la ventana -> dispara la Notificacion")
    void disparaCuandoSuperaUmbral() {
        AlertaAuditoriaRepository alertaRepo = mock(AlertaAuditoriaRepository.class);
        RegistroAuditoriaRepository registroRepo = mock(RegistroAuditoriaRepository.class);
        NotificadorAlertasPort notificador = mock(NotificadorAlertasPort.class);

        when(alertaRepo.buscarActivasPorPatron(eq(TENANT), eq(PatronAlerta.ACCESOS_DENEGADOS)))
                .thenReturn(List.of(reglaAccesosDenegados(5)));
        when(registroRepo.contarPorAccionEnVentana(
                eq(TENANT), eq(PatronAlerta.ACCESOS_DENEGADOS.accionAsociada()), any(Instant.class)))
                .thenReturn(6L); // >= umbral (5)

        ServicioAlertasAuditoria servicio =
                new ServicioAlertasAuditoria(alertaRepo, registroRepo, notificador);

        List<AlertaDisparada> disparadas = servicio.evaluar(eventoAccesoDenegado());

        assertThat(disparadas).hasSize(1);
        ArgumentCaptor<AlertaDisparada> captor = ArgumentCaptor.forClass(AlertaDisparada.class);
        verify(notificador).notificar(captor.capture());
        AlertaDisparada alerta = captor.getValue();
        assertThat(alerta.patron()).isEqualTo(PatronAlerta.ACCESOS_DENEGADOS);
        assertThat(alerta.conteoObservado()).isEqualTo(6L);
        assertThat(alerta.umbral()).isEqualTo(5);
        assertThat(alerta.destinatarios()).isEqualTo("seguridad@empresa.com");
    }

    @Test
    @DisplayName("Por debajo del umbral -> no dispara Notificacion")
    void noDisparaPorDebajoDelUmbral() {
        AlertaAuditoriaRepository alertaRepo = mock(AlertaAuditoriaRepository.class);
        RegistroAuditoriaRepository registroRepo = mock(RegistroAuditoriaRepository.class);
        NotificadorAlertasPort notificador = mock(NotificadorAlertasPort.class);

        when(alertaRepo.buscarActivasPorPatron(eq(TENANT), eq(PatronAlerta.ACCESOS_DENEGADOS)))
                .thenReturn(List.of(reglaAccesosDenegados(5)));
        when(registroRepo.contarPorAccionEnVentana(
                eq(TENANT), eq(PatronAlerta.ACCESOS_DENEGADOS.accionAsociada()), any(Instant.class)))
                .thenReturn(3L); // < umbral (5)

        ServicioAlertasAuditoria servicio =
                new ServicioAlertasAuditoria(alertaRepo, registroRepo, notificador);

        List<AlertaDisparada> disparadas = servicio.evaluar(eventoAccesoDenegado());

        assertThat(disparadas).isEmpty();
        verify(notificador, never()).notificar(any());
    }

    @Test
    @DisplayName("Accion sin patron asociado no consulta reglas ni notifica")
    void accionSinPatronNoHaceNada() {
        AlertaAuditoriaRepository alertaRepo = mock(AlertaAuditoriaRepository.class);
        RegistroAuditoriaRepository registroRepo = mock(RegistroAuditoriaRepository.class);
        NotificadorAlertasPort notificador = mock(NotificadorAlertasPort.class);

        ServicioAlertasAuditoria servicio =
                new ServicioAlertasAuditoria(alertaRepo, registroRepo, notificador);

        RegistroAuditoriaView eventoNeutro = new RegistroAuditoriaView(
                1L, TENANT, "juan", "crear", "cliente", null, null, null,
                "trace", Instant.now(), "0".repeat(64), "b".repeat(64));

        List<AlertaDisparada> disparadas = servicio.evaluar(eventoNeutro);

        assertThat(disparadas).isEmpty();
        verify(notificador, never()).notificar(any());
    }
}

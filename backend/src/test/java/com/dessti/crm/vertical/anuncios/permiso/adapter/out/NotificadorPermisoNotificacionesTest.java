package com.dessti.crm.vertical.anuncios.permiso.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.notificaciones.application.NotificacionDto;
import com.dessti.crm.notificaciones.application.NotificacionPort;
import com.dessti.crm.notificaciones.application.SolicitudNotificacion;
import com.dessti.crm.notificaciones.domain.CanalNotificacion;
import com.dessti.crm.notificaciones.domain.TipoEventoNotificacion;
import com.dessti.crm.vertical.anuncios.permiso.application.DestinatarioPermisoPort;
import com.dessti.crm.vertical.anuncios.permiso.application.NotificacionVencimientoPermiso;

/**
 * Pruebas unitarias del adaptador real {@link NotificadorPermisoNotificaciones}
 * (Tarea 8.4; Req 13.2, 13.3). Usan dobles de Mockito; no arrancan Spring ni base
 * de datos.
 *
 * <p>Cubren:</p>
 * <ul>
 *   <li><strong>N permisos por vencer -&gt; N notificaciones:</strong> por cada
 *       {@link NotificacionVencimientoPermiso} con destinatario resoluble se
 *       construye y envia una {@link SolicitudNotificacion} con evento
 *       {@link TipoEventoNotificacion#PERMISO_POR_VENCER}, canal
 *       {@link CanalNotificacion#CORREO}, {@code esMarketing=false}, recurso
 *       {@code permiso_instalacion} y contenido que incluye Sitio, tipo, fecha de
 *       vencimiento y dias restantes (Req 13.2, 13.3).</li>
 *   <li><strong>Omision sin romper el barrido:</strong> si el
 *       {@link DestinatarioPermisoPort} no resuelve correo, la notificacion se
 *       omite sin invocar el {@link NotificacionPort} y sin lanzar excepcion, para
 *       no abortar {@code ServicioPermisos.notificarVencimientosProximos()}.</li>
 * </ul>
 */
class NotificadorPermisoNotificacionesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CORREO = "responsable@empresa.mx";

    private NotificacionVencimientoPermiso permiso(String tipo, LocalDate vence, long dias) {
        return new NotificacionVencimientoPermiso(
                TENANT, UUID.randomUUID(), SITIO, tipo, vence, dias);
    }

    /**
     * Construye un {@link NotificacionDto} minimo para el retorno del puerto (el
     * adaptador bajo prueba ignora el valor de retorno). {@link NotificacionDto} es
     * un record (final) y no puede mockearse.
     */
    private NotificacionDto notificacionDto() {
        Instant ahora = Instant.parse("2024-05-01T12:00:00Z");
        return new NotificacionDto(
                UUID.randomUUID(), "permiso_por_vencer", "correo", CORREO,
                "Permiso por vencer", "contenido", false, "permiso_instalacion",
                UUID.randomUUID(), "enviada", null, ahora, ahora, 0L, ahora, ahora);
    }

    @Test
    @DisplayName("N permisos por vencer producen N notificaciones bien formadas (Req 13.2, 13.3)")
    void nPermisosNNotificaciones() {
        NotificacionPort notificacionPort = mock(NotificacionPort.class);
        DestinatarioPermisoPort destinatarioPort = mock(DestinatarioPermisoPort.class);
        when(notificacionPort.notificar(any(SolicitudNotificacion.class)))
                .thenReturn(notificacionDto());
        when(destinatarioPort.resolverCorreo(any(NotificacionVencimientoPermiso.class)))
                .thenReturn(Optional.of(CORREO));

        NotificadorPermisoNotificaciones notificador =
                new NotificadorPermisoNotificaciones(notificacionPort, destinatarioPort);

        List<NotificacionVencimientoPermiso> porVencer = List.of(
                permiso("municipal", LocalDate.of(2024, 5, 21), 20L),
                permiso("arrendador", LocalDate.of(2024, 5, 10), 9L),
                permiso("municipal", LocalDate.of(2024, 5, 31), 30L));

        // N permisos por vencer -> N notificaciones (uno por cada uno).
        porVencer.forEach(notificador::notificarVencimientoProximo);

        ArgumentCaptor<SolicitudNotificacion> captor =
                ArgumentCaptor.forClass(SolicitudNotificacion.class);
        verify(notificacionPort, times(porVencer.size())).notificar(captor.capture());

        List<SolicitudNotificacion> enviadas = captor.getAllValues();
        assertThat(enviadas).hasSize(porVencer.size());
        for (int i = 0; i < enviadas.size(); i++) {
            SolicitudNotificacion s = enviadas.get(i);
            NotificacionVencimientoPermiso origen = porVencer.get(i);
            assertThat(s.eventoOrigen()).isEqualTo(TipoEventoNotificacion.PERMISO_POR_VENCER);
            assertThat(s.canalPreferido()).isEqualTo(CanalNotificacion.CORREO);
            assertThat(s.destinatario()).isEqualTo(CORREO);
            assertThat(s.esMarketing()).isFalse();
            assertThat(s.referenciaTipo()).isEqualTo("permiso_instalacion");
            assertThat(s.referenciaId()).isEqualTo(origen.permisoId());
            // El contenido incluye Sitio, tipo, fecha de vencimiento y dias restantes (Req 13.3).
            assertThat(s.contenido())
                    .contains(SITIO.toString())
                    .contains(origen.tipo())
                    .contains(origen.fechaVencimiento().toString())
                    .contains(String.valueOf(origen.diasParaVencer()));
        }
    }

    @Test
    @DisplayName("sin destinatario resoluble omite la notificacion sin invocar el puerto ni lanzar (Req 13.2)")
    void sinDestinatarioOmiteSinRomper() {
        NotificacionPort notificacionPort = mock(NotificacionPort.class);
        DestinatarioPermisoPort destinatarioPort = mock(DestinatarioPermisoPort.class);
        when(destinatarioPort.resolverCorreo(any(NotificacionVencimientoPermiso.class)))
                .thenReturn(Optional.empty());

        NotificadorPermisoNotificaciones notificador =
                new NotificadorPermisoNotificaciones(notificacionPort, destinatarioPort);

        NotificacionVencimientoPermiso sinDestino =
                permiso("municipal", LocalDate.of(2024, 5, 21), 20L);

        // No lanza: el barrido no debe abortarse por un permiso sin destinatario.
        assertThatCode(() -> notificador.notificarVencimientoProximo(sinDestino))
                .doesNotThrowAnyException();
        verify(notificacionPort, never()).notificar(any());
    }

    @Test
    @DisplayName("un permiso sin destinatario no impide notificar los demas del barrido (Req 13.2)")
    void omisionNoAbortaElBarrido() {
        NotificacionPort notificacionPort = mock(NotificacionPort.class);
        DestinatarioPermisoPort destinatarioPort = mock(DestinatarioPermisoPort.class);
        when(notificacionPort.notificar(any(SolicitudNotificacion.class)))
                .thenReturn(notificacionDto());

        NotificacionVencimientoPermiso conDestino =
                permiso("municipal", LocalDate.of(2024, 5, 21), 20L);
        NotificacionVencimientoPermiso sinDestino =
                permiso("arrendador", LocalDate.of(2024, 5, 10), 9L);

        when(destinatarioPort.resolverCorreo(conDestino)).thenReturn(Optional.of(CORREO));
        when(destinatarioPort.resolverCorreo(sinDestino)).thenReturn(Optional.empty());

        NotificadorPermisoNotificaciones notificador =
                new NotificadorPermisoNotificaciones(notificacionPort, destinatarioPort);

        // Simula el barrido: uno sin destinatario en medio de otros con destinatario.
        List<NotificacionVencimientoPermiso> barrido = new ArrayList<>();
        barrido.add(conDestino);
        barrido.add(sinDestino);
        barrido.add(conDestino);

        assertThatCode(() -> barrido.forEach(notificador::notificarVencimientoProximo))
                .doesNotThrowAnyException();

        // Solo los dos con destinatario producen envio.
        verify(notificacionPort, times(2)).notificar(any(SolicitudNotificacion.class));
    }
}

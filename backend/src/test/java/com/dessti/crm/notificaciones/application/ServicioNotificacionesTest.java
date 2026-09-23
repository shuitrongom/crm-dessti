package com.dessti.crm.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.notificaciones.adapter.out.canal.NotificadorCorreoRegistroLog;
import com.dessti.crm.notificaciones.adapter.out.persistence.IntentoEnvioNotificacionRepository;
import com.dessti.crm.notificaciones.adapter.out.persistence.NotificacionRepository;
import com.dessti.crm.notificaciones.domain.CanalNotificacion;
import com.dessti.crm.notificaciones.domain.Notificacion;
import com.dessti.crm.notificaciones.domain.TipoEventoNotificacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioNotificaciones} (Req 46.3, 46.7). Usan dobles
 * de Mockito; no arrancan Spring ni base de datos. Cubren:
 * <ul>
 *   <li>Reintentos hasta agotar la politica y marca {@code fallida}, con un
 *       {@link com.dessti.crm.notificaciones.domain.IntentoEnvioNotificacion} por
 *       intento (Req 46.3).</li>
 *   <li>Omision de una Notificacion de marketing por Canal_Social sin Opt_In
 *       vigente (estado {@code omitida}) sin invocar ningun envio (Req 46.7).</li>
 *   <li>Envio exitoso al primer intento por correo (Req 46.1, 46.3).</li>
 * </ul>
 */
class ServicioNotificacionesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private NotificacionRepository notificacionRepository;
    private IntentoEnvioNotificacionRepository intentoRepository;
    private NotificadorCorreoPort notificadorCorreo;
    private NotificadorWhatsappPort notificadorWhatsapp;
    private NotificadorSocialPort notificadorSocial;
    private ConsentimientoPort consentimiento;
    private AuditoriaPort auditoria;
    private Clock clock;

    @BeforeEach
    void setUp() {
        notificacionRepository = mock(NotificacionRepository.class);
        intentoRepository = mock(IntentoEnvioNotificacionRepository.class);
        notificadorCorreo = mock(NotificadorCorreoPort.class);
        notificadorWhatsapp = mock(NotificadorWhatsappPort.class);
        notificadorSocial = mock(NotificadorSocialPort.class);
        consentimiento = mock(ConsentimientoPort.class);
        auditoria = mock(AuditoriaPort.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        // Las escrituras devuelven la propia entidad para inspeccionar su estado.
        when(notificacionRepository.saveAndFlush(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ServicioNotificaciones servicioConMaxIntentos(int maxIntentos) {
        ReintentosProperties reintentos = new ReintentosProperties(maxIntentos, 0L);
        return new ServicioNotificaciones(
                notificacionRepository, intentoRepository, notificadorCorreo, notificadorWhatsapp,
                notificadorSocial, consentimiento, reintentos, auditoria, clock);
    }

    @Test
    @DisplayName("envio que siempre falla agota los reintentos, registra cada intento y marca fallida (Req 46.3)")
    void reintentaYFalla() {
        when(notificadorCorreo.enviarCorreo(any(MensajeNotificacion.class)))
                .thenReturn(ResultadoEnvio.fallido("proveedor no disponible"));
        ServicioNotificaciones servicio = servicioConMaxIntentos(3);

        SolicitudNotificacion solicitud = new SolicitudNotificacion(
                TipoEventoNotificacion.FACTURA_TIMBRADA, CanalNotificacion.CORREO,
                "cliente@example.com", "Factura timbrada", "Su factura fue timbrada.",
                false, "factura", UUID.randomUUID());

        NotificacionDto dto = servicio.notificar(solicitud);

        assertThat(dto.estado()).isEqualTo("fallida");
        assertThat(dto.enviadaEn()).isNull();
        // Un intento por cada uno de los 3 reintentos (Req 46.3).
        verify(notificadorCorreo, times(3)).enviarCorreo(any(MensajeNotificacion.class));
        verify(intentoRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("marketing por Canal_Social sin Opt_In se omite sin enviar y registra el motivo (Req 46.7)")
    void omiteMarketingSinOptIn() {
        when(consentimiento.tieneOptInVigente(eq(CanalNotificacion.WHATSAPP), any()))
                .thenReturn(false);
        ServicioNotificaciones servicio = servicioConMaxIntentos(3);

        SolicitudNotificacion solicitud = new SolicitudNotificacion(
                TipoEventoNotificacion.FACTURA_TIMBRADA, CanalNotificacion.WHATSAPP,
                "+521234567890", null, "Promocion del mes.",
                true, null, null);

        NotificacionDto dto = servicio.notificar(solicitud);

        assertThat(dto.estado()).isEqualTo("omitida");
        assertThat(dto.motivoOmision()).contains("Opt_In");
        // No se realiza ningun envio ni se registra intento alguno (Req 46.7).
        verify(notificadorSocial, never()).enviarPorCanalSocial(any(), any());
        verify(notificadorWhatsapp, never()).enviarWhatsapp(any());
        verify(intentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("envio de correo exitoso al primer intento marca enviada con enviada_en (Req 46.1, 46.3)")
    void envioExitosoPrimerIntento() {
        // Adaptador real de registro por defecto: siempre exito.
        NotificadorCorreoPort correoLog = new NotificadorCorreoRegistroLog();
        ServicioNotificaciones servicio = new ServicioNotificaciones(
                notificacionRepository, intentoRepository, correoLog, notificadorWhatsapp,
                notificadorSocial, consentimiento, new ReintentosProperties(3, 0L), auditoria, clock);

        SolicitudNotificacion solicitud = new SolicitudNotificacion(
                TipoEventoNotificacion.NOMINA_TIMBRADA, CanalNotificacion.CORREO,
                "empleado@example.com", "Nomina timbrada", "Su recibo esta disponible.",
                false, "nomina", UUID.randomUUID());

        NotificacionDto dto = servicio.notificar(solicitud);

        assertThat(dto.estado()).isEqualTo("enviada");
        assertThat(dto.enviadaEn()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        // Un unico intento exitoso registrado.
        verify(intentoRepository, times(1)).save(any());
    }
}

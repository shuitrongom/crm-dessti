package com.dessti.crm.platform.respaldo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.respaldo.adapter.out.persistence.RespaldoRepository;
import com.dessti.crm.platform.respaldo.domain.EstadoRespaldo;
import com.dessti.crm.platform.respaldo.domain.Respaldo;
import com.dessti.crm.platform.respaldo.domain.TipoRespaldo;

/**
 * Pruebas unitarias de {@link ServicioRespaldo} (Tarea 49.2, Req 50):
 * <ul>
 *   <li>Un respaldo exitoso vuelca, cifra, completa la bitacora y audita
 *       ({@code respaldo:ejecutar}).</li>
 *   <li>Un fallo del motor marca la bitacora {@code FALLIDO}, audita y propaga
 *       una {@link RespaldoException} controlada.</li>
 *   <li>Restaurar desde un respaldo inexistente da 404.</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers: los puertos (motor,
 * cifrador), el repositorio y la auditoria se sustituyen por dobles de Mockito.
 * No se ejecuta ningun proceso externo.</p>
 */
class ServicioRespaldoTest {

    private RespaldoRepository respaldoRepository;
    private MotorRespaldoPort motor;
    private CifradorRespaldoPort cifrador;
    private AuditoriaPort auditoria;
    private ServicioRespaldo servicio;

    @BeforeEach
    void preparar(@TempDir Path dir) {
        respaldoRepository = mock(RespaldoRepository.class);
        motor = mock(MotorRespaldoPort.class);
        cifrador = mock(CifradorRespaldoPort.class);
        auditoria = mock(AuditoriaPort.class);
        RespaldoProperties propiedades = new RespaldoProperties(
                false, null, dir.toString(), Duration.ofHours(24), Duration.ofHours(4),
                null, null, "localhost", 5432, "crm", "u", "p", Duration.ofHours(1));
        servicio = new ServicioRespaldo(respaldoRepository, motor, cifrador, auditoria, propiedades);

        // saveAndFlush / save devuelven la misma entidad recibida.
        when(respaldoRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(respaldoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Un respaldo exitoso completa la bitacora y audita ejecutar (Req 50.1, 50.4)")
    void respaldoExitosoCompletaYAudita() {
        when(cifrador.cifrar(any(), any()))
                .thenReturn(new CifradorRespaldoPort.ArtefactoCifrado("v1", "abc123", 1024L));

        RespaldoDto dto = servicio.ejecutarRespaldo();

        assertThat(dto.tipo()).isEqualTo(TipoRespaldo.RESPALDO.valor());
        assertThat(dto.estado()).isEqualTo(EstadoRespaldo.COMPLETADO.valor());
        assertThat(dto.aliasLlave()).isEqualTo("v1");
        assertThat(dto.tamanoBytes()).isEqualTo(1024L);

        verify(motor).volcar(any(Path.class));
        verify(cifrador).cifrar(any(Path.class), any(Path.class));

        // Se audita como evento de plataforma con recurso 'respaldo' y accion 'ejecutar'.
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.recurso()).isEqualTo("respaldo");
        assertThat(evento.accion()).isEqualTo("ejecutar");
        assertThat(evento.tenantId()).isEmpty();
    }

    @Test
    @DisplayName("Si el motor falla, la bitacora queda FALLIDO, se audita y se propaga RespaldoException")
    void respaldoFallidoSeMarcaYAudita() {
        doThrow(new RespaldoException("motor caido")).when(motor).volcar(any());

        assertThatThrownBy(() -> servicio.ejecutarRespaldo())
                .isInstanceOf(RespaldoException.class);

        // La entidad se persiste al iniciar (EN_PROCESO) y al marcar fallida.
        ArgumentCaptor<Respaldo> captor = ArgumentCaptor.forClass(Respaldo.class);
        verify(respaldoRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoRespaldo.FALLIDO);

        // Se audita el fallo.
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("Restaurar desde un respaldo inexistente da 404 (RecursoNoEncontrado)")
    void restaurarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(respaldoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restaurar(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Restaurar exige que el respaldo de origen este COMPLETADO")
    void restaurarExigeRespaldoCompletado() {
        UUID id = UUID.randomUUID();
        // Respaldo en estado EN_PROCESO (no utilizable para restaurar).
        Respaldo enProceso = Respaldo.iniciar(TipoRespaldo.RESPALDO, "completo", "super_admin");
        when(respaldoRepository.findById(id)).thenReturn(Optional.of(enProceso));

        assertThatThrownBy(() -> servicio.restaurar(id))
                .isInstanceOf(RespaldoException.class);
    }
}

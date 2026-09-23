package com.dessti.crm.calidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dessti.crm.calidad.adapter.out.persistence.AccionCorrectivaRepository;
import com.dessti.crm.calidad.adapter.out.persistence.NoConformidadRepository;
import com.dessti.crm.calidad.domain.AccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoAccionCorrectiva;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias (Mockito) del {@link ServicioNoConformidades}, centradas en el flujo
 * de la Accion_Correctiva y su guarda de cierre (Property 43, Req 70.2). Se mockean el
 * repositorio y la auditoria; el {@link TenantContext} se fija por prueba para las
 * llamadas de auditoria.
 */
class ServicioNoConformidadesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);

    private NoConformidadRepository noConformidadRepository;
    private AccionCorrectivaRepository accionCorrectivaRepository;
    private AuditoriaPort auditoria;
    private ServicioNoConformidades servicio;

    @BeforeEach
    void setUp() {
        noConformidadRepository = mock(NoConformidadRepository.class);
        accionCorrectivaRepository = mock(AccionCorrectivaRepository.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioNoConformidades(
                noConformidadRepository, accionCorrectivaRepository, auditoria, RELOJ);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static AccionCorrectiva enVerificacion(boolean eficaciaVerificada) {
        AccionCorrectiva accion = AccionCorrectiva.abrir(
                null, UUID.randomUUID(), "causa", "acciones", "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_ANALISIS, "actor");
        accion.avanzar(EstadoAccionCorrectiva.EN_EJECUCION, "actor");
        accion.avanzar(EstadoAccionCorrectiva.VERIFICACION, "actor");
        if (eficaciaVerificada) {
            accion.verificarEficacia("evidencia", "actor");
        }
        return accion;
    }

    @Test
    void cerrarConEficaciaVerificadaPersisteYAudita() {
        AccionCorrectiva accion = enVerificacion(true);
        UUID id = accion.getId();
        when(accionCorrectivaRepository.findById(id)).thenReturn(Optional.of(accion));
        when(accionCorrectivaRepository.save(any(AccionCorrectiva.class))).thenAnswer(inv -> inv.getArgument(0));

        AccionCorrectivaDto dto = servicio.cerrarAccionCorrectiva(id);

        assertThat(dto.estado()).isEqualTo("cerrada");
        verify(accionCorrectivaRepository).save(accion);
        verify(auditoria).registrar(any());
    }

    @Test
    void cerrarSinEficaciaVerificadaSeRechazaYNoPersiste() {
        AccionCorrectiva accion = enVerificacion(false);
        UUID id = accion.getId();
        when(accionCorrectivaRepository.findById(id)).thenReturn(Optional.of(accion));

        assertThatThrownBy(() -> servicio.cerrarAccionCorrectiva(id))
                .isInstanceOf(ReglaNegocioException.class);
        verify(accionCorrectivaRepository, never()).save(any());
    }

    @Test
    void cerrarAccionInexistenteDevuelve404YAuditaAccesoCruzado() {
        UUID id = UUID.randomUUID();
        when(accionCorrectivaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cerrarAccionCorrectiva(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(auditoria).registrar(any());
        verify(accionCorrectivaRepository, never()).save(any());
    }

    @Test
    void abrirAccionConNoConformidadInexistenteDevuelve404() {
        UUID noConformidadId = UUID.randomUUID();
        when(noConformidadRepository.findById(noConformidadId)).thenReturn(Optional.empty());

        AbrirAccionCorrectivaCommand comando = new AbrirAccionCorrectivaCommand(
                noConformidadId, UUID.randomUUID(), "causa", "acciones");

        assertThatThrownBy(() -> servicio.abrirAccionCorrectiva(comando))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(accionCorrectivaRepository, never()).save(any());
    }
}

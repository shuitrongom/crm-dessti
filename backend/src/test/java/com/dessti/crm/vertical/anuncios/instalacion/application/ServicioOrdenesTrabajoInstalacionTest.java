package com.dessti.crm.vertical.anuncios.instalacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.operacion.produccion.application.OrdenFabricacionTerminadaPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.EvidenciaInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.OrdenTrabajoInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.PendienteInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EvidenciaInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.PendienteInstalacion;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;

/**
 * Pruebas unitarias de {@link ServicioOrdenesTrabajoInstalacion} (tarea 6.5; Req
 * 8.1, 8.2, 8.3, 8.4, 8.6, 14.1). Usan dobles de Mockito; no arrancan Spring ni
 * base de datos, siguiendo el patron de los tests de servicio del modulo. Cubren:
 * consulta de detalle con pendientes/evidencias (con y sin elementos), endpoints
 * dedicados de pendientes y evidencias, resolucion de un pendiente via registro de
 * avance (marca {@code resuelto} + auditoria), la guarda de cierre (422 informativo
 * que enumera las descripciones de los pendientes sin resolver; 409 si la
 * transicion no es valida) y 404 cuando la OTI no es accesible.
 */
class ServicioOrdenesTrabajoInstalacionTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private OrdenTrabajoInstalacionRepository ordenRepository;
    private PendienteInstalacionRepository pendienteRepository;
    private EvidenciaInstalacionRepository evidenciaRepository;
    private OrdenFabricacionTerminadaPort ordenFabricacionTerminada;
    private LevantamientoCompletadoPort levantamientoCompletado;
    private PermisoAprobadoPort permisoAprobado;
    private AuditoriaPort auditoria;
    private ServicioOrdenesTrabajoInstalacion servicio;

    @BeforeEach
    void setUp() {
        ordenRepository = mock(OrdenTrabajoInstalacionRepository.class);
        pendienteRepository = mock(PendienteInstalacionRepository.class);
        evidenciaRepository = mock(EvidenciaInstalacionRepository.class);
        ordenFabricacionTerminada = mock(OrdenFabricacionTerminadaPort.class);
        levantamientoCompletado = mock(LevantamientoCompletadoPort.class);
        permisoAprobado = mock(PermisoAprobadoPort.class);
        auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioOrdenesTrabajoInstalacion(
                ordenRepository, pendienteRepository, evidenciaRepository,
                ordenFabricacionTerminada, levantamientoCompletado, permisoAprobado, auditoria);
        TenantContext.set(TENANT);
        when(ordenRepository.save(any(OrdenTrabajoInstalacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @AfterEach
    void limpiarContexto() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades de montaje
    // ------------------------------------------------------------------

    private static OrdenTrabajoInstalacion otiEn(EstadoOrdenTrabajoInstalacion estado) {
        OrdenTrabajoInstalacion orden = OrdenTrabajoInstalacion.programar(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2025, 1, 20), "actor-prueba");
        if (estado == EstadoOrdenTrabajoInstalacion.EN_CURSO) {
            orden.cambiarEstado(EstadoOrdenTrabajoInstalacion.EN_CURSO, "actor-prueba");
        } else if (estado != EstadoOrdenTrabajoInstalacion.PROGRAMADA) {
            throw new IllegalArgumentException(
                    "Solo se construyen OTI en 'programada' o 'en_curso' via transiciones legales.");
        }
        return orden;
    }

    private static PendienteInstalacion pendiente(OrdenTrabajoInstalacion orden, String descripcion,
                                                  boolean resuelto) {
        PendienteInstalacion pendiente = PendienteInstalacion.paraOrden(orden, descripcion, "actor-prueba");
        if (resuelto) {
            pendiente.resolver("actor-prueba");
        }
        return pendiente;
    }

    // ------------------------------------------------------------------
    // Consulta de detalle (Req 8.1, 8.2, 8.3)
    // ------------------------------------------------------------------

    @Test
    void consultarDetalle_devuelvePendientesYEvidencias() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of(pendiente(orden, "fijar anclas", false)));
        when(evidenciaRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of(EvidenciaInstalacion.paraOrden(orden, "fotos/1.jpg", "actor-prueba")));

        OrdenTrabajoInstalacionDetalleDto dto = servicio.consultarDetalle(orden.getId());

        assertThat(dto.pendientes()).hasSize(1);
        assertThat(dto.pendientes().get(0).descripcion()).isEqualTo("fijar anclas");
        assertThat(dto.pendientes().get(0).resuelto()).isFalse();
        assertThat(dto.evidencias()).hasSize(1);
        assertThat(dto.evidencias().get(0).url()).isEqualTo("fotos/1.jpg");
    }

    @Test
    void consultarDetalle_devuelveListasVacias_cuandoNoHayElementos() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.PROGRAMADA);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of());
        when(evidenciaRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of());

        OrdenTrabajoInstalacionDetalleDto dto = servicio.consultarDetalle(orden.getId());

        assertThat(dto.pendientes()).isEmpty();
        assertThat(dto.evidencias()).isEmpty();
    }

    @Test
    void consultarDetalle_lanza404_cuandoOtiNoAccesible() {
        UUID desconocida = UUID.randomUUID();
        when(ordenRepository.findById(desconocida)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarDetalle(desconocida))
                .isInstanceOf(RecursoNoEncontradoException.class);
        // 404 + auditoria del acceso cruzado (Req 8.6, 23.3).
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    // ------------------------------------------------------------------
    // Endpoints dedicados (Req 8.2, 8.3, 8.6)
    // ------------------------------------------------------------------

    @Test
    void pendientesDe_devuelveListaConBanderaResuelto() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of(pendiente(orden, "fijar anclas", false),
                        pendiente(orden, "conectar acometida", true)));

        List<PendienteInstalacionDto> pendientes = servicio.pendientesDe(orden.getId());

        assertThat(pendientes).hasSize(2);
        assertThat(pendientes.get(0).resuelto()).isFalse();
        assertThat(pendientes.get(1).resuelto()).isTrue();
    }

    @Test
    void pendientesDe_devuelveVacia_cuandoNoHayPendientes() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.PROGRAMADA);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of());

        assertThat(servicio.pendientesDe(orden.getId())).isEmpty();
    }

    @Test
    void pendientesDe_lanza404_cuandoOtiNoAccesible() {
        UUID desconocida = UUID.randomUUID();
        when(ordenRepository.findById(desconocida)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.pendientesDe(desconocida))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void evidenciasDe_devuelveLista() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(evidenciaRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of(EvidenciaInstalacion.paraOrden(orden, "fotos/1.jpg", "actor-prueba")));

        List<EvidenciaInstalacionDto> evidencias = servicio.evidenciasDe(orden.getId());

        assertThat(evidencias).hasSize(1);
        assertThat(evidencias.get(0).url()).isEqualTo("fotos/1.jpg");
    }

    @Test
    void evidenciasDe_devuelveVacia_cuandoNoHayEvidencias() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.PROGRAMADA);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(evidenciaRepository.findByOrdenTrabajoInstalacionIdOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of());

        assertThat(servicio.evidenciasDe(orden.getId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // Resolucion de un pendiente via registro de avance (Req 8.2)
    // ------------------------------------------------------------------

    @Test
    void registrarAvance_marcaPendienteComoResuelto_yAudita() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        PendienteInstalacion pendiente = pendiente(orden, "fijar anclas", false);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository.findById(pendiente.getId())).thenReturn(Optional.of(pendiente));

        servicio.registrarAvance(orden.getId(),
                new RegistrarAvanceCommand(null, null, List.of(pendiente.getId())));

        // El pendiente queda marcado como resuelto y se persiste.
        assertThat(pendiente.isResuelto()).isTrue();
        verify(pendienteRepository).save(pendiente);
        // Se audita la operacion de avance (Req 19.8).
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    // ------------------------------------------------------------------
    // Guarda de cierre (Req 8.4)
    // ------------------------------------------------------------------

    @Test
    void cambiarEstado_lanza422_yEnumeraLosPendientesNoResueltos() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository
                .findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of(pendiente(orden, "fijar anclas", false),
                        pendiente(orden, "conectar acometida", false)));

        assertThatThrownBy(() -> servicio.cambiarEstado(orden.getId(), "completada"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("fijar anclas")
                .hasMessageContaining("conectar acometida");
        // El estado se conserva y no se persiste el cambio.
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        verify(ordenRepository, never()).save(any(OrdenTrabajoInstalacion.class));
    }

    @Test
    void cambiarEstado_completa_cuandoNoHayPendientesSinResolver() {
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.EN_CURSO);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(pendienteRepository
                .findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc(orden.getId()))
                .thenReturn(List.of());

        OrdenTrabajoInstalacionDto dto = servicio.cambiarEstado(orden.getId(), "completada");

        assertThat(dto.estado()).isEqualTo("completada");
        verify(ordenRepository).save(any(OrdenTrabajoInstalacion.class));
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
    }

    @Test
    void cambiarEstado_lanza409_cuandoTransicionInvalida() {
        // Desde 'programada', 'completada' es transicion invalida (Req 19.5): 409
        // con independencia de los pendientes.
        OrdenTrabajoInstalacion orden = otiEn(EstadoOrdenTrabajoInstalacion.PROGRAMADA);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> servicio.cambiarEstado(orden.getId(), "completada"))
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrdenTrabajoInstalacion.PROGRAMADA);
        verify(ordenRepository, never()).save(any(OrdenTrabajoInstalacion.class));
    }

    @Test
    void cambiarEstado_lanza404_cuandoOtiNoAccesible() {
        UUID desconocida = UUID.randomUUID();
        when(ordenRepository.findById(desconocida)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarEstado(desconocida, "completada"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}

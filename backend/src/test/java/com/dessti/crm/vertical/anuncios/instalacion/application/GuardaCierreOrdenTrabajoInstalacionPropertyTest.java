package com.dessti.crm.vertical.anuncios.instalacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.EvidenciaInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.OrdenTrabajoInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence.PendienteInstalacionRepository;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.OrdenTrabajoInstalacion;
import com.dessti.crm.vertical.anuncios.instalacion.domain.PendienteInstalacion;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.operacion.produccion.application.OrdenFabricacionTerminadaPort;
import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.error.TransicionInvalidaException;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 6: Guarda de cierre
 * de la Orden_Trabajo_Instalacion</strong> (Req 19.6).
 *
 * <p>Enunciado: <em>para cualquier Orden_Trabajo_Instalacion, la transicion a
 * "completada" se permite si y solo si su Lista_Pendientes no contiene ningun
 * elemento sin resolver</em>. Esta guarda vive en la capa de aplicacion
 * ({@link ServicioOrdenesTrabajoInstalacion#cambiarEstado(UUID, String)}), pues
 * requiere consultar la tabla hija {@code pendiente_instalacion} via
 * {@link PendienteInstalacionRepository#existsByOrdenTrabajoInstalacionIdAndResueltoFalse(UUID)}.</p>
 *
 * <p>Reutiliza la pieza de produccion {@link ServicioOrdenesTrabajoInstalacion} tal
 * cual, inyectando como <em>mocks</em> de Mockito sus siete colaboradores
 * ({@link OrdenTrabajoInstalacionRepository}, {@link PendienteInstalacionRepository},
 * {@link EvidenciaInstalacionRepository}, {@link OrdenFabricacionTerminadaPort},
 * {@link LevantamientoCompletadoPort}, {@link PermisoAprobadoPort} y
 * {@link AuditoriaPort}). No hay contexto de Spring, ni base de datos, ni
 * Testcontainers: cada dimension relevante se modela como un valor generado y los
 * stubs devuelven exactamente ese valor.</p>
 *
 * <h2>Diseno de la property (el si-y-solo-si)</h2>
 * <p>Para aislar la guarda como <strong>unico</strong> determinante del cierre, la
 * OTI se coloca en {@link EstadoOrdenTrabajoInstalacion#EN_CURSO} &mdash; el unico
 * estado desde el que {@code completada} es una transicion valida de la maquina de
 * estados (Req 19.5). Asi la propia transicion nunca es la causa del rechazo y la
 * unica dimension que decide el resultado es
 * {@code hayPendientesSinResolver}:</p>
 * <ul>
 *   <li>{@code false}: la transicion a {@code completada} tiene exito &rarr; el DTO
 *       queda en {@code completada} y se persiste exactamente una vez.</li>
 *   <li>{@code true}: se rechaza con {@link ReglaNegocioException} ("existen
 *       pendientes por resolver") y el estado se conserva (jamas se persiste).</li>
 * </ul>
 * <p>Una segunda property documenta el <em>orden</em> de las comprobaciones: desde
 * {@link EstadoOrdenTrabajoInstalacion#PROGRAMADA} la maquina de estados prohibe
 * saltar a {@code completada}, de modo que se lanza
 * {@link TransicionInvalidaException} (409) <strong>con independencia</strong> de
 * los pendientes.</p>
 *
 * <p><strong>Aislamiento entre iteraciones:</strong> el servicio audita via
 * {@link TenantContext#require()}, que lee un thread-local; jqwik reutiliza el hilo
 * entre intentos. Por ello {@link #limpiarContexto()} (anotado {@link AfterTry})
 * limpia el {@link TenantContext} tras cada intento para evitar fugas de estado.</p>
 */
class GuardaCierreOrdenTrabajoInstalacionPropertyTest {

    /** Etiqueta del estado destino bajo prueba (cierre). */
    private static final String ESTADO_COMPLETADA = "completada";

    // ----------------------------------------------------------------------
    // Aislamiento entre intentos: TenantContext es thread-local y jqwik
    // reutiliza el hilo. Establecer/limpiar el tenant en cada try.
    // ----------------------------------------------------------------------
    @AfterTry
    void limpiarContexto() {
        TenantContext.clear();
    }

    // ----------------------------------------------------------------------
    // Utilidad de montaje del servicio con sus colaboradores mockeados.
    // ----------------------------------------------------------------------

    /**
     * Monta un {@link ServicioOrdenesTrabajoInstalacion} con mocks, coloca la OTI en
     * el {@code estadoInicial} indicado (partiendo de {@code programada} y aplicando
     * las transiciones legales necesarias) y configura la guarda del Req 19.6 segun
     * {@code hayPendientesSinResolver}. Establece el tenant en contexto (el servicio
     * audita via {@link TenantContext#require()}).
     */
    private static Montaje montar(EstadoOrdenTrabajoInstalacion estadoInicial,
                                  boolean hayPendientesSinResolver) {
        TenantContext.set(UUID.randomUUID());

        OrdenTrabajoInstalacionRepository ordenRepository = mock(OrdenTrabajoInstalacionRepository.class);
        PendienteInstalacionRepository pendienteRepository = mock(PendienteInstalacionRepository.class);
        EvidenciaInstalacionRepository evidenciaRepository = mock(EvidenciaInstalacionRepository.class);
        OrdenFabricacionTerminadaPort ordenFabricacionTerminada = mock(OrdenFabricacionTerminadaPort.class);
        LevantamientoCompletadoPort levantamientoCompletado = mock(LevantamientoCompletadoPort.class);
        PermisoAprobadoPort permisoAprobado = mock(PermisoAprobadoPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        OrdenTrabajoInstalacion orden = construirOrdenEn(estadoInicial);

        // cargar(...) resuelve la OTI por id; devolvemos la misma instancia cuyo
        // getId() consulta la guarda.
        lenient().when(ordenRepository.findById(any(UUID.class))).thenReturn(Optional.of(orden));

        // save(...) devuelve la misma entidad recibida (materializa el cambio).
        lenient().when(ordenRepository.save(any(OrdenTrabajoInstalacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        // Guarda de cierre (Req 19.6, Req 8.5; diseno §C2): unica dimension que decide
        // el resultado. La guarda recupera los pendientes SIN RESOLVER (consulta
        // agregada findBy...OrderByCreatedAtAsc) y, si hay al menos uno, enumera sus
        // descripciones en el 422. Se devuelve un pendiente sin resolver cuando la
        // dimension generada lo indica; en otro caso, lista vacia.
        List<PendienteInstalacion> sinResolver = hayPendientesSinResolver
                ? List.of(PendienteInstalacion.paraOrden(orden, "fijar anclas", "actor-prueba"))
                : List.of();
        lenient().when(pendienteRepository
                        .findByOrdenTrabajoInstalacionIdAndResueltoFalseOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(sinResolver);

        ServicioOrdenesTrabajoInstalacion servicio = new ServicioOrdenesTrabajoInstalacion(
                ordenRepository, pendienteRepository, evidenciaRepository,
                ordenFabricacionTerminada, levantamientoCompletado, permisoAprobado, auditoria);
        return new Montaje(servicio, orden, ordenRepository);
    }

    /**
     * Construye una {@link OrdenTrabajoInstalacion} en el estado indicado partiendo
     * de {@code programada} y aplicando solo transiciones legales de la maquina de
     * estados (Req 19.5): {@code programada -> en_curso}.
     */
    private static OrdenTrabajoInstalacion construirOrdenEn(EstadoOrdenTrabajoInstalacion estado) {
        OrdenTrabajoInstalacion orden = OrdenTrabajoInstalacion.programar(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2025, 1, 15), "actor-prueba");
        if (estado == EstadoOrdenTrabajoInstalacion.EN_CURSO) {
            orden.cambiarEstado(EstadoOrdenTrabajoInstalacion.EN_CURSO, "actor-prueba");
        } else if (estado != EstadoOrdenTrabajoInstalacion.PROGRAMADA) {
            throw new IllegalArgumentException(
                    "Solo se construyen OTI en 'programada' o 'en_curso' via transiciones legales.");
        }
        return orden;
    }

    // ----------------------------------------------------------------------
    // Property 6 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 6: Para cualquier Orden_Trabajo_Instalacion, la transición a "completada" se permite si y solo si su Lista_Pendientes no contiene ningún elemento sin resolver.
    @Property(tries = 1000)
    void cierrePermitidoSiiNoHayPendientesSinResolver(
            @ForAll boolean hayPendientesSinResolver) {

        // OTI en EN_CURSO: la transicion a 'completada' es valida en la maquina de
        // estados, aislando la guarda del Req 19.6 como unico determinante.
        Montaje montaje = montar(EstadoOrdenTrabajoInstalacion.EN_CURSO, hayPendientesSinResolver);
        UUID ordenId = montaje.orden().getId();

        if (!hayPendientesSinResolver) {
            // si-y-solo-si (permitido): sin pendientes sin resolver, el cierre procede.
            OrdenTrabajoInstalacionDto dto =
                    montaje.servicio().cambiarEstado(ordenId, ESTADO_COMPLETADA);
            assertThat(dto).as("el cierre exitoso devuelve un DTO").isNotNull();
            assertThat(dto.estado())
                    .as("sin pendientes sin resolver, la OTI queda en 'completada'")
                    .isEqualTo(ESTADO_COMPLETADA);
            // Se persiste exactamente una vez el cambio de estado.
            verify(montaje.ordenRepository()).save(any(OrdenTrabajoInstalacion.class));
        } else {
            // si-y-solo-si (rechazado): con algun pendiente sin resolver, la guarda
            // dispara => ReglaNegocioException y el estado se conserva (no se persiste).
            assertThatThrownBy(() -> montaje.servicio().cambiarEstado(ordenId, ESTADO_COMPLETADA))
                    .as("con pendientes sin resolver, el cierre se rechaza (Req 19.6)")
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no se puede completar: pendientes por resolver:");
            assertThat(montaje.orden().getEstado())
                    .as("tras el rechazo, el estado de la OTI se conserva en 'en_curso'")
                    .isEqualTo(EstadoOrdenTrabajoInstalacion.EN_CURSO);
            verify(montaje.ordenRepository(), never()).save(any(OrdenTrabajoInstalacion.class));
        }
    }

    // Feature: crm-anuncios-luminosos, Property 6: Para cualquier Orden_Trabajo_Instalacion, la transición a "completada" se permite si y solo si su Lista_Pendientes no contiene ningún elemento sin resolver.
    @Property(tries = 1000)
    void desdeProgramadaElCierreEsTransicionInvalidaIndependienteDeLosPendientes(
            @ForAll boolean hayPendientesSinResolver) {

        // OTI en PROGRAMADA: la maquina de estados (Req 19.5) prohibe saltar a
        // 'completada' sin pasar por 'en_curso'. La transicion es invalida (409)
        // CON INDEPENDENCIA de la Lista_Pendientes: el cierre solo puede permitirse
        // desde un estado en que la transicion sea valida, condicion previa al
        // si-y-solo-si de la guarda del Req 19.6.
        Montaje montaje = montar(EstadoOrdenTrabajoInstalacion.PROGRAMADA, hayPendientesSinResolver);
        UUID ordenId = montaje.orden().getId();

        assertThatThrownBy(() -> montaje.servicio().cambiarEstado(ordenId, ESTADO_COMPLETADA))
                .as("desde 'programada', 'completada' es transicion invalida (409)")
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(montaje.orden().getEstado())
                .as("tras la transicion invalida, el estado se conserva en 'programada'")
                .isEqualTo(EstadoOrdenTrabajoInstalacion.PROGRAMADA);
        verify(montaje.ordenRepository(), never()).save(any(OrdenTrabajoInstalacion.class));
    }

    // ----------------------------------------------------------------------
    // Montaje
    // ----------------------------------------------------------------------

    /**
     * Tripleta (servicio bajo prueba, OTI mockeada devuelta por {@code findById},
     * repositorio mockeado) para poder verificar la ausencia/presencia de
     * {@code save} sobre el mismo mock inyectado y el estado conservado de la OTI.
     */
    private record Montaje(ServicioOrdenesTrabajoInstalacion servicio,
                           OrdenTrabajoInstalacion orden,
                           OrdenTrabajoInstalacionRepository ordenRepository) {
    }
}

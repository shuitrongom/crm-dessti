package com.dessti.crm.operacion.proyecto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.AvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaAvanceConsultaPort;
import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.operacion.proyecto.domain.Sitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Property-based test (jqwik) del <strong>gating del avance manual de fase</strong>
 * (Req 3-bis, Property 2-bis). Para el giro anuncios y todo estado de las
 * precondiciones (levantamiento completado sí/no, permiso vigente sí/no), el avance de
 * uso común a {@code en_preparacion}/{@code en_instalacion} se acepta <em>si y solo
 * si</em> se cumplen las precondiciones de esa fase; ante rechazo, 422 y la fase se
 * conserva. Con giro genérico el gating no aplica y el avance procede.
 *
 * <p>Se ejerce el servicio de producción {@link ServicioProyectos} con dobles Mockito;
 * cada dimensión se modela como un booleano generado y los adaptadores devuelven
 * exactamente ese valor. El {@link TenantContext} se limpia tras cada intento.</p>
 */
class GatingAvanceSitioPropertyTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @AfterTry
    void limpiarTenant() {
        TenantContext.clear();
    }

    // Feature: operacion-produccion-enterprise, Property 2-bis: el avance a en_preparacion (giro anuncios) se acepta sii el levantamiento está completado; si no, 422 y la fase se conserva.
    @Property(tries = 200)
    void preparacionAceptadaSiiLevantamientoCompletado(@ForAll boolean levantamientoCompletado) {
        Montaje m = montarAnuncios();
        when(m.precondicionesAnuncios.sitioTieneLevantamientoCompletado(m.sitio.getId()))
                .thenReturn(levantamientoCompletado);

        Throwable error = catchThrowable(() -> m.servicio.avanzarAvanceSitio(
                m.proyecto.getId(), m.sitio.getId(), FaseSitioGenerica.EN_PREPARACION, null, null));

        if (levantamientoCompletado) {
            assertThat(error).as("con levantamiento completado, el avance procede").isNull();
            assertThat(m.avance.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
        } else {
            assertThat(error).isInstanceOf(ReglaNegocioException.class);
            assertThat(m.avance.getFase())
                    .as("sin levantamiento, la fase se conserva en pendiente")
                    .isEqualTo(FaseSitioGenerica.PENDIENTE);
        }
    }

    // Feature: operacion-produccion-enterprise, Property 2-bis: el avance a en_instalacion (giro anuncios) se acepta sii hay levantamiento completado Y permiso vigente; en otro caso 422 y la fase se conserva.
    @Property(tries = 300)
    void instalacionAceptadaSiiLevantamientoYPermisoVigente(
            @ForAll boolean levantamientoCompletado,
            @ForAll boolean permisoVigente) {

        Montaje m = montarAnuncios();
        // La OTI parte de en_preparacion (transición lineal válida hacia en_instalacion).
        m.avance.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, "actor");
        lenient().when(m.precondicionesAnuncios.sitioTieneLevantamientoCompletado(m.sitio.getId()))
                .thenReturn(levantamientoCompletado);
        lenient().when(m.precondicionesAnuncios.sitioTienePermisoVigente(m.sitio.getId()))
                .thenReturn(permisoVigente);

        Throwable error = catchThrowable(() -> m.servicio.avanzarAvanceSitio(
                m.proyecto.getId(), m.sitio.getId(), FaseSitioGenerica.EN_INSTALACION, null, null));

        boolean debeProceder = levantamientoCompletado && permisoVigente;
        if (debeProceder) {
            assertThat(error).as("con levantamiento y permiso vigente, procede").isNull();
            assertThat(m.avance.getFase()).isEqualTo(FaseSitioGenerica.EN_INSTALACION);
        } else {
            assertThat(error).isInstanceOf(ReglaNegocioException.class);
            assertThat(m.avance.getFase())
                    .as("si falta alguna precondición, la fase se conserva en en_preparacion")
                    .isEqualTo(FaseSitioGenerica.EN_PREPARACION);
        }
    }

    // Feature: operacion-produccion-enterprise, Property 2-bis: con giro genérico el avance lineal no aplica las precondiciones de levantamiento/permiso.
    @Property(tries = 100)
    void genericoNoAplicaGatingDeAnuncios(@ForAll boolean ignorado) {
        Montaje m = montarGenerico();

        Throwable error = catchThrowable(() -> m.servicio.avanzarAvanceSitio(
                m.proyecto.getId(), m.sitio.getId(), FaseSitioGenerica.EN_PREPARACION, null, null));

        assertThat(error).as("el giro genérico no bloquea por levantamiento/permiso").isNull();
        assertThat(m.avance.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
    }

    // ------------------------------------------------------------------
    // Montaje
    // ------------------------------------------------------------------

    private Montaje montarAnuncios() {
        return montar(PerfilFasesGiro.ANUNCIOS);
    }

    private Montaje montarGenerico() {
        return montar(PerfilFasesGiro.GENERICO);
    }

    private Montaje montar(PerfilFasesGiro perfil) {
        TenantContext.set(TENANT);
        ProyectoRepository proyectoRepository = mock(ProyectoRepository.class);
        SitioRepository sitioRepository = mock(SitioRepository.class);
        AvanceSitioRepository avanceSitioRepository = mock(AvanceSitioRepository.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        PerfilFasesGiroPort perfilFasesGiro = mock(PerfilFasesGiroPort.class);
        AvanceSitioPort avanceProduccion = mock(AvanceSitioPort.class);
        AvanceSitioPort avanceSitioAnuncios = mock(AvanceSitioPort.class);
        PrecondicionesFaseSitioPort precondicionesGenerico = mock(PrecondicionesFaseSitioPort.class);
        PrecondicionesFaseSitioPort precondicionesAnuncios = mock(PrecondicionesFaseSitioPort.class);
        EvidenciaAvanceConsultaPort evidenciaConsulta = mock(EvidenciaAvanceConsultaPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);

        // El adaptador genérico no bloquea (patrón del adaptador real).
        lenient().when(precondicionesGenerico.sitioTieneLevantamientoCompletado(any())).thenReturn(true);
        lenient().when(precondicionesGenerico.sitioTienePermisoVigente(any())).thenReturn(true);
        when(perfilFasesGiro.perfilDelTenant()).thenReturn(perfil);

        ServicioProyectos servicio = new ServicioProyectos(
                proyectoRepository, sitioRepository, avanceSitioRepository, clienteExistente,
                perfilFasesGiro, avanceProduccion, avanceSitioAnuncios,
                precondicionesGenerico, precondicionesAnuncios, evidenciaConsulta, auditoria);

        Proyecto proyecto = Proyecto.crear(CLIENTE, "Proyecto multi-sitio", "actor");
        Sitio sitio = Sitio.paraProyecto(proyecto.getId(), "Sucursal Centro", null, "actor");
        AvanceSitio avance = AvanceSitio.inicial(sitio.getId(), "actor");

        lenient().when(proyectoRepository.findById(proyecto.getId())).thenReturn(Optional.of(proyecto));
        lenient().when(sitioRepository.findById(sitio.getId())).thenReturn(Optional.of(sitio));
        lenient().when(avanceSitioRepository.findBySitioId(sitio.getId())).thenReturn(Optional.of(avance));
        lenient().when(avanceSitioRepository.save(any(AvanceSitio.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sitioRepository.findByProyectoIdOrderByCreatedAtAsc(proyecto.getId()))
                .thenReturn(List.of(sitio));
        lenient().when(avanceSitioRepository.findBySitioIdIn(any())).thenReturn(List.of(avance));

        return new Montaje(servicio, proyecto, sitio, avance, precondicionesAnuncios);
    }

    private record Montaje(ServicioProyectos servicio, Proyecto proyecto, Sitio sitio,
                           AvanceSitio avance, PrecondicionesFaseSitioPort precondicionesAnuncios) {
    }
}

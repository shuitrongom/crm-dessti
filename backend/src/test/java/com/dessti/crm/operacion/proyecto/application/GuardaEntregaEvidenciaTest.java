package com.dessti.crm.operacion.proyecto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.AvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.application.AvanceSitioPort;
import com.dessti.crm.operacion.proyecto.application.PrecondicionesFaseSitioPort;
import com.dessti.crm.operacion.proyecto.application.evidencia.EvidenciaAvanceConsultaPort;
import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;
import com.dessti.crm.operacion.proyecto.application.PerfilFasesGiroPort;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.operacion.proyecto.domain.Sitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas de la GUARDA DE NEGOCIO de entrega (Req 3.2, deber-ser enterprise): un
 * Sitio en {@code en_instalacion} NO puede avanzar a {@code entregado} sin al
 * menos una evidencia APROBADA de la fase de instalacion; con evidencia aprobada,
 * la entrega procede. La correccion administrativa puede saltar la guarda.
 */
class GuardaEntregaEvidenciaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ProyectoRepository proyectoRepository;
    private SitioRepository sitioRepository;
    private AvanceSitioRepository avanceSitioRepository;
    private EvidenciaAvanceConsultaPort evidenciaConsulta;
    private ServicioProyectos servicio;

    private Proyecto proyecto;
    private Sitio sitio;
    private AvanceSitio avanceEnInstalacion;

    @BeforeEach
    void preparar() {
        proyectoRepository = mock(ProyectoRepository.class);
        sitioRepository = mock(SitioRepository.class);
        avanceSitioRepository = mock(AvanceSitioRepository.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        PerfilFasesGiroPort perfilFasesGiro = mock(PerfilFasesGiroPort.class);
        AvanceSitioPort avanceProduccion = mock(AvanceSitioPort.class);
        AvanceSitioPort avanceSitioAnuncios = mock(AvanceSitioPort.class);
        PrecondicionesFaseSitioPort precondicionesGenerico = mock(PrecondicionesFaseSitioPort.class);
        PrecondicionesFaseSitioPort precondicionesAnuncios = mock(PrecondicionesFaseSitioPort.class);
        evidenciaConsulta = mock(EvidenciaAvanceConsultaPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioProyectos(
                proyectoRepository, sitioRepository, avanceSitioRepository, clienteExistente,
                perfilFasesGiro, avanceProduccion, avanceSitioAnuncios,
                precondicionesGenerico, precondicionesAnuncios, evidenciaConsulta, auditoria);

        // El giro es anuncios (perfil con todas las fases); para ENTREGADO la guarda
        // que decide es la de evidencia, no las precondiciones de levantamiento/permiso.
        lenient().when(perfilFasesGiro.perfilDelTenant()).thenReturn(PerfilFasesGiro.ANUNCIOS);

        TenantContext.set(TENANT);
        proyecto = Proyecto.crear(CLIENTE, "Proyecto multi-sitio", "actor");
        sitio = Sitio.paraProyecto(proyecto.getId(), "Sucursal Centro", null, "actor");
        // Avance ya en instalacion (para intentar entregar).
        avanceEnInstalacion = AvanceSitio.inicial(sitio.getId(), "actor");
        avanceEnInstalacion.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, "actor");
        avanceEnInstalacion.avanzarFase(FaseSitioGenerica.EN_INSTALACION, null, null, "actor");

        lenient().when(proyectoRepository.findById(proyecto.getId())).thenReturn(Optional.of(proyecto));
        lenient().when(sitioRepository.findById(sitio.getId())).thenReturn(Optional.of(sitio));
        lenient().when(avanceSitioRepository.findBySitioId(sitio.getId()))
                .thenReturn(Optional.of(avanceEnInstalacion));
        lenient().when(avanceSitioRepository.save(any(AvanceSitio.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // consultarMultisitio() recarga los sitios del proyecto al final.
        lenient().when(sitioRepository.findByProyectoIdOrderByCreatedAtAsc(proyecto.getId()))
                .thenReturn(List.of(sitio));
        lenient().when(avanceSitioRepository.findBySitioIdIn(any()))
                .thenReturn(List.of(avanceEnInstalacion));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void noEntregaSinEvidenciaAprobada() {
        when(evidenciaConsulta.tieneEvidenciaAprobada(
                avanceEnInstalacion.getId(), FaseSitioGenerica.EN_INSTALACION)).thenReturn(false);

        assertThatThrownBy(() -> servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.ENTREGADO, null, null))
                .isInstanceOf(ReglaNegocioException.class);

        // La fase NO cambio a entregado.
        assertThat(avanceEnInstalacion.getFase()).isEqualTo(FaseSitioGenerica.EN_INSTALACION);
    }

    @Test
    void entregaConEvidenciaAprobada() {
        when(evidenciaConsulta.tieneEvidenciaAprobada(
                avanceEnInstalacion.getId(), FaseSitioGenerica.EN_INSTALACION)).thenReturn(true);

        servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.ENTREGADO, null, null);

        assertThat(avanceEnInstalacion.getFase()).isEqualTo(FaseSitioGenerica.ENTREGADO);
    }

    @Test
    void correccionAdministrativaSaltaLaGuarda() {
        // Sin evidencia aprobada, pero la correccion administrativa puede entregar.
        lenient().when(evidenciaConsulta.tieneEvidenciaAprobada(any(), eq(FaseSitioGenerica.EN_INSTALACION)))
                .thenReturn(false);

        servicio.corregirAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.ENTREGADO, "ajuste", null);

        assertThat(avanceEnInstalacion.getFase()).isEqualTo(FaseSitioGenerica.ENTREGADO);
    }
}

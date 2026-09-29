package com.dessti.crm.operacion.proyecto.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

/**
 * Pruebas del GATING de precondiciones del avance manual de fase del Sitio (Req 3-bis):
 * el avance de uso comun a {@code en_preparacion}/{@code en_instalacion} exige, en el
 * giro anuncios, Levantamiento_Sitio completado y Permiso_Instalacion aprobado y vigente;
 * en el giro generico no se aplica ese gating. La correccion administrativa exige un
 * motivo (nota) no vacio pero salta las precondiciones de avance.
 *
 * <p>Se ejerce el servicio real con dobles Mockito, siguiendo el patron de
 * {@code GuardaEntregaEvidenciaTest}. Los adaptadores de precondiciones se doblan para
 * modelar cada dimension (levantamiento/permiso) como un valor controlado.</p>
 */
class GatingAvanceSitioTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ProyectoRepository proyectoRepository;
    private SitioRepository sitioRepository;
    private AvanceSitioRepository avanceSitioRepository;
    private PerfilFasesGiroPort perfilFasesGiro;
    private PrecondicionesFaseSitioPort precondicionesAnuncios;
    private PrecondicionesFaseSitioPort precondicionesGenerico;
    private EvidenciaAvanceConsultaPort evidenciaConsulta;
    private ServicioProyectos servicio;

    private Proyecto proyecto;
    private Sitio sitio;
    private AvanceSitio avance;

    @BeforeEach
    void preparar() {
        proyectoRepository = mock(ProyectoRepository.class);
        sitioRepository = mock(SitioRepository.class);
        avanceSitioRepository = mock(AvanceSitioRepository.class);
        ClienteExistentePort clienteExistente = mock(ClienteExistentePort.class);
        perfilFasesGiro = mock(PerfilFasesGiroPort.class);
        AvanceSitioPort avanceProduccion = mock(AvanceSitioPort.class);
        AvanceSitioPort avanceSitioAnuncios = mock(AvanceSitioPort.class);
        precondicionesGenerico = mock(PrecondicionesFaseSitioPort.class);
        precondicionesAnuncios = mock(PrecondicionesFaseSitioPort.class);
        evidenciaConsulta = mock(EvidenciaAvanceConsultaPort.class);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        servicio = new ServicioProyectos(
                proyectoRepository, sitioRepository, avanceSitioRepository, clienteExistente,
                perfilFasesGiro, avanceProduccion, avanceSitioAnuncios,
                precondicionesGenerico, precondicionesAnuncios, evidenciaConsulta, auditoria);

        TenantContext.set(TENANT);
        proyecto = Proyecto.crear(CLIENTE, "Proyecto multi-sitio", "actor");
        sitio = Sitio.paraProyecto(proyecto.getId(), "Sucursal Centro", null, "actor");
        avance = AvanceSitio.inicial(sitio.getId(), "actor");

        lenient().when(proyectoRepository.findById(proyecto.getId())).thenReturn(Optional.of(proyecto));
        lenient().when(sitioRepository.findById(sitio.getId())).thenReturn(Optional.of(sitio));
        lenient().when(avanceSitioRepository.findBySitioId(sitio.getId()))
                .thenReturn(Optional.of(avance));
        lenient().when(avanceSitioRepository.save(any(AvanceSitio.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // consultarMultisitio() recarga los sitios y avances del proyecto al final.
        lenient().when(sitioRepository.findByProyectoIdOrderByCreatedAtAsc(proyecto.getId()))
                .thenReturn(List.of(sitio));
        lenient().when(avanceSitioRepository.findBySitioIdIn(any()))
                .thenReturn(List.of(avance));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private void giroAnuncios() {
        when(perfilFasesGiro.perfilDelTenant()).thenReturn(PerfilFasesGiro.ANUNCIOS);
    }

    private void giroGenerico() {
        when(perfilFasesGiro.perfilDelTenant()).thenReturn(PerfilFasesGiro.GENERICO);
    }

    // ------------------------------------------------------------------
    // Preparacion: requiere levantamiento completado (giro anuncios)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("anuncios: pasar a en_preparacion sin levantamiento completado se rechaza (422) y conserva la fase")
    void preparacionSinLevantamientoRechaza() {
        giroAnuncios();
        when(precondicionesAnuncios.sitioTieneLevantamientoCompletado(sitio.getId())).thenReturn(false);

        assertThatThrownBy(() -> servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_PREPARACION, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Levantamiento_Sitio completado");

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.PENDIENTE);
    }

    @Test
    @DisplayName("anuncios: pasar a en_preparacion con levantamiento completado procede")
    void preparacionConLevantamientoProcede() {
        giroAnuncios();
        when(precondicionesAnuncios.sitioTieneLevantamientoCompletado(sitio.getId())).thenReturn(true);

        servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_PREPARACION, null, null);

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
    }

    // ------------------------------------------------------------------
    // Instalacion: requiere levantamiento + permiso vigente (giro anuncios)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("anuncios: pasar a en_instalacion sin permiso vigente se rechaza (422) y conserva la fase")
    void instalacionSinPermisoVigenteRechaza() {
        giroAnuncios();
        avance.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, "actor");
        when(precondicionesAnuncios.sitioTieneLevantamientoCompletado(sitio.getId())).thenReturn(true);
        when(precondicionesAnuncios.sitioTienePermisoVigente(sitio.getId())).thenReturn(false);

        assertThatThrownBy(() -> servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_INSTALACION, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Permiso_Instalacion aprobado y vigente");

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
    }

    @Test
    @DisplayName("anuncios: pasar a en_instalacion sin levantamiento (aunque haya permiso) se rechaza (422)")
    void instalacionSinLevantamientoRechaza() {
        giroAnuncios();
        avance.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, "actor");
        when(precondicionesAnuncios.sitioTieneLevantamientoCompletado(sitio.getId())).thenReturn(false);

        assertThatThrownBy(() -> servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_INSTALACION, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Levantamiento_Sitio completado");

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
    }

    @Test
    @DisplayName("anuncios: pasar a en_instalacion con levantamiento y permiso vigente procede")
    void instalacionConPrecondicionesProcede() {
        giroAnuncios();
        avance.avanzarFase(FaseSitioGenerica.EN_PREPARACION, null, null, "actor");
        when(precondicionesAnuncios.sitioTieneLevantamientoCompletado(sitio.getId())).thenReturn(true);
        when(precondicionesAnuncios.sitioTienePermisoVigente(sitio.getId())).thenReturn(true);

        servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_INSTALACION, null, null);

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.EN_INSTALACION);
    }

    // ------------------------------------------------------------------
    // Giro generico: el gating de levantamiento/permiso NO aplica
    // ------------------------------------------------------------------

    @Test
    @DisplayName("generico: el avance lineal no exige levantamiento ni permiso")
    void genericoAvanzaSinPrecondicionesDeAnuncios() {
        giroGenerico();
        // El adaptador generico devuelve true en ambos; ni siquiera se consulta anuncios.

        servicio.avanzarAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_PREPARACION, null, null);

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.EN_PREPARACION);
    }

    // ------------------------------------------------------------------
    // Correccion administrativa: motivo obligatorio; salta precondiciones
    // ------------------------------------------------------------------

    @Test
    @DisplayName("correccion sin motivo se rechaza (422) y conserva la fase")
    void correccionSinMotivoRechaza() {
        giroAnuncios();

        assertThatThrownBy(() -> servicio.corregirAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_INSTALACION, "  ", null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("motivo");

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.PENDIENTE);
    }

    @Test
    @DisplayName("correccion con motivo salta las precondiciones de avance (puede saltar a instalacion)")
    void correccionConMotivoSaltaPrecondiciones() {
        giroAnuncios();
        // Sin levantamiento ni permiso: la correccion administrativa igual procede.
        lenient().when(precondicionesAnuncios.sitioTieneLevantamientoCompletado(sitio.getId()))
                .thenReturn(false);
        lenient().when(precondicionesAnuncios.sitioTienePermisoVigente(sitio.getId()))
                .thenReturn(false);

        servicio.corregirAvanceSitio(
                proyecto.getId(), sitio.getId(), FaseSitioGenerica.EN_INSTALACION,
                "alta administrativa por migracion", null);

        assertThat(avance.getFase()).isEqualTo(FaseSitioGenerica.EN_INSTALACION);
    }
}

package com.dessti.crm.operacion.proyecto.application.evidencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dessti.crm.operacion.proyecto.adapter.out.persistence.AvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.EvidenciaAvanceSitioRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.ProyectoRepository;
import com.dessti.crm.operacion.proyecto.adapter.out.persistence.SitioRepository;
import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.Proyecto;
import com.dessti.crm.operacion.proyecto.domain.Sitio;
import com.dessti.crm.operacion.proyecto.domain.evidencia.EstadoEvidencia;
import com.dessti.crm.operacion.proyecto.domain.evidencia.EvidenciaAvanceSitio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Pruebas unitarias de {@link ServicioEvidenciasAvance} (Req 3.2): subida con
 * validacion de tipo/tamano, guardado del archivo en el almacen, listado,
 * decision (aprobar/rechazar) y la guarda de evidencia aprobada. Sin Spring ni BD.
 */
class ServicioEvidenciasAvanceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENTE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private EvidenciaAvanceSitioRepository evidenciaRepository;
    private AvanceSitioRepository avanceSitioRepository;
    private SitioRepository sitioRepository;
    private ProyectoRepository proyectoRepository;
    private EvidenciaStoragePort storage;
    private EvidenciaStorageProperties propiedades;
    private AuditoriaPort auditoria;
    private ServicioEvidenciasAvance servicio;

    private Proyecto proyecto;
    private Sitio sitio;

    @BeforeEach
    void preparar() {
        evidenciaRepository = mock(EvidenciaAvanceSitioRepository.class);
        avanceSitioRepository = mock(AvanceSitioRepository.class);
        sitioRepository = mock(SitioRepository.class);
        proyectoRepository = mock(ProyectoRepository.class);
        storage = mock(EvidenciaStoragePort.class);
        propiedades = new EvidenciaStorageProperties(
                "datos/evidencias", 10, List.of("image/jpeg", "image/png", "image/webp", "application/pdf"));
        auditoria = mock(AuditoriaPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);
        servicio = new ServicioEvidenciasAvance(
                evidenciaRepository, avanceSitioRepository, sitioRepository, proyectoRepository,
                storage, propiedades, auditoria, clock);

        TenantContext.set(TENANT);
        proyecto = Proyecto.crear(CLIENTE, "Proyecto multi-sitio", "actor");
        sitio = Sitio.paraProyecto(proyecto.getId(), "Sucursal Centro", "Av. Reforma 100", "actor");
        lenient().when(proyectoRepository.findById(proyecto.getId())).thenReturn(Optional.of(proyecto));
        lenient().when(sitioRepository.findById(sitio.getId())).thenReturn(Optional.of(sitio));
        lenient().when(evidenciaRepository.save(any(EvidenciaAvanceSitio.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(avanceSitioRepository.save(any(AvanceSitio.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void subirGuardaArchivoYCreaEvidenciaPendiente() {
        // El Sitio aun no tiene avance: se materializa en PENDIENTE.
        when(avanceSitioRepository.findBySitioId(sitio.getId())).thenReturn(Optional.empty());
        when(storage.guardar(eq(TENANT), any(), any(), eq("image/jpeg"))).thenReturn("k1.jpg");

        EvidenciaAvanceDto dto = servicio.subir(
                proyecto.getId(), sitio.getId(), "foto".getBytes(), "obra.jpg", "image/jpeg");

        assertThat(dto.estado()).isEqualTo(EstadoEvidencia.PENDIENTE.valorBd());
        assertThat(dto.fase()).isEqualTo(FaseSitioGenerica.PENDIENTE.valorBd());
        assertThat(dto.nombreOriginal()).isEqualTo("obra.jpg");
        verify(storage).guardar(eq(TENANT), any(), eq("obra.jpg"), eq("image/jpeg"));
        verify(evidenciaRepository).save(any(EvidenciaAvanceSitio.class));
    }

    @Test
    void subirRechazaTipoNoPermitido() {
        assertThatThrownBy(() -> servicio.subir(
                proyecto.getId(), sitio.getId(), "x".getBytes(), "malo.exe", "application/x-msdownload"))
                .isInstanceOf(ReglaNegocioException.class);
        verify(storage, never()).guardar(any(), any(), any(), any());
    }

    @Test
    void subirRechazaArchivoQueExcedeTamano() {
        byte[] grande = new byte[(int) propiedades.maxTamanoBytes() + 1];
        assertThatThrownBy(() -> servicio.subir(
                proyecto.getId(), sitio.getId(), grande, "grande.pdf", "application/pdf"))
                .isInstanceOf(ReglaNegocioException.class);
        verify(storage, never()).guardar(any(), any(), any(), any());
    }

    @Test
    void aprobarTransicionaEvidenciaAAprobada() {
        AvanceSitio avance = AvanceSitio.inicial(sitio.getId(), "actor");
        EvidenciaAvanceSitio evidencia = EvidenciaAvanceSitio.subir(
                avance.getId(), FaseSitioGenerica.EN_INSTALACION, "k.jpg", "obra.jpg",
                "image/jpeg", 100, "operador",
                Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC));
        when(evidenciaRepository.findById(evidencia.getId())).thenReturn(Optional.of(evidencia));

        EvidenciaAvanceDto dto = servicio.decidir(evidencia.getId(), true, null);

        assertThat(dto.estado()).isEqualTo(EstadoEvidencia.APROBADA.valorBd());
        assertThat(dto.decididaPor()).isNotNull();
    }

    @Test
    void rechazarSinMotivoLanza422() {
        AvanceSitio avance = AvanceSitio.inicial(sitio.getId(), "actor");
        EvidenciaAvanceSitio evidencia = EvidenciaAvanceSitio.subir(
                avance.getId(), FaseSitioGenerica.EN_INSTALACION, "k.jpg", "obra.jpg",
                "image/jpeg", 100, "operador",
                Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC));
        when(evidenciaRepository.findById(evidencia.getId())).thenReturn(Optional.of(evidencia));

        assertThatThrownBy(() -> servicio.decidir(evidencia.getId(), false, "  "))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void tieneEvidenciaAprobadaConsultaElRepositorio() {
        UUID avanceId = UUID.randomUUID();
        when(evidenciaRepository.countByAvanceSitioIdAndFaseAndEstado(
                avanceId, FaseSitioGenerica.EN_INSTALACION, EstadoEvidencia.APROBADA)).thenReturn(1L);

        assertThat(servicio.tieneEvidenciaAprobada(avanceId, FaseSitioGenerica.EN_INSTALACION)).isTrue();
    }

    @Test
    void listarSinAvanceDevuelveVacio() {
        when(avanceSitioRepository.findBySitioId(sitio.getId())).thenReturn(Optional.empty());
        assertThat(servicio.listar(proyecto.getId(), sitio.getId())).isEmpty();
    }
}

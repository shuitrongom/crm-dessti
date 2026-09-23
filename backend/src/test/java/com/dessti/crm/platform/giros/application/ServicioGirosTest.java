package com.dessti.crm.platform.giros.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.GiroDto;
import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.ItemNavegacionVertical;
import com.dessti.crm.platform.vertical.RegistroVerticales;

/**
 * Pruebas unitarias de {@link ServicioGiros} (Req 1). Sustituyen los
 * colaboradores (repositorio, puerto de conteo de empresas por giro y puerto de
 * auditoria) por dobles de Mockito, y usan un {@link RegistroVerticales} real
 * construido a partir de {@link ContratoVertical} <em>fake</em> para ejercer la
 * enriquecedora de completitud sin arrancar contexto de Spring ni base de datos.
 * Replican el estilo de {@code ServicioRolesTest} y {@code ServicioEmpresasTest}.
 *
 * <p>Cubren las reglas de negocio criticas del catalogo de Giros:</p>
 * <ul>
 *   <li>La clave duplicada rechaza el alta con conflicto (Req 1.3) y no
 *       persiste.</li>
 *   <li>El alta con clave valida y unica persiste el Giro y audita la operacion
 *       (Req 1.1, 1.7).</li>
 *   <li>No se puede desactivar un Giro en uso por alguna Empresa (Req 1.5): se
 *       rechaza con {@link ReglaNegocioException} y no se guarda el cambio.</li>
 *   <li>Desactivar un Giro libre (conteo 0) aplica la baja logica y audita
 *       (Req 1.1, 1.7).</li>
 *   <li>Activar/desactivar un Giro inexistente produce
 *       {@link RecursoNoEncontradoException} (404).</li>
 *   <li>El listado delega en el repositorio con y sin filtro de estado
 *       (Req 1.6).</li>
 *   <li>La completitud del DTO ("Base"/"Completo") se deriva del
 *       {@link RegistroVerticales}: un Giro con vertical programado expone
 *       {@code tieneReglasNegocio=true} y {@code modulosEspecificos>0}; uno sin
 *       vertical, {@code false} y {@code 0}.</li>
 * </ul>
 */
class ServicioGirosTest {

    /** Clave del unico Giro con vertical programado en estas pruebas. */
    private static final String GIRO_CON_VERTICAL = "anuncios-luminosos";

    private GiroRepository giroRepository;
    private ConteoEmpresasPorGiroPort conteoEmpresasPorGiro;
    private AuditoriaPort auditoria;
    private RegistroVerticales registroVerticales;
    private ServicioGiros servicio;

    @BeforeEach
    void preparar() {
        giroRepository = mock(GiroRepository.class);
        conteoEmpresasPorGiro = mock(ConteoEmpresasPorGiroPort.class);
        auditoria = mock(AuditoriaPort.class);
        // Registro real con un unico vertical programado (anuncios-luminosos,
        // 2 modulos: operacion + mantenimiento), como el AnunciosVertical real.
        registroVerticales = new RegistroVerticales(List.of(
                new VerticalFake(GIRO_CON_VERTICAL, Set.of("operacion", "mantenimiento"))));
        servicio = new ServicioGiros(giroRepository, conteoEmpresasPorGiro, auditoria, registroVerticales);
    }

    // -----------------------------------------------------------------
    // Req 1.3: clave duplicada -> conflicto, sin persistir
    // -----------------------------------------------------------------

    @Test
    @DisplayName("crear con clave duplicada lanza ConflictoUnicidadException y no persiste ni audita (Req 1.3)")
    void crearClaveDuplicadaEsConflicto() {
        // La clave normalizada de 'Anuncios Luminosos' es 'anuncios-luminosos'.
        when(giroRepository.existsByClave("anuncios-luminosos")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear("Anuncios Luminosos", "Anuncios Luminosos", null))
                .isInstanceOf(ConflictoUnicidadException.class)
                .hasMessageContaining("anuncios-luminosos");

        // No se guarda nada ni se registra auditoria si la clave ya existe.
        verify(giroRepository, never()).saveAndFlush(any());
        verify(giroRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // Req 1.1 + 1.7: alta valida persiste y audita
    // -----------------------------------------------------------------

    @Test
    @DisplayName("crear con clave valida y unica persiste el Giro (clave normalizada, activo) y audita (Req 1.1, 1.7)")
    void crearGiroValidoPersisteYAudita() {
        when(giroRepository.existsByClave("manufactura")).thenReturn(false);
        // saveAndFlush devuelve la misma entidad recibida.
        when(giroRepository.saveAndFlush(any(Giro.class))).thenAnswer(inv -> inv.getArgument(0));

        GiroDto creado = servicio.crear("  Manufactura  ", "Manufactura Industrial", "Giro de prueba");

        // La clave se normaliza a kebab/minusculas y el Giro nace activo.
        assertThat(creado.clave()).isEqualTo("manufactura");
        assertThat(creado.nombreVisible()).isEqualTo("Manufactura Industrial");
        assertThat(creado.activo()).isTrue();
        // 'manufactura' no tiene vertical programado en estas pruebas: es "Base".
        assertThat(creado.tieneReglasNegocio()).isFalse();
        assertThat(creado.modulosEspecificos()).isZero();

        // Se persiste exactamente el Giro creado.
        ArgumentCaptor<Giro> giroCaptor = ArgumentCaptor.forClass(Giro.class);
        verify(giroRepository).saveAndFlush(giroCaptor.capture());
        assertThat(giroCaptor.getValue().getClave()).isEqualTo("manufactura");

        // Auditoria de plataforma (sin tenant): accion 'crear', recurso 'giro'.
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.accion()).isEqualTo("crear");
        assertThat(evento.recurso()).isEqualTo(ServicioGiros.RECURSO_GIRO);
        assertThat(evento.tenantId()).isEmpty();
    }

    // -----------------------------------------------------------------
    // Req 1.5: no desactivar un Giro en uso
    // -----------------------------------------------------------------

    @Test
    @DisplayName("desactivar un Giro en uso lanza ReglaNegocioException (con conteo) y no guarda el cambio (Req 1.5)")
    void desactivarGiroEnUsoEsRechazado() {
        Giro giro = Giro.crear("anuncios-luminosos", "Anuncios Luminosos", null, "super");
        when(giroRepository.findById(giro.getId())).thenReturn(Optional.of(giro));
        // Tres empresas usan aun este giro: la desactivacion debe rechazarse.
        when(conteoEmpresasPorGiro.contarEmpresasPorGiro(giro.getId())).thenReturn(3L);

        assertThatThrownBy(() -> servicio.desactivar(giro.getId()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("3");

        // El Giro sigue activo y no se persiste ni audita el cambio.
        assertThat(giro.isActivo()).isTrue();
        verify(giroRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // Req 1.1 + 1.7: desactivar un Giro libre
    // -----------------------------------------------------------------

    @Test
    @DisplayName("desactivar un Giro libre (conteo 0) aplica la baja logica (activo=false) y audita (Req 1.1, 1.7)")
    void desactivarGiroLibreDesactivaYAudita() {
        Giro giro = Giro.crear("manufactura", "Manufactura Industrial", null, "super");
        when(giroRepository.findById(giro.getId())).thenReturn(Optional.of(giro));
        when(conteoEmpresasPorGiro.contarEmpresasPorGiro(giro.getId())).thenReturn(0L);
        when(giroRepository.save(any(Giro.class))).thenAnswer(inv -> inv.getArgument(0));

        GiroDto resultado = servicio.desactivar(giro.getId());

        // La baja logica deja el Giro inactivo y se persiste el cambio.
        assertThat(resultado.activo()).isFalse();
        ArgumentCaptor<Giro> giroCaptor = ArgumentCaptor.forClass(Giro.class);
        verify(giroRepository).save(giroCaptor.capture());
        assertThat(giroCaptor.getValue().isActivo()).isFalse();

        // Auditoria de la desactivacion.
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo("desactivar");
        assertThat(eventoCaptor.getValue().recurso()).isEqualTo(ServicioGiros.RECURSO_GIRO);
    }

    // -----------------------------------------------------------------
    // Req 1.1: id inexistente -> 404
    // -----------------------------------------------------------------

    @Test
    @DisplayName("activar un Giro inexistente lanza RecursoNoEncontradoException (404)")
    void activarGiroInexistenteEsNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(giroRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.activar(id))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(giroRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("desactivar un Giro inexistente lanza RecursoNoEncontradoException (404)")
    void desactivarGiroInexistenteEsNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(giroRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.desactivar(id))
                .isInstanceOf(RecursoNoEncontradoException.class);

        // No se consulta el conteo ni se guarda si el Giro no existe.
        verify(conteoEmpresasPorGiro, never()).contarEmpresasPorGiro(any());
        verify(giroRepository, never()).save(any());
    }

    // -----------------------------------------------------------------
    // Req 1.6: listado delega en el repositorio, con y sin filtro
    // -----------------------------------------------------------------

    @Test
    @DisplayName("listar con estado filtra por 'activo'; sin estado lista todos (Req 1.6)")
    void listarDelegaEnRepositorioConYSinFiltro() {
        Pageable pageable = PageRequest.of(0, 20);
        Giro giro = Giro.crear("anuncios-luminosos", "Anuncios Luminosos", null, "super");
        Page<Giro> pagina = new PageImpl<>(List.of(giro), pageable, 1);
        when(giroRepository.findByActivo(true, pageable)).thenReturn(pagina);
        when(giroRepository.findAllBy(pageable)).thenReturn(pagina);

        Page<GiroDto> conFiltro = servicio.listar(true, pageable);
        assertThat(conFiltro.getContent()).extracting(GiroDto::clave).containsExactly("anuncios-luminosos");
        verify(giroRepository).findByActivo(eq(true), eq(pageable));

        Page<GiroDto> sinFiltro = servicio.listar(null, pageable);
        assertThat(sinFiltro.getContent()).extracting(GiroDto::clave).containsExactly("anuncios-luminosos");
        verify(giroRepository).findAllBy(eq(pageable));
    }

    // -----------------------------------------------------------------
    // Completitud del Giro (Base vs Completo) derivada del RegistroVerticales
    // -----------------------------------------------------------------

    @Test
    @DisplayName("un Giro con vertical programado es 'Completo': tieneReglasNegocio=true y modulosEspecificos>0")
    void giroConVerticalEsCompleto() {
        Pageable pageable = PageRequest.of(0, 20);
        // Su clave 'anuncios-luminosos' esta registrada por el VerticalFake (2 modulos).
        Giro giro = Giro.crear(GIRO_CON_VERTICAL, "Anuncios Luminosos", null, "super");
        when(giroRepository.findAllBy(pageable))
                .thenReturn(new PageImpl<>(List.of(giro), pageable, 1));

        GiroDto dto = servicio.listar(null, pageable).getContent().get(0);

        assertThat(dto.tieneReglasNegocio()).isTrue();
        assertThat(dto.modulosEspecificos()).isEqualTo(2);
    }

    @Test
    @DisplayName("un Giro sin vertical programado es 'Base': tieneReglasNegocio=false y modulosEspecificos=0")
    void giroSinVerticalEsBase() {
        Pageable pageable = PageRequest.of(0, 20);
        // 'manufactura' no esta registrado por ningun vertical en estas pruebas.
        Giro giro = Giro.crear("manufactura", "Manufactura Industrial", null, "super");
        when(giroRepository.findAllBy(pageable))
                .thenReturn(new PageImpl<>(List.of(giro), pageable, 1));

        GiroDto dto = servicio.listar(null, pageable).getContent().get(0);

        assertThat(dto.tieneReglasNegocio()).isFalse();
        assertThat(dto.modulosEspecificos()).isZero();
    }

    // -----------------------------------------------------------------
    // Unicidad por nombre visible: crear -> 409 (ademas de por clave)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("crear con nombre visible ya usado (ignore-case) lanza ConflictoUnicidadException y no persiste ni audita")
    void crearNombreVisibleDuplicadoEsConflicto() {
        // La clave 'manufactura' es libre, pero el nombre visible ya existe.
        when(giroRepository.existsByClave("manufactura")).thenReturn(false);
        when(giroRepository.existsByNombreVisibleIgnoreCase("Manufactura Industrial")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear("Manufactura", "Manufactura Industrial", null))
                .isInstanceOf(ConflictoUnicidadException.class)
                .hasMessageContaining("Manufactura Industrial");

        verify(giroRepository, never()).saveAndFlush(any());
        verify(giroRepository, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // eliminar: happy path, 404, 422 (reglas de negocio) y 422 (en uso)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("eliminar un Giro 'Base' (sin vertical) y sin Empresas lo borra y audita")
    void eliminarGiroBaseSinEmpresasBorraYAudita() {
        // 'manufactura' no tiene vertical programado en estas pruebas: es "Base".
        Giro giro = Giro.crear("manufactura", "Manufactura Industrial", null, "super");
        when(giroRepository.findById(giro.getId())).thenReturn(Optional.of(giro));
        when(conteoEmpresasPorGiro.contarEmpresasPorGiro(giro.getId())).thenReturn(0L);

        servicio.eliminar(giro.getId());

        verify(giroRepository).delete(giro);
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo("eliminar");
        assertThat(eventoCaptor.getValue().recurso()).isEqualTo(ServicioGiros.RECURSO_GIRO);
        assertThat(eventoCaptor.getValue().tenantId()).isEmpty();
    }

    @Test
    @DisplayName("eliminar un Giro inexistente lanza RecursoNoEncontradoException (404)")
    void eliminarGiroInexistenteEsNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(giroRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(id))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(conteoEmpresasPorGiro, never()).contarEmpresasPorGiro(any());
        verify(giroRepository, never()).delete(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("eliminar un Giro con reglas de negocio programadas (Completo) lanza ReglaNegocioException (422)")
    void eliminarGiroConReglasNegocioEsRechazado() {
        // 'anuncios-luminosos' SI tiene vertical programado (VerticalFake): es "Completo".
        Giro giro = Giro.crear(GIRO_CON_VERTICAL, "Anuncios Luminosos", null, "super");
        when(giroRepository.findById(giro.getId())).thenReturn(Optional.of(giro));

        assertThatThrownBy(() -> servicio.eliminar(giro.getId()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("reglas de negocio");

        // No se consulta el conteo de empresas ni se borra ni audita.
        verify(conteoEmpresasPorGiro, never()).contarEmpresasPorGiro(any());
        verify(giroRepository, never()).delete(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("eliminar un Giro 'Base' en uso por Empresas lanza ReglaNegocioException con el conteo (422)")
    void eliminarGiroEnUsoEsRechazado() {
        Giro giro = Giro.crear("manufactura", "Manufactura Industrial", null, "super");
        when(giroRepository.findById(giro.getId())).thenReturn(Optional.of(giro));
        when(conteoEmpresasPorGiro.contarEmpresasPorGiro(giro.getId())).thenReturn(5L);

        assertThatThrownBy(() -> servicio.eliminar(giro.getId()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("5");

        verify(giroRepository, never()).delete(any());
        verify(auditoria, never()).registrar(any());
    }

    // -----------------------------------------------------------------
    // Doble de ContratoVertical para poblar un RegistroVerticales real.
    // -----------------------------------------------------------------

    /**
     * {@link ContratoVertical} minimo para las pruebas: declara una clave de Giro
     * y un conjunto de modulos, sin recursos ni navegacion, para ejercer la
     * enriquecedora de completitud del servicio.
     */
    private record VerticalFake(String giro, Set<String> modulos) implements ContratoVertical {
        @Override
        public Set<String> recursos() {
            return Set.of();
        }

        @Override
        public List<ItemNavegacionVertical> navegacion() {
            return List.of();
        }
    }
}

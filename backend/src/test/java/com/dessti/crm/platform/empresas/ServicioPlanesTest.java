package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.modulos.CatalogoModulosService;
import com.dessti.crm.platform.modulos.ModuloCatalogoDto;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.domain.Moneda;

/**
 * Pruebas unitarias de {@link ServicioPlanes} (Req 25.1, 25.5) tras el rediseno
 * {@code plataforma-multigiro}:
 * <ul>
 *   <li>crearPlan persiste con Giro, moneda y precio por modulo, calcula el total
 *       (suma) y audita como evento de plataforma.</li>
 *   <li>crearPlan rechaza un nombre duplicado con 409 (comprobacion previa y
 *       violacion del indice de la BD).</li>
 *   <li>Giro obligatorio/inexistente, moneda inexistente/inactiva y modulo de
 *       otro Giro producen los errores esperados; los modulos de Nucleo siempre
 *       se aceptan.</li>
 *   <li>actualizarPlan modifica y audita; 404 si el Plan no existe.</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers: los colaboradores se
 * sustituyen por dobles de Mockito.</p>
 */
class ServicioPlanesTest {

    private static final UUID GIRO_ID = UUID.randomUUID();
    private static final String GIRO_CLAVE = "anuncios-luminosos";

    private PlanRepository planRepository;
    private GiroRepository giroRepository;
    private MonedaRepository monedaRepository;
    private CatalogoModulosService catalogoModulosService;
    private CatalogoModulosGiroValidacion catalogoModulosGiroValidacion;
    private SuscripcionRepository suscripcionRepository;
    private AuditoriaPort auditoria;
    private ServicioPlanes servicio;
    private Giro giro;

    @BeforeEach
    void preparar() {
        planRepository = mock(PlanRepository.class);
        giroRepository = mock(GiroRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        catalogoModulosService = mock(CatalogoModulosService.class);
        suscripcionRepository = mock(SuscripcionRepository.class);
        auditoria = mock(AuditoriaPort.class);
        // El helper compartido de validacion (moneda activa + modulos por giro) se
        // construye REAL sobre los mismos dobles de Mockito para conservar la
        // cobertura previa de esas validaciones (que antes vivian en ServicioPlanes).
        catalogoModulosGiroValidacion =
                new CatalogoModulosGiroValidacion(catalogoModulosService, monedaRepository);
        servicio = new ServicioPlanes(planRepository, giroRepository,
                catalogoModulosGiroValidacion, suscripcionRepository, auditoria);

        // Giro del Plan existente y activo por defecto. Su id lo genera la entidad.
        giro = Giro.crear(GIRO_CLAVE, "Anuncios luminosos", null, "super");
        lenient().when(giroRepository.findById(GIRO_ID)).thenReturn(Optional.of(giro));
        // Moneda MXN activa por defecto.
        Moneda mxn = Moneda.crear("MXN", "Peso mexicano", "super");
        lenient().when(monedaRepository.findByCodigo("MXN")).thenReturn(Optional.of(mxn));
        // Catalogo: 'comercial' es Nucleo (giro null); 'produccion-industrial' es del Giro anuncios.
        lenient().when(catalogoModulosService.listar()).thenReturn(List.of(
                new ModuloCatalogoDto("comercial", "Comercial (CRM)", null, UUID.randomUUID(),
                        new BigDecimal("100.00"), "MXN"),
                new ModuloCatalogoDto("facturacion", "Facturacion (CFDI)", null, UUID.randomUUID(),
                        new BigDecimal("50.00"), "MXN"),
                new ModuloCatalogoDto("produccion-industrial", "Produccion industrial", GIRO_CLAVE, null,
                        null, "MXN"),
                new ModuloCatalogoDto("modulo-de-otro-giro", "Otro", "manufactura", null, null, "MXN")));
    }

    private CrearPlanCommand comandoCrear(Map<String, BigDecimal> precios) {
        return new CrearPlanCommand("Premium", 10, 730, GIRO_ID, "MXN", precios);
    }

    @Test
    @DisplayName("crearPlan persiste con Giro, moneda, precios y total, y audita (Req 25.1, 25.5)")
    void creaPlanValidoYAudita() {
        when(planRepository.existsByNombre("Premium")).thenReturn(false);
        when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = comandoCrear(Map.of(
                "Comercial", new BigDecimal("100.00"),
                "facturacion", new BigDecimal("50.00")));
        PlanDto dto = servicio.crearPlan(comando);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.nombre()).isEqualTo("Premium");
        assertThat(dto.maxUsuarios()).isEqualTo(10);
        assertThat(dto.giroId()).isEqualTo(giro.getId());
        assertThat(dto.monedaCodigo()).isEqualTo("MXN");
        // Claves normalizadas y sincronizadas con modulos_habilitados.
        assertThat(dto.modulosHabilitados()).containsExactlyInAnyOrder("comercial", "facturacion");
        assertThat(dto.preciosModulos()).containsKeys("comercial", "facturacion");
        // Total = suma de precios (100 + 50).
        assertThat(dto.total()).isEqualByComparingTo(new BigDecimal("150.00"));

        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).saveAndFlush(planCaptor.capture());
        assertThat(planCaptor.getValue().getMaxUsuarios()).isEqualTo(10);

        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.tenantId()).isEmpty(); // plataforma
        assertThat(evento.accion()).isEqualTo("crear");
        assertThat(evento.recurso()).isEqualTo(ServicioPlanes.RECURSO_PLAN);
    }

    @Test
    @DisplayName("crearPlan acepta modulos de Nucleo y de su propio Giro (Req plataforma-multigiro)")
    void aceptaModulosDeNucleoYDelPropioGiro() {
        when(planRepository.existsByNombre("Premium")).thenReturn(false);
        when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = comandoCrear(Map.of(
                "comercial", new BigDecimal("100.00"),
                "produccion-industrial", new BigDecimal("200.00")));
        PlanDto dto = servicio.crearPlan(comando);

        assertThat(dto.modulosHabilitados()).containsExactlyInAnyOrder("comercial", "produccion-industrial");
        assertThat(dto.total()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("crearPlan con precios vacios da total 0.00 (Req plataforma-multigiro)")
    void planSinModulosTieneTotalCero() {
        when(planRepository.existsByNombre("Premium")).thenReturn(false);
        when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PlanDto dto = servicio.crearPlan(comandoCrear(Map.of()));

        assertThat(dto.modulosHabilitados()).isEmpty();
        assertThat(dto.total()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("crearPlan rechaza un modulo especifico de OTRO Giro con 422 (Req plataforma-multigiro)")
    void rechazaModuloDeOtroGiro() {
        var comando = comandoCrear(Map.of("modulo-de-otro-giro", new BigDecimal("10.00")));

        assertThatThrownBy(() -> servicio.crearPlan(comando))
                .isInstanceOf(ReglaNegocioException.class);

        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("crearPlan rechaza una clave de modulo inexistente con 422 (Req plataforma-multigiro)")
    void rechazaModuloDesconocido() {
        var comando = comandoCrear(Map.of("no-existe", new BigDecimal("10.00")));

        assertThatThrownBy(() -> servicio.crearPlan(comando))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crearPlan con Giro inexistente da 404 (Req plataforma-multigiro)")
    void rechazaGiroInexistente() {
        UUID otro = UUID.randomUUID();
        when(giroRepository.findById(otro)).thenReturn(Optional.empty());
        var comando = new CrearPlanCommand("Premium", 10, 730, otro, "MXN", Map.of());

        assertThatThrownBy(() -> servicio.crearPlan(comando))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("crearPlan con moneda inexistente da 422 (Req plataforma-multigiro)")
    void rechazaMonedaInexistente() {
        when(monedaRepository.findByCodigo("JPY")).thenReturn(Optional.empty());
        var comando = new CrearPlanCommand("Premium", 10, 730, GIRO_ID, "JPY", Map.of());

        assertThatThrownBy(() -> servicio.crearPlan(comando))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crearPlan con moneda inactiva da 422 (Req plataforma-multigiro)")
    void rechazaMonedaInactiva() {
        Moneda inactiva = Moneda.crear("EUR", "Euro", "super");
        inactiva.desactivar("super");
        when(monedaRepository.findByCodigo("EUR")).thenReturn(Optional.of(inactiva));
        var comando = new CrearPlanCommand("Premium", 10, 730, GIRO_ID, "EUR", Map.of());

        assertThatThrownBy(() -> servicio.crearPlan(comando))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("crearPlan rechaza un nombre duplicado por comprobacion previa -> 409 (Req 25.1)")
    void rechazaNombreDuplicadoPorComprobacionPrevia() {
        when(planRepository.existsByNombre("Premium")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearPlan(comandoCrear(Map.of())))
                .isInstanceOf(ConflictoUnicidadException.class);

        verify(planRepository, never()).saveAndFlush(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("crearPlan traduce la violacion del indice unico de la BD a 409 (Req 25.1)")
    void traduceViolacionUnicidadDeLaBaseDeDatos() {
        when(planRepository.existsByNombre("Premium")).thenReturn(false);
        when(planRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_plan_nombre"));

        assertThatThrownBy(() -> servicio.crearPlan(comandoCrear(Map.of())))
                .isInstanceOf(ConflictoUnicidadException.class);
    }

    @Test
    @DisplayName("crearPlan con max_usuarios negativo se rechaza con 422 (Req 25.1)")
    void rechazaMaxUsuariosNegativo() {
        var comando = new CrearPlanCommand("Premium", -1, 730, GIRO_ID, "MXN", Map.of());

        assertThatThrownBy(() -> servicio.crearPlan(comando))
                .isInstanceOf(ReglaNegocioException.class);

        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("actualizarPlan modifica los limites, audita y devuelve el DTO (Req 25.1, 25.5)")
    void actualizaPlanExistente() {
        UUID planId = UUID.randomUUID();
        Plan existente = Plan.crear("Basico", 5, 730, GIRO_ID, "MXN",
                Map.of("comercial", new BigDecimal("100.00")), "super");
        when(planRepository.findById(planId)).thenReturn(Optional.of(existente));
        when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new ActualizarPlanCommand("Basico", 8, 730, GIRO_ID, "MXN",
                Map.of("comercial", new BigDecimal("120.00"),
                        "produccion-industrial", new BigDecimal("80.00")));
        PlanDto dto = servicio.actualizarPlan(planId, comando);

        assertThat(dto.maxUsuarios()).isEqualTo(8);
        assertThat(dto.modulosHabilitados()).containsExactlyInAnyOrder("comercial", "produccion-industrial");
        assertThat(dto.total()).isEqualByComparingTo(new BigDecimal("200.00"));

        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo("actualizar");
    }

    @Test
    @DisplayName("actualizarPlan de un Plan inexistente da 404 (Req 25.1)")
    void actualizarPlanInexistenteEsNoEncontrado() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        var comando = new ActualizarPlanCommand("Basico", 8, 730, GIRO_ID, "MXN", Map.of());

        assertThatThrownBy(() -> servicio.actualizarPlan(planId, comando))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(planRepository, never()).saveAndFlush(any());
    }

    // -----------------------------------------------------------------
    // eliminarPlan: happy path, 404 y 422 (Suscripciones asociadas)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("eliminarPlan sin Suscripciones borra el Plan y audita como plataforma (Req 25.1, 25.5)")
    void eliminaPlanSinSuscripciones() {
        UUID planId = UUID.randomUUID();
        Plan existente = Plan.crear("Basico", 5, 730, GIRO_ID, "MXN",
                Map.of("comercial", new BigDecimal("100.00")), "super");
        when(planRepository.findById(planId)).thenReturn(Optional.of(existente));
        when(suscripcionRepository.countByPlanId(planId)).thenReturn(0L);

        servicio.eliminarPlan(planId);

        verify(planRepository).delete(existente);
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.accion()).isEqualTo("eliminar");
        assertThat(evento.recurso()).isEqualTo(ServicioPlanes.RECURSO_PLAN);
        assertThat(evento.tenantId()).isEmpty();
    }

    @Test
    @DisplayName("eliminarPlan de un Plan inexistente da 404 y no borra ni audita (Req 25.1)")
    void eliminarPlanInexistenteEsNoEncontrado() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminarPlan(planId))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(suscripcionRepository, never()).countByPlanId(any());
        verify(planRepository, never()).delete(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("eliminarPlan con Suscripciones asociadas da 422 (con conteo) y no borra ni audita (Req 25.1)")
    void eliminarPlanConSuscripcionesEsRechazado() {
        UUID planId = UUID.randomUUID();
        Plan existente = Plan.crear("Premium", 10, 730, GIRO_ID, "MXN", Map.of(), "super");
        when(planRepository.findById(planId)).thenReturn(Optional.of(existente));
        when(suscripcionRepository.countByPlanId(planId)).thenReturn(4L);

        assertThatThrownBy(() -> servicio.eliminarPlan(planId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("4");

        verify(planRepository, never()).delete(any());
        verify(auditoria, never()).registrar(any());
    }
}

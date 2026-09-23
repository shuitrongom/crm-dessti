package com.dessti.crm.platform.monetizacion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.empresas.EstadoSuscripcion;
import com.dessti.crm.platform.empresas.Plan;
import com.dessti.crm.platform.empresas.PlanRepository;
import com.dessti.crm.platform.empresas.Suscripcion;
import com.dessti.crm.platform.empresas.SuscripcionRepository;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.FacturaRentaRepository;
import com.dessti.crm.platform.monetizacion.domain.FacturaRenta;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas del calculo/emision de la factura de renta en
 * {@link ServicioFacturacionRenta}: suma de los modulos habilitados por el
 * precio que define el Plan ({@code preciosModulos}), regla de coherencia de
 * moneda (sin conversion FX), herencia de moneda del Plan, modulos gratuitos
 * (precio 0) y omision de modulos sin precio en el Plan.
 */
class ServicioFacturacionRentaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GIRO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);

    private SuscripcionRepository suscripcionRepository;
    private PlanRepository planRepository;
    private CatalogoModuloRepository catalogoRepository;
    private FacturaRentaRepository facturaRepository;
    private com.dessti.crm.platform.empresas.EmpresaRepository empresaRepository;
    private TenantSessionInitializer tenantSession;
    private ServicioFacturacionRenta servicio;

    @BeforeEach
    void preparar() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        planRepository = mock(PlanRepository.class);
        catalogoRepository = mock(CatalogoModuloRepository.class);
        facturaRepository = mock(FacturaRentaRepository.class);
        empresaRepository = mock(com.dessti.crm.platform.empresas.EmpresaRepository.class);
        tenantSession = mock(TenantSessionInitializer.class);
        servicio = new ServicioFacturacionRenta(suscripcionRepository, planRepository,
                catalogoRepository, facturaRepository,
                empresaRepository, new FacturaRentaPdfService(),
                new EmisorProperties(null, null, null, null, null),
                mock(AuditoriaPort.class), RELOJ, tenantSession);
        lenient().when(catalogoRepository.findByClave(any())).thenReturn(Optional.empty());
    }

    /** Prepara una Suscripcion activa (con override de modulos y/o moneda) para el tenant. */
    private Suscripcion suscripcionActiva(java.util.Set<String> override, String moneda) {
        Suscripcion s = Suscripcion.crear(TENANT, PLAN_ID, LocalDate.of(2025, 1, 1), null, "super");
        if (override != null) {
            s.asignarModulos(override, "super");
        }
        if (moneda != null) {
            s.fijarMonedaFacturacion(moneda, "super");
        }
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(s));
        return s;
    }

    /** Prepara el Plan referenciado por la Suscripcion con su mapa de precios. */
    private Plan planConPrecios(String moneda, Map<String, BigDecimal> precios) {
        Plan plan = Plan.crear("Premium", 10, 730, GIRO_ID, moneda, precios, "super");
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        return plan;
    }

    @Test
    @DisplayName("valua los modulos del Plan y suma el total en la moneda del Plan")
    void valuaConPreciosDelPlan() {
        suscripcionActiva(null, "MXN");
        planConPrecios("MXN", Map.of(
                "comercial", new BigDecimal("1500.00"),
                "facturacion", new BigDecimal("800.00")));

        FacturaRentaDto dto = servicio.calcular(TENANT, LocalDate.of(2025, 6, 1));

        assertThat(dto.monedaCodigo()).isEqualTo("MXN");
        assertThat(dto.total()).isEqualByComparingTo("2300.00");
        assertThat(dto.lineas()).hasSize(2);
        assertThat(dto.periodo()).isEqualTo(LocalDate.of(2025, 6, 1));
    }

    @Test
    @DisplayName("con override de la Suscripcion factura solo ese subconjunto")
    void overrideDeSuscripcion() {
        suscripcionActiva(java.util.Set.of("comercial"), "MXN");
        planConPrecios("MXN", Map.of(
                "comercial", new BigDecimal("1500.00"),
                "facturacion", new BigDecimal("800.00")));

        FacturaRentaDto dto = servicio.calcular(TENANT, LocalDate.of(2025, 6, 1));

        assertThat(dto.total()).isEqualByComparingTo("1500.00");
        assertThat(dto.lineas()).hasSize(1);
        assertThat(dto.lineas().get(0).moduloClave()).isEqualTo("comercial");
    }

    @Test
    @DisplayName("rechaza (422) si la moneda de facturacion difiere de la del Plan (sin FX)")
    void rechazaMonedaDistinta() {
        suscripcionActiva(java.util.Set.of("comercial"), "USD");
        planConPrecios("MXN", Map.of("comercial", new BigDecimal("1500.00")));

        assertThatThrownBy(() -> servicio.calcular(TENANT, null))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("cuando la Suscripcion no tiene moneda fijada usa la moneda del Plan")
    void heredaMonedaDelPlan() {
        suscripcionActiva(java.util.Set.of("comercial"), null);
        planConPrecios("MXN", Map.of("comercial", new BigDecimal("1500.00")));

        FacturaRentaDto dto = servicio.calcular(TENANT, LocalDate.of(2025, 6, 1));

        assertThat(dto.monedaCodigo()).isEqualTo("MXN");
        assertThat(dto.total()).isEqualByComparingTo("1500.00");
        assertThat(dto.lineas()).hasSize(1);
    }

    @Test
    @DisplayName("incluye como linea 0 un modulo gratuito (precio 0 en el Plan)")
    void moduloGratuitoIncluido() {
        suscripcionActiva(null, "MXN");
        planConPrecios("MXN", Map.of(
                "comercial", new BigDecimal("1500.00"),
                "soporte", new BigDecimal("0.00")));

        FacturaRentaDto dto = servicio.calcular(TENANT, LocalDate.of(2025, 6, 1));

        assertThat(dto.lineas()).hasSize(2);
        assertThat(dto.total()).isEqualByComparingTo("1500.00");
        assertThat(dto.lineas())
                .anySatisfy(l -> {
                    assertThat(l.moduloClave()).isEqualTo("soporte");
                    assertThat(l.precioAplicado()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    @DisplayName("omite un modulo del override que no tiene precio en el Plan")
    void omiteModuloSinPrecioEnPlan() {
        suscripcionActiva(java.util.Set.of("comercial", "inventario-avanzado"), "MXN");
        planConPrecios("MXN", Map.of("comercial", new BigDecimal("1500.00")));

        FacturaRentaDto dto = servicio.calcular(TENANT, LocalDate.of(2025, 6, 1));

        assertThat(dto.lineas()).hasSize(1);
        assertThat(dto.total()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("rechaza (422) cuando el Plan no tiene ningun modulo con precio")
    void rechazaPlanSinModulos() {
        suscripcionActiva(java.util.Set.of("comercial"), "MXN");
        // Plan con precios pero override apunta a un modulo inexistente en el Plan.
        planConPrecios("MXN", Map.of("facturacion", new BigDecimal("800.00")));

        assertThatThrownBy(() -> servicio.calcular(TENANT, null))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("emitir persiste la factura y calcula el total")
    void emiteFactura() {
        suscripcionActiva(java.util.Set.of("comercial"), "MXN");
        planConPrecios("MXN", Map.of("comercial", new BigDecimal("1500.00")));
        when(facturaRepository.findByTenantIdAndPeriodoAndMonedaCodigo(eq(TENANT), any(), eq("MXN")))
                .thenReturn(Optional.empty());
        when(facturaRepository.saveAndFlush(any(FacturaRenta.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FacturaRentaDto dto = servicio.emitir(TENANT, LocalDate.of(2025, 6, 1));

        assertThat(dto.total()).isEqualByComparingTo("1500.00");
        assertThat(dto.estado()).isEqualTo("emitida");
    }

    @Test
    @DisplayName("calcular fija app.current_tenant ANTES de leer la suscripcion (RLS, Req 23/24.3)")
    void calcularFijaTenantAntesDeLeerSuscripcion() {
        suscripcionActiva(java.util.Set.of("comercial"), "MXN");
        planConPrecios("MXN", Map.of("comercial", new BigDecimal("1500.00")));

        servicio.calcular(TENANT, LocalDate.of(2025, 6, 1));

        // El super_admin no tiene tenant en contexto; se debe fijar el tenant en la
        // transaccion ANTES de consultar la tabla `suscripcion` (protegida por RLS),
        // de lo contrario sus filas quedan ocultas (falso 404).
        InOrder orden = inOrder(tenantSession, suscripcionRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(suscripcionRepository)
                .findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA);
    }

    @Test
    @DisplayName("emitir fija app.current_tenant ANTES de leer la suscripcion (RLS, Req 23/24.3)")
    void emitirFijaTenantAntesDeLeerSuscripcion() {
        suscripcionActiva(java.util.Set.of("comercial"), "MXN");
        planConPrecios("MXN", Map.of("comercial", new BigDecimal("1500.00")));
        when(facturaRepository.findByTenantIdAndPeriodoAndMonedaCodigo(eq(TENANT), any(), eq("MXN")))
                .thenReturn(Optional.empty());
        when(facturaRepository.saveAndFlush(any(FacturaRenta.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        servicio.emitir(TENANT, LocalDate.of(2025, 6, 1));

        InOrder orden = inOrder(tenantSession, suscripcionRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(suscripcionRepository)
                .findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA);
    }
}

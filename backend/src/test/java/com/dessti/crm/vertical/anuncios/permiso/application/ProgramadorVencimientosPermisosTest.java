package com.dessti.crm.vertical.anuncios.permiso.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias del planificador multi-tenant
 * {@link ProgramadorVencimientosPermisos} (Tarea 8.4; Req 13.6). Usan dobles de
 * Mockito; no arrancan Spring, base de datos ni el scheduler real.
 *
 * <p>El {@link org.springframework.transaction.support.TransactionTemplate} interno
 * se construye a partir de un {@link PlatformTransactionManager} mockeado; el
 * template real ejecuta el callback (fija/limpia el contexto de tenant e invoca el
 * servicio), de modo que se verifica el comportamiento sin acoplar a una base de
 * datos.</p>
 *
 * <p>Cubren:</p>
 * <ul>
 *   <li><strong>Barrido de todos los tenants activos:</strong> por cada Empresa
 *       activa de {@link EmpresasActivasPort} se establece el contexto e invoca
 *       {@link ServicioPermisos#notificarVencimientosProximos()} (Req 13.6).</li>
 *   <li><strong>Aislamiento de fallos:</strong> un fallo al procesar un tenant no
 *       aborta el barrido de los demas (Req 13.6).</li>
 *   <li><strong>Limpieza del contexto:</strong> tras el barrido el
 *       {@link TenantContext} queda limpio (no filtra el tenant al hilo).</li>
 * </ul>
 */
class ProgramadorVencimientosPermisosTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ProgramadorVencimientosPermisos programador(EmpresasActivasPort empresas,
                                                        ServicioPermisos servicio,
                                                        TenantSessionInitializer sesion) {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        return new ProgramadorVencimientosPermisos(empresas, servicio, sesion, txManager);
    }

    @Test
    @DisplayName("procesa todas las Empresas activas invocando la notificacion por tenant (Req 13.6)")
    void barreTodosLosTenantsActivos() {
        EmpresasActivasPort empresas = mock(EmpresasActivasPort.class);
        ServicioPermisos servicio = mock(ServicioPermisos.class);
        TenantSessionInitializer sesion = mock(TenantSessionInitializer.class);
        when(empresas.tenantsActivos()).thenReturn(List.of(TENANT_A, TENANT_B, TENANT_C));
        when(servicio.notificarVencimientosProximos()).thenReturn(2);

        ProgramadorVencimientosPermisos programador = programador(empresas, servicio, sesion);

        programador.notificarVencimientosProgramado();

        // Un barrido por tenant activo: sesion RLS + notificacion por cada uno.
        verify(sesion).applyTenant(TENANT_A);
        verify(sesion).applyTenant(TENANT_B);
        verify(sesion).applyTenant(TENANT_C);
        verify(servicio, times(3)).notificarVencimientosProximos();
    }

    @Test
    @DisplayName("un fallo en un tenant no aborta el barrido de los demas (aislamiento, Req 13.6)")
    void unFalloNoAbortaElBarrido() {
        EmpresasActivasPort empresas = mock(EmpresasActivasPort.class);
        ServicioPermisos servicio = mock(ServicioPermisos.class);
        TenantSessionInitializer sesion = mock(TenantSessionInitializer.class);
        when(empresas.tenantsActivos()).thenReturn(List.of(TENANT_A, TENANT_B, TENANT_C));
        // El tenant B falla; A y C deben procesarse igualmente.
        doThrow(new RuntimeException("fallo simulado en B"))
                .when(sesion).applyTenant(TENANT_B);

        ProgramadorVencimientosPermisos programador = programador(empresas, servicio, sesion);

        assertThatCode(programador::notificarVencimientosProgramado)
                .doesNotThrowAnyException();

        // A y C se procesan pese al fallo de B (aislamiento).
        verify(servicio, times(2)).notificarVencimientosProximos();
        verify(sesion).applyTenant(TENANT_A);
        verify(sesion).applyTenant(TENANT_C);
    }

    @Test
    @DisplayName("tras el barrido el TenantContext queda limpio (no filtra el tenant, Req 13.6)")
    void limpiaElContextoTrasCadaTenant() {
        EmpresasActivasPort empresas = mock(EmpresasActivasPort.class);
        ServicioPermisos servicio = mock(ServicioPermisos.class);
        TenantSessionInitializer sesion = mock(TenantSessionInitializer.class);
        when(empresas.tenantsActivos()).thenReturn(List.of(TENANT_A, TENANT_B));

        ProgramadorVencimientosPermisos programador = programador(empresas, servicio, sesion);

        programador.notificarVencimientosProgramado();

        // El contexto se limpia en finally; no debe quedar tenant establecido.
        assertThatCode(TenantContext::require)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("sin Empresas activas no invoca la notificacion (barrido vacio, Req 13.6)")
    void barridoVacioNoInvoca() {
        EmpresasActivasPort empresas = mock(EmpresasActivasPort.class);
        ServicioPermisos servicio = mock(ServicioPermisos.class);
        TenantSessionInitializer sesion = mock(TenantSessionInitializer.class);
        when(empresas.tenantsActivos()).thenReturn(List.of());

        ProgramadorVencimientosPermisos programador = programador(empresas, servicio, sesion);

        programador.notificarVencimientosProgramado();

        verify(servicio, times(0)).notificarVencimientosProximos();
        verify(sesion, times(0)).applyTenant(any());
    }
}

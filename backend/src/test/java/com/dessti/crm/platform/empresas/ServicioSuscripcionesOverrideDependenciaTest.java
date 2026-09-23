package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de ejemplos/bordes de
 * {@link ServicioSuscripciones#actualizarModulosEmpresa} centradas en la
 * normalizacion de la dependencia {@code inventario-avanzado -> operacion}
 * (tarea 3.9) ANTES de validar el subconjunto contra el instrumento contratado
 * (Req 8.1, 8.4, 11.1, 11.2, 11.3):
 * <ul>
 *   <li>Un override con {@code inventario-avanzado} sobre un instrumento que
 *       ya ofrece {@code operacion} (porque el Plan se normalizo al persistir):
 *       agrega {@code operacion} al override, PASA la validacion de subconjunto
 *       y la Suscripcion guardada incluye {@code operacion}.</li>
 *   <li>Un override que pide un modulo que el instrumento realmente NO ofrece
 *       (ni por dependencia): lanza {@link ReglaNegocioException} (422). El
 *       comportamiento previo se conserva.</li>
 *   <li>Un override {@code null} (heredar): no se toca; la semantica
 *       null-vs-vacio queda intacta.</li>
 * </ul>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers; se apoya en mocks de los
 * repositorios y de los puertos de plataforma, igual que
 * {@link ServicioSuscripcionesTest}.</p>
 */
class ServicioSuscripcionesOverrideDependenciaTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SuscripcionRepository suscripcionRepository;
    private EmpresaRepository empresaRepository;
    private PlanRepository planRepository;
    private PaqueteSuscripcionRepository paqueteSuscripcionRepository;
    private AuditoriaPort auditoria;
    private TenantSessionInitializer tenantSession;
    private ServicioSuscripciones servicio;

    @BeforeEach
    void preparar() {
        suscripcionRepository = mock(SuscripcionRepository.class);
        empresaRepository = mock(EmpresaRepository.class);
        planRepository = mock(PlanRepository.class);
        paqueteSuscripcionRepository = mock(PaqueteSuscripcionRepository.class);
        auditoria = mock(AuditoriaPort.class);
        tenantSession = mock(TenantSessionInitializer.class);
        servicio = new ServicioSuscripciones(
                suscripcionRepository, empresaRepository, planRepository,
                paqueteSuscripcionRepository, auditoria, tenantSession);
    }

    /**
     * Prepara una Suscripcion ACTIVA de tipo PLAN cuyo Plan ofrece exactamente
     * los modulos indicados. El Plan se construye con {@link PlanTestFactory}
     * (que pasa por {@code aplicarPrecios}), de modo que si {@code modulos}
     * contiene {@code inventario-avanzado} el instrumento ya incluye
     * {@code operacion} (normalizacion al persistir, tarea 3.6).
     */
    private Suscripcion conSuscripcionActivaYPlan(Set<String> modulosPlan) {
        Suscripcion susc = Suscripcion.crear(TENANT, PLAN, LocalDate.of(2025, 1, 1), null, "super");
        Plan plan = PlanTestFactory.conModulos("Premium", 10, modulosPlan);
        when(suscripcionRepository.findFirstByTenantIdAndEstadoOrderByIdAsc(TENANT, EstadoSuscripcion.ACTIVA))
                .thenReturn(Optional.of(susc));
        when(planRepository.findById(PLAN)).thenReturn(Optional.of(plan));
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return susc;
    }

    @Test
    @DisplayName("Override con inventario-avanzado agrega operacion y pasa la validacion de subconjunto (Req 8.1, 8.4)")
    void overrideConInventarioAvanzadoAgregaOperacionYPasa() {
        // El Plan ofrece inventario-avanzado; al normalizarse al persistir
        // tambien ofrece operacion, por lo que la dependencia agregada al
        // override NO es rechazada por "no pertenece al instrumento".
        conSuscripcionActivaYPlan(Set.of("comercial", "inventario-avanzado"));

        // El super_admin pide solo inventario-avanzado (sin nombrar operacion).
        SuscripcionDto dto = servicio.actualizarModulosEmpresa(TENANT, Set.of("inventario-avanzado"));

        // La Suscripcion guardada incluye operacion por el cierre de dependencias.
        assertThat(dto.modulosHabilitados())
                .contains("inventario-avanzado", "operacion");

        // Se persiste sin lanzar 422.
        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(captor.capture());
        assertThat(captor.getValue().getModulosHabilitados())
                .contains("inventario-avanzado", "operacion");
    }

    @Test
    @DisplayName("Override con un modulo que el instrumento no ofrece (ni por dependencia) lanza 422 (Req 11.1, 11.2)")
    void overrideConModuloNoOfrecidoLanza422() {
        // El Plan solo ofrece comercial; pedir facturacion (que el instrumento
        // no ofrece ni por dependencia) debe rechazarse con 422.
        conSuscripcionActivaYPlan(Set.of("comercial"));

        assertThatThrownBy(() -> servicio.actualizarModulosEmpresa(TENANT, Set.of("facturacion")))
                .isInstanceOf(ReglaNegocioException.class);

        // No se persiste ningun cambio cuando la validacion falla.
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Override null (heredar) no se toca: semantica null-vs-vacio intacta (Req 11.3)")
    void overrideNullHereda() {
        Suscripcion susc = conSuscripcionActivaYPlan(Set.of("comercial", "inventario-avanzado"));

        servicio.actualizarModulosEmpresa(TENANT, null);

        // Con override null la Suscripcion vuelve a heredar del instrumento: el
        // override queda en null (no se fija un subconjunto ni se fuerza operacion).
        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(captor.capture());
        assertThat(captor.getValue().tieneOverrideModulos()).isFalse();
        assertThat(susc.getModulosHabilitados()).isNull();
    }
}

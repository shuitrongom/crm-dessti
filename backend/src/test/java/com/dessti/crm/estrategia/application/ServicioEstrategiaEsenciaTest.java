package com.dessti.crm.estrategia.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dessti.crm.estrategia.adapter.out.persistence.EsenciaEmpresaRepository;
import com.dessti.crm.estrategia.adapter.out.persistence.HistorialAvanceObjetivoRepository;
import com.dessti.crm.estrategia.adapter.out.persistence.ObjetivoEstrategicoRepository;
import com.dessti.crm.estrategia.adapter.out.persistence.ResultadoClaveRepository;
import com.dessti.crm.estrategia.domain.EsenciaEmpresa;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

/**
 * Pruebas unitarias de la visibilidad multi-tenant de la <strong>esencia</strong>
 * (mision/vision/valores) en {@link ServicioEstrategia} (Req 58.1, 23), que fijan la
 * regresion del bug del 404 en {@code GET /estrategia/esencia}.
 *
 * <p><strong>Bug corregido:</strong> con {@code open-in-view=false}, ni el filtro de
 * Hibernate (Capa 1, habilitado en el filtro web sobre una sesion efimera) ni
 * {@code app.current_tenant} (Capa 2/RLS) se aplicaban de forma fiable sobre la sesion
 * transaccional de LECTURA del servicio, por lo que la RLS ocultaba la fila propia del
 * tenant y {@code consultarEsencia} devolvia 404 aunque la esencia existiera en la BD.
 * El fix hace que cada operacion transaccional (1) fije {@code app.current_tenant} en
 * su propia transaccion via {@link TenantSessionInitializer#applyTenant(UUID)} y
 * (2) lea la esencia filtrando el {@code tenant_id} de forma EXPLICITA
 * ({@code findFirstByTenantIdOrderByCreatedAtAsc}).</p>
 *
 * <p>No usa {@code @SpringBootTest} ni Testcontainers: valida el contrato del servicio
 * con mocks, comprobando el metodo de repositorio tenant-explicito y el fijado del
 * tenant en la transaccion. La verificacion de la RLS real (PostgreSQL) se cubre en las
 * pruebas de integracion del modulo.</p>
 */
@DisplayName("ServicioEstrategia - visibilidad multi-tenant de la esencia (Req 58.1, 23)")
class ServicioEstrategiaEsenciaTest {

    private static final UUID TENANT = UUID.fromString("daee8f1a-2422-47f2-be47-02e630933d10");

    private EsenciaEmpresaRepository esenciaRepository;
    private ObjetivoEstrategicoRepository objetivoRepository;
    private ResultadoClaveRepository resultadoClaveRepository;
    private HistorialAvanceObjetivoRepository historialRepository;
    private AuditoriaPort auditoria;
    private TenantSessionInitializer tenantSession;
    private ServicioEstrategia servicio;

    @BeforeEach
    void preparar() {
        esenciaRepository = mock(EsenciaEmpresaRepository.class);
        objetivoRepository = mock(ObjetivoEstrategicoRepository.class);
        resultadoClaveRepository = mock(ResultadoClaveRepository.class);
        historialRepository = mock(HistorialAvanceObjetivoRepository.class);
        auditoria = mock(AuditoriaPort.class);
        tenantSession = mock(TenantSessionInitializer.class);
        Clock clock = Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), ZoneOffset.UTC);
        servicio = new ServicioEstrategia(esenciaRepository, objetivoRepository,
                resultadoClaveRepository, historialRepository, auditoria, clock, tenantSession);
        // El tenant se deriva del contexto autenticado (Req 23.4); en la peticion real
        // lo fija TenantResolutionFilter. Aqui se simula el contexto del hilo.
        TenantContext.set(TENANT);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("consultarEsencia devuelve la esencia del tenant propietario (no 404) leyendo por tenant explicito")
    void consultarEsenciaDevuelveLaFilaDelTenantPropietario() {
        EsenciaEmpresa esencia = EsenciaEmpresa.crear(
                "Iluminar espacios", "Ser el referente", "Calidad", "admin");
        when(esenciaRepository.findFirstByTenantIdOrderByCreatedAtAsc(TENANT))
                .thenReturn(Optional.of(esencia));

        EsenciaEmpresaDto dto = servicio.consultarEsencia();

        assertThat(dto.mision()).isEqualTo("Iluminar espacios");
        assertThat(dto.vision()).isEqualTo("Ser el referente");
        assertThat(dto.valores()).isEqualTo("Calidad");
        // La lectura debe filtrar el tenant EXPLICITAMENTE (no depender del filtro
        // de Hibernate, que con open-in-view=false puede no estar habilitado).
        verify(esenciaRepository).findFirstByTenantIdOrderByCreatedAtAsc(TENANT);
        // Y debe fijar app.current_tenant en la transaccion (RLS, Capa 2) para que la
        // fila propia del tenant sea visible.
        verify(tenantSession).applyTenant(TENANT);
    }

    @Test
    @DisplayName("consultarEsencia fija app.current_tenant ANTES de leer (para que la RLS exponga la fila)")
    void consultarEsenciaFijaTenantAntesDeLeer() {
        EsenciaEmpresa esencia = EsenciaEmpresa.crear("m", "v", "val", "admin");
        when(esenciaRepository.findFirstByTenantIdOrderByCreatedAtAsc(TENANT))
                .thenReturn(Optional.of(esencia));

        servicio.consultarEsencia();

        // Orden imprescindible: sin app.current_tenant fijada ANTES del SELECT, la RLS
        // (deny-by-default) ocultaria la fila propia y produciria un falso 404.
        InOrder orden = inOrder(tenantSession, esenciaRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(esenciaRepository).findFirstByTenantIdOrderByCreatedAtAsc(TENANT);
    }

    @Test
    @DisplayName("consultarEsencia devuelve 404 solo cuando el tenant aun no ha registrado su esencia")
    void consultarEsenciaSinRegistroDevuelve404() {
        when(esenciaRepository.findFirstByTenantIdOrderByCreatedAtAsc(TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarEsencia())
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(tenantSession).applyTenant(TENANT);
        verify(esenciaRepository).findFirstByTenantIdOrderByCreatedAtAsc(TENANT);
    }

    @Test
    @DisplayName("guardarEsencia hace upsert leyendo por tenant explicito y fija app.current_tenant")
    void guardarEsenciaUpsertPorTenantExplicito() {
        when(esenciaRepository.findFirstByTenantIdOrderByCreatedAtAsc(TENANT))
                .thenReturn(Optional.empty());
        when(esenciaRepository.save(any(EsenciaEmpresa.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EsenciaEmpresaDto dto = servicio.guardarEsencia("Iluminar", "Referente", "Calidad");

        assertThat(dto.mision()).isEqualTo("Iluminar");
        InOrder orden = inOrder(tenantSession, esenciaRepository);
        orden.verify(tenantSession).applyTenant(TENANT);
        orden.verify(esenciaRepository).findFirstByTenantIdOrderByCreatedAtAsc(TENANT);
        verify(esenciaRepository).save(any(EsenciaEmpresa.class));
        verify(auditoria).registrar(any());
    }
}

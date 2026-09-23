package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de integracion END-TO-END del <strong>alta excluyente Plan/Suscripcion</strong>
 * y de las <strong>acciones de contrato</strong> (Tarea 6.7, Req 4.1-4.5, 8.1,
 * 8.3, 9.2, 1.3) ejercitando los servicios de aplicacion REALES
 * ({@link ServicioEmpresas}, {@link ServicioSuscripciones},
 * {@link ServicioPaquetesSuscripcion}, {@link ServicioPlanes}) sobre el contexto
 * completo de Spring y una base de datos PostgreSQL real (Testcontainers), con la
 * persistencia real (Flyway + JPA + RLS).
 *
 * <h2>Escenarios cubiertos</h2>
 * <ol>
 *   <li><b>Alta con Plan</b> ({@code planId} no nulo): crea la Empresa y su
 *       Contrato de tipo {@link TipoInstrumento#PLAN} en estado
 *       {@link EstadoSuscripcion#ACTIVA} (Req 4.1).</li>
 *   <li><b>Alta con Paquete</b> ({@code paqueteSuscripcionId} no nulo, sin
 *       prueba): crea la Empresa y su Contrato de tipo
 *       {@link TipoInstrumento#SUSCRIPCION} en estado {@code ACTIVA} (Req 4.2).</li>
 *   <li><b>Alta con AMBOS instrumentos</b>: {@link ReglaNegocioException} (422,
 *       Req 4.4).</li>
 *   <li><b>Alta con NINGUNO</b>: {@link ReglaNegocioException} (422, Req 4.3).</li>
 *   <li><b>Alta con prueba</b> ({@code otorgarPrueba} + Paquete que
 *       {@code admitePrueba}): Contrato {@link EstadoSuscripcion#EN_PRUEBA} con
 *       {@code vigenciaFin = inicio + duracionPruebaMeses} (Req 4.5).</li>
 *   <li><b>activarFacturacion</b>: EN_PRUEBA &rarr; ACTIVA marcando
 *       {@code facturacionActivada} (Req 8.1).</li>
 *   <li><b>extenderPrueba</b>: mueve {@code vigenciaFin} manteniendo EN_PRUEBA
 *       (Req 8.3).</li>
 *   <li><b>convertirAPlan</b>: crea un Contrato de Plan y cancela el anterior,
 *       dejando <b>un solo Contrato vigente</b> por Empresa (Req 9.2, 1.3).</li>
 * </ol>
 *
 * <h2>Reloj fijo</h2>
 * <p>{@link ServicioEmpresas} usa {@code LocalDate.now(clock)} para la vigencia
 * del Contrato. Se sustituye el bean {@link Clock} por uno FIJO anclado a
 * {@link #HOY} (via {@link TestConfiguration} anidada, {@link Primary}) para que
 * la {@code vigenciaFin} de la prueba ({@code HOY + N meses}) sea determinista.</p>
 *
 * <h2>Aislamiento RLS y contexto</h2>
 * <p>La tabla {@code suscripcion} tiene RLS ({@code tenant_isolation}). Los
 * servicios fijan {@code app.current_tenant} internamente (via
 * {@code TenantSessionInitializer}) al crear/leer Contratos de la nueva Empresa,
 * por lo que las llamadas se envuelven en transaccion pero NO requieren fijar el
 * {@link TenantContext} para el alta (el super_admin opera en plataforma). Para
 * VERIFICAR los Contratos sembrados por el alta se lee con el
 * {@link TenantContext} del tenant fijado, como haria el filtro web, dentro de
 * una transaccion.</p>
 *
 * <h2>Secretos de PRUEBA</h2>
 * <p>Mismo patron de arranque temprano que los demas ITs end-to-end: secretos de
 * PRUEBA (jamas reales, CONSTANTES) inyectados en un bloque {@code static} y
 * liberados en {@link #limpiarSecretos()}. Nombrada {@code *IT} para Failsafe;
 * bajo {@code -o test} NO se ejecuta, pero compila.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Tarea 6.7 - Alta excluyente Plan/Suscripcion y acciones de contrato END-TO-END (Req 4.x, 8.x, 9.2, 1.3)")
class AltaExcluyenteYAccionesContratoIT {

    static {
        System.setProperty("spring.datasource.username", "crm_test");
        System.setProperty("spring.datasource.password", "crm_test_pwd");
        System.setProperty("crm.secretos.jwt-signing-key",
                "clave-de-firma-jwt-solo-para-pruebas-no-usar-en-produccion-1234567890");
        System.setProperty("crm.cifrado.activa", "v1");
        System.setProperty("crm.cifrado.llaves.v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        System.setProperty("DB_USER", "crm_test");
        System.setProperty("DB_PASSWORD", "crm_test_pwd");
        System.setProperty("JWT_SIGNING_KEY",
                "clave-de-firma-jwt-solo-para-pruebas-no-usar-en-produccion-1234567890");
        System.setProperty("CRM_ENC_KEY_ACTIVE", "v1");
        System.setProperty("CRM_ENC_KEY_V1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        System.setProperty("crm.respaldo.habilitado", "false");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("crm")
                    .withUsername("crm_test")
                    .withPassword("crm_test_pwd");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
    }

    @AfterAll
    static void limpiarSecretos() {
        System.clearProperty("spring.datasource.username");
        System.clearProperty("spring.datasource.password");
        System.clearProperty("crm.secretos.jwt-signing-key");
        System.clearProperty("crm.cifrado.activa");
        System.clearProperty("crm.cifrado.llaves.v1");
        System.clearProperty("DB_USER");
        System.clearProperty("DB_PASSWORD");
        System.clearProperty("JWT_SIGNING_KEY");
        System.clearProperty("CRM_ENC_KEY_ACTIVE");
        System.clearProperty("CRM_ENC_KEY_V1");
        System.clearProperty("crm.respaldo.habilitado");
    }

    /** "Hoy" determinista del reloj fijo (2025-06-15). */
    private static final LocalDate HOY = LocalDate.of(2025, 6, 15);
    /** Meses de prueba del Paquete que admite prueba. */
    private static final int MESES_PRUEBA = 3;
    private static final String MONEDA = "MXN";

    /**
     * Sustituye el bean {@link Clock} por uno FIJO anclado a {@link #HOY}, para
     * que la {@code vigenciaFin} derivada del alta sea determinista.
     */
    @TestConfiguration
    static class RelojFijoConfig {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(HOY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired
    private ServicioEmpresas servicioEmpresas;

    @Autowired
    private ServicioSuscripciones servicioSuscripciones;

    @Autowired
    private ServicioPaquetesSuscripcion servicioPaquetes;

    @Autowired
    private ServicioPlanes servicioPlanes;

    @Autowired
    private SuscripcionRepository suscripcionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // Altas excluyentes (Req 4.1-4.5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Alta con Plan crea Contrato tipo PLAN en estado ACTIVA (Req 4.1)")
    void altaConPlanCreaContratoPlanActiva() {
        UUID giroId = giroAnuncios();
        UUID planId = crearPlan("Plan largo plazo " + unico(), 730);

        EmpresaCreadaDto creada = servicioEmpresas.crearEmpresa(comando(
                "Empresa Plan " + unico(), rfc(), giroId, planId, null, false));

        UUID tenantId = creada.empresa().id();
        List<Suscripcion> contratos = contratosDe(tenantId);
        assertThat(contratos).hasSize(1);
        Suscripcion contrato = contratos.get(0);
        assertThat(contrato.getTipoInstrumento()).isEqualTo(TipoInstrumento.PLAN);
        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(contrato.getPlanId()).isEqualTo(planId);
        assertThat(contrato.getPaqueteSuscripcionId()).isNull();
    }

    @Test
    @DisplayName("Alta con Paquete crea Contrato tipo SUSCRIPCION en estado ACTIVA (Req 4.2)")
    void altaConPaqueteCreaContratoSuscripcion() {
        UUID giroId = giroAnuncios();
        UUID paqueteId = crearPaquete("Paquete corto " + unico(), 180, false, null);

        EmpresaCreadaDto creada = servicioEmpresas.crearEmpresa(comando(
                "Empresa Paquete " + unico(), rfc(), giroId, null, paqueteId, false));

        UUID tenantId = creada.empresa().id();
        List<Suscripcion> contratos = contratosDe(tenantId);
        assertThat(contratos).hasSize(1);
        Suscripcion contrato = contratos.get(0);
        assertThat(contrato.getTipoInstrumento()).isEqualTo(TipoInstrumento.SUSCRIPCION);
        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(contrato.getPaqueteSuscripcionId()).isEqualTo(paqueteId);
        assertThat(contrato.getPlanId()).isNull();
        // Sin prueba: vigenciaFin = hoy + duracionDias del Paquete.
        assertThat(contrato.getVigenciaFin()).isEqualTo(HOY.plusDays(180));
    }

    @Test
    @DisplayName("Alta con AMBOS instrumentos se rechaza con 422 (Req 4.4)")
    void altaConAmbosInstrumentosRechaza() {
        UUID giroId = giroAnuncios();
        UUID planId = crearPlan("Plan ambos " + unico(), 730);
        UUID paqueteId = crearPaquete("Paquete ambos " + unico(), 180, false, null);

        assertThatThrownBy(() -> servicioEmpresas.crearEmpresa(comando(
                "Empresa ambos " + unico(), rfc(), giroId, planId, paqueteId, false)))
                .as("indicar Plan y Paquete a la vez viola la exclusividad (Req 4.4)")
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Alta con NINGUN instrumento se rechaza con 422 (Req 4.3)")
    void altaSinInstrumentoRechaza() {
        UUID giroId = giroAnuncios();

        assertThatThrownBy(() -> servicioEmpresas.crearEmpresa(comando(
                "Empresa ninguno " + unico(), rfc(), giroId, null, null, false)))
                .as("no indicar ni Plan ni Paquete se rechaza (Req 4.3)")
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Alta con prueba crea Contrato EN_PRUEBA con vigenciaFin = inicio + duracionPruebaMeses (Req 4.5)")
    void altaConPruebaCreaContratoEnPrueba() {
        UUID giroId = giroAnuncios();
        UUID paqueteId = crearPaquete("Paquete con prueba " + unico(), 300, true, MESES_PRUEBA);

        EmpresaCreadaDto creada = servicioEmpresas.crearEmpresa(comando(
                "Empresa prueba " + unico(), rfc(), giroId, null, paqueteId, true));

        UUID tenantId = creada.empresa().id();
        Suscripcion contrato = contratosDe(tenantId).get(0);
        assertThat(contrato.getTipoInstrumento()).isEqualTo(TipoInstrumento.SUSCRIPCION);
        assertThat(contrato.getEstado()).isEqualTo(EstadoSuscripcion.EN_PRUEBA);
        // vigenciaFin = inicio (HOY) + duracionPruebaMeses (Req 4.5).
        assertThat(contrato.getVigenciaFin()).isEqualTo(HOY.plusMonths(MESES_PRUEBA));
    }

    // ------------------------------------------------------------------
    // Acciones de contrato (Req 8.1, 8.3, 9.2, 1.3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("activarFacturacion transiciona EN_PRUEBA -> ACTIVA y marca facturacionActivada (Req 8.1)")
    void activarFacturacionDesdeEnPrueba() {
        UUID giroId = giroAnuncios();
        UUID paqueteId = crearPaquete("Paquete activar " + unico(), 300, true, MESES_PRUEBA);
        EmpresaCreadaDto creada = servicioEmpresas.crearEmpresa(comando(
                "Empresa activar " + unico(), rfc(), giroId, null, paqueteId, true));
        UUID tenantId = creada.empresa().id();
        UUID contratoId = contratosDe(tenantId).get(0).getId();

        SuscripcionDto activada = servicioSuscripciones.activarFacturacion(contratoId, null, null);

        assertThat(activada.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        Suscripcion recargada = contratosDe(tenantId).get(0);
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(recargada.isFacturacionActivada()).isTrue();
        // Sin fecha explicita: inicio de facturacion = primer dia del mes siguiente a hoy real.
        assertThat(recargada.getInicioFacturacion()).isNotNull();
    }

    @Test
    @DisplayName("extenderPrueba mueve vigenciaFin manteniendo EN_PRUEBA (Req 8.3)")
    void extenderPruebaMueveVigenciaFin() {
        UUID giroId = giroAnuncios();
        UUID paqueteId = crearPaquete("Paquete extender " + unico(), 300, true, MESES_PRUEBA);
        EmpresaCreadaDto creada = servicioEmpresas.crearEmpresa(comando(
                "Empresa extender " + unico(), rfc(), giroId, null, paqueteId, true));
        UUID tenantId = creada.empresa().id();
        UUID contratoId = contratosDe(tenantId).get(0).getId();

        LocalDate nuevaFin = HOY.plusMonths(MESES_PRUEBA).plusDays(15);
        SuscripcionDto extendida = servicioSuscripciones.extenderPrueba(contratoId, nuevaFin);

        assertThat(extendida.estado()).isEqualTo(EstadoSuscripcion.EN_PRUEBA);
        assertThat(extendida.vigenciaFin()).isEqualTo(nuevaFin);
    }

    @Test
    @DisplayName("convertirAPlan crea Contrato de Plan, cierra el anterior y deja UN solo Contrato vigente (Req 9.2, 1.3)")
    void convertirAPlanMantieneUnSoloVigente() {
        UUID giroId = giroAnuncios();
        UUID paqueteId = crearPaquete("Paquete convertir " + unico(), 300, false, null);
        UUID planDestino = crearPlan("Plan destino " + unico(), 730);
        EmpresaCreadaDto creada = servicioEmpresas.crearEmpresa(comando(
                "Empresa convertir " + unico(), rfc(), giroId, null, paqueteId, false));
        UUID tenantId = creada.empresa().id();
        UUID contratoSuscripcionId = contratosDe(tenantId).get(0).getId();

        SuscripcionDto nuevoPlan = servicioSuscripciones.convertirAPlan(contratoSuscripcionId, planDestino);

        assertThat(nuevoPlan.tipoInstrumento()).isEqualTo(TipoInstrumento.PLAN);
        assertThat(nuevoPlan.planId()).isEqualTo(planDestino);

        // Exclusividad (Req 1.3): tras la conversion, exactamente UN Contrato vigente
        // (estado que otorga acceso: ACTIVA o EN_PRUEBA) por Empresa; el origen queda cancelado.
        List<Suscripcion> todos = contratosDe(tenantId);
        assertThat(todos)
                .as("existen dos filas: el nuevo Contrato de Plan y el de suscripcion cancelado")
                .hasSize(2);
        List<Suscripcion> vigentes = todos.stream()
                .filter(c -> c.getEstado() == EstadoSuscripcion.ACTIVA
                        || c.getEstado() == EstadoSuscripcion.EN_PRUEBA)
                .toList();
        assertThat(vigentes)
                .as("solo un Contrato vigente por Empresa tras convertir (Req 1.3)")
                .hasSize(1);
        assertThat(vigentes.get(0).getTipoInstrumento()).isEqualTo(TipoInstrumento.PLAN);
        // El Contrato de suscripcion original quedo cancelado.
        Suscripcion origen = todos.stream()
                .filter(c -> c.getId().equals(contratoSuscripcionId))
                .findFirst().orElseThrow();
        assertThat(origen.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
    }

    // ------------------------------------------------------------------
    // Utilidades de siembra y verificacion
    // ------------------------------------------------------------------

    /** Comando de alta con instrumento excluyente y admin sin password (se genera). */
    private static CrearEmpresaCommand comando(String nombre, String rfc, UUID giroId,
                                               UUID planId, UUID paqueteId, boolean otorgarPrueba) {
        return new CrearEmpresaCommand(
                nombre, rfc, giroId, planId, paqueteId, otorgarPrueba,
                "admin-" + UUID.randomUUID(), null, null, null);
    }

    /** Crea un Plan real (duracion > 365) con un modulo de Nucleo ('comercial'). */
    private UUID crearPlan(String nombre, int duracionDias) {
        PlanDto dto = servicioPlanes.crearPlan(new CrearPlanCommand(
                nombre, 50, duracionDias, giroAnuncios(), MONEDA,
                Map.of("comercial", new BigDecimal("1000.00"))));
        return dto.id();
    }

    /** Crea un Paquete real (duracion en (0,365]) con un modulo de Nucleo ('comercial'). */
    private UUID crearPaquete(String nombre, int duracionDias, boolean admitePrueba,
                              Integer duracionPruebaMeses) {
        PaqueteSuscripcionDto dto = servicioPaquetes.crearPaquete(new CrearPaqueteSuscripcionCommand(
                nombre, 50, duracionDias, admitePrueba, duracionPruebaMeses, giroAnuncios(), MONEDA,
                Map.of("comercial", new BigDecimal("500.00"))));
        return dto.id();
    }

    /** Id del Giro sembrado por V50 ('anuncios-luminosos'), obligatorio en empresa.giro_id (V51). */
    private UUID giroAnuncios() {
        return transactionTemplate.execute(status -> (UUID) entityManager.createNativeQuery(
                        "SELECT id FROM giro WHERE clave = 'anuncios-luminosos'")
                .getSingleResult());
    }

    /** Lee los Contratos del tenant bajo su contexto RLS (como haria el filtro web). */
    private List<Suscripcion> contratosDe(UUID tenantId) {
        return enContextoTenant(tenantId,
                () -> suscripcionRepository.findByTenantIdOrderByIdAsc(tenantId));
    }

    private <T> T enContextoTenant(UUID tenant, Supplier<T> accion) {
        return transactionTemplate.execute(status -> {
            TenantContext.set(tenant);
            try {
                // Fija app.current_tenant para que la RLS exponga las filas del tenant.
                entityManager.createNativeQuery("SET LOCAL app.current_tenant = '" + tenant + "'")
                        .executeUpdate();
                return accion.get();
            } finally {
                TenantContext.clear();
            }
        });
    }

    /**
     * RFC de persona moral valido (12 caracteres) y unico por prueba: 3 letras +
     * 6 digitos de fecha (AAMMDD) + 3 caracteres de homoclave alfanumericos. La
     * homoclava se deriva de un UUID para evitar colisiones de unicidad
     * ({@code uq_empresa_rfc}) entre pruebas del mismo fork.
     */
    private static String rfc() {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String homoclave = hex.substring(0, 3);
        return "AAA010101" + homoclave;
    }

    private static String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

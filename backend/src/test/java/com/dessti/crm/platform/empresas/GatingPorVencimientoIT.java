package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

import com.dessti.crm.platform.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de integracion END-TO-END del <strong>gating por vencimiento</strong>
 * (Tarea 7.3, Req 6.1-6.4) contra el {@link PlanModulosPlanAdapter} real y una
 * base de datos PostgreSQL real (Testcontainers), con un {@link Clock} FIJO para
 * que la nocion de "hoy" sea determinista.
 *
 * <h2>Que se ejercita</h2>
 * <p>Se invoca directamente {@link PlanModulosPlanAdapter#modulosHabilitadosDe(UUID)}
 * (la fuente unica de verdad del gating) sobre Contratos ({@code suscripcion})
 * sembrados por tenant, y se comprueba el corte por estado y por vigencia:</p>
 * <ol>
 *   <li><b>Concede</b> con un Contrato {@code EN_PRUEBA} vigente
 *       ({@code vigenciaFin >= hoy}): devuelve los modulos del Paquete (Req 6.1).</li>
 *   <li><b>Deniega</b> (cero modulos, mismo resultado que sin Contrato, Req 6.4)
 *       con un Contrato {@code EN_PRUEBA} VENCIDO ({@code vigenciaFin < hoy}).</li>
 *   <li><b>Deniega</b> con un Contrato {@code ACTIVA} VENCIDO
 *       ({@code vigenciaFin < hoy}) (Req 6.2).</li>
 *   <li><b>Deniega</b> con un Contrato {@code SUSPENDIDA} (Req 6.3).</li>
 *   <li><b>Deniega</b> con un Contrato {@code CANCELADA} (Req 6.3).</li>
 *   <li><b>Concede</b> en el borde ESTRICTO {@code vigenciaFin == hoy} (Req 6.1;
 *       confirmado por {@link Suscripcion#estaVencida(LocalDate)}: solo vence si
 *       {@code vigenciaFin.isBefore(hoy)}).</li>
 * </ol>
 *
 * <h2>Reloj fijo</h2>
 * <p>El {@link PlanModulosPlanAdapter} inyecta un bean {@link Clock}. En
 * produccion lo define {@code SecurityConfig.clock()} como {@code Clock.systemUTC()}.
 * Aqui se sustituye por un {@link Clock#fixed(Instant, java.time.ZoneId)} anclado
 * a {@link #HOY} mediante una {@link TestConfiguration} anidada que declara el
 * bean como {@link Primary}, de modo que el adaptador resuelva su {@code Clock}
 * al reloj fijo. Asi la distincion vigente/vencido es exacta e independiente de
 * la fecha real de ejecucion.</p>
 *
 * <h2>Aislamiento RLS</h2>
 * <p>La tabla {@code suscripcion} tiene RLS ({@code tenant_isolation}, V2/V53).
 * La siembra por tenant se hace con {@code EntityManager} nativo dentro de una
 * transaccion con {@code SET LOCAL app.current_tenant} = tenant. El propio
 * adaptador fija el tenant antes de leer (via {@code TenantSessionInitializer}),
 * por lo que las lecturas se envuelven en una transaccion con el
 * {@link TenantContext} fijado, igual que haria el filtro web.</p>
 *
 * <h2>Secretos de PRUEBA</h2>
 * <p>Identico patron de arranque temprano que los demas ITs end-to-end
 * ({@code CuentaBancariaRlsVisibilidadIT}/{@code GatingPorGiroVerticalIT}): los
 * secretos de PRUEBA (jamas reales, valores CONSTANTES) se inyectan como
 * propiedades de sistema en un bloque {@code static} y se liberan en
 * {@link #limpiarSecretos()}. Solo la URL con puerto aleatorio queda en
 * {@link DynamicPropertySource}. Nombrada {@code *IT} para ejecutarse bajo
 * Failsafe; bajo {@code -o test} NO se ejecuta, pero compila.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Tarea 7.3 - Gating por vencimiento END-TO-END con Clock fijo (Req 6.1-6.4)")
class GatingPorVencimientoIT {

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

    /** "Hoy" determinista del reloj fijo (2025-06-15, mediodia UTC). */
    private static final LocalDate HOY = LocalDate.of(2025, 6, 15);

    /**
     * Sustituye el bean {@link Clock} de produccion por uno FIJO anclado a
     * {@link #HOY} (mediodia UTC), para que el corte por vencimiento del
     * {@link PlanModulosPlanAdapter} sea determinista. Se marca {@link Primary}
     * para prevalecer sobre {@code SecurityConfig.clock()} en la inyeccion.
     */
    @TestConfiguration
    static class RelojFijoConfig {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(HOY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

    // Un tenant por escenario (aislamiento entre casos).
    private static final UUID T_PRUEBA_VIGENTE = UUID.fromString("aa000000-0000-0000-0000-000000000001");
    private static final UUID T_PRUEBA_VENCIDA = UUID.fromString("aa000000-0000-0000-0000-000000000002");
    private static final UUID T_ACTIVA_VENCIDA = UUID.fromString("aa000000-0000-0000-0000-000000000003");
    private static final UUID T_SUSPENDIDA = UUID.fromString("aa000000-0000-0000-0000-000000000004");
    private static final UUID T_CANCELADA = UUID.fromString("aa000000-0000-0000-0000-000000000005");
    private static final UUID T_BORDE_HOY = UUID.fromString("aa000000-0000-0000-0000-000000000006");

    /** Paquete de Suscripcion que habilita el modulo Nucleo {@code comercial}. */
    private static final UUID PAQUETE_ID = UUID.fromString("aa000000-0000-0000-0000-0000000000b1");
    private static final String MODULOS_PAQUETE = "[\"comercial\"]";

    @Autowired
    private PlanModulosPlanAdapter gating;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static boolean sembrado;

    @Test
    @DisplayName("(1) Concede modulos con Contrato EN_PRUEBA vigente (vigenciaFin >= hoy) (Req 6.1)")
    void concedeConEnPruebaVigente() {
        sembrarUnaVez();
        // vigenciaFin = hoy + 10 dias (vigente): hereda los modulos del Paquete.
        List<String> modulos = enContextoTenant(T_PRUEBA_VIGENTE,
                () -> gating.modulosHabilitadosDe(T_PRUEBA_VIGENTE));
        assertThat(modulos)
                .as("un Contrato EN_PRUEBA vigente concede los modulos del Paquete (Req 6.1)")
                .containsExactly("comercial");
    }

    @Test
    @DisplayName("(2) Deniega (cero modulos) con Contrato EN_PRUEBA VENCIDO (vigenciaFin < hoy) (Req 6.2, 6.4)")
    void deniegaConEnPruebaVencida() {
        sembrarUnaVez();
        List<String> modulos = enContextoTenant(T_PRUEBA_VENCIDA,
                () -> gating.modulosHabilitadosDe(T_PRUEBA_VENCIDA));
        assertThat(modulos)
                .as("un Contrato EN_PRUEBA vencido deniega todo (mismo resultado que sin Contrato, Req 6.4)")
                .isEmpty();
    }

    @Test
    @DisplayName("(3) Deniega (cero modulos) con Contrato ACTIVA VENCIDO (vigenciaFin < hoy) (Req 6.2, 6.4)")
    void deniegaConActivaVencida() {
        sembrarUnaVez();
        List<String> modulos = enContextoTenant(T_ACTIVA_VENCIDA,
                () -> gating.modulosHabilitadosDe(T_ACTIVA_VENCIDA));
        assertThat(modulos)
                .as("un Contrato ACTIVA vencido deniega todo (Req 6.2, 6.4)")
                .isEmpty();
    }

    @Test
    @DisplayName("(4) Deniega (cero modulos) con Contrato SUSPENDIDA (Req 6.3)")
    void deniegaConSuspendida() {
        sembrarUnaVez();
        List<String> modulos = enContextoTenant(T_SUSPENDIDA,
                () -> gating.modulosHabilitadosDe(T_SUSPENDIDA));
        assertThat(modulos)
                .as("un Contrato SUSPENDIDA no se selecciona: cero modulos (Req 6.3)")
                .isEmpty();
    }

    @Test
    @DisplayName("(5) Deniega (cero modulos) con Contrato CANCELADA (Req 6.3)")
    void deniegaConCancelada() {
        sembrarUnaVez();
        List<String> modulos = enContextoTenant(T_CANCELADA,
                () -> gating.modulosHabilitadosDe(T_CANCELADA));
        assertThat(modulos)
                .as("un Contrato CANCELADA no se selecciona: cero modulos (Req 6.3)")
                .isEmpty();
    }

    @Test
    @DisplayName("(6) Concede en el borde ESTRICTO vigenciaFin == hoy (Req 6.1)")
    void concedeEnBordeVigenciaFinIgualHoy() {
        sembrarUnaVez();
        // vigenciaFin == hoy del reloj fijo: NO esta vencido (estaVencida usa isBefore).
        List<String> modulos = enContextoTenant(T_BORDE_HOY,
                () -> gating.modulosHabilitadosDe(T_BORDE_HOY));
        assertThat(modulos)
                .as("vigenciaFin == hoy concede (borde estricto, Req 6.1)")
                .containsExactly("comercial");
    }

    // ------------------------------------------------------------------
    // Siembra (patron de GatingPorGiroVerticalIT / SaneoOverrideModulosV64IT)
    // ------------------------------------------------------------------

    /**
     * Ejecuta la accion dentro de una transaccion con SOLO el {@link TenantContext}
     * fijado (imita a {@code TenantResolutionFilter}); es el adaptador quien fija
     * {@code app.current_tenant} en su lectura.
     */
    private <T> T enContextoTenant(UUID tenant, Supplier<T> accion) {
        return transactionTemplate.execute(status -> {
            TenantContext.set(tenant);
            try {
                return accion.get();
            } finally {
                TenantContext.clear();
            }
        });
    }

    private synchronized void sembrarUnaVez() {
        if (sembrado) {
            return;
        }
        // Empresas raiz (sin RLS): giro_id obligatorio (V51), se reutiliza el Giro
        // sembrado 'anuncios-luminosos' (V50). El modulo sembrado (comercial) es de
        // Nucleo (giro null), asi que es valido para cualquier Giro.
        crearEmpresa(T_PRUEBA_VIGENTE, "AAA010101AA1");
        crearEmpresa(T_PRUEBA_VENCIDA, "AAA010101AA2");
        crearEmpresa(T_ACTIVA_VENCIDA, "AAA010101AA3");
        crearEmpresa(T_SUSPENDIDA, "AAA010101AA4");
        crearEmpresa(T_CANCELADA, "AAA010101AA5");
        crearEmpresa(T_BORDE_HOY, "AAA010101AA6");

        // Paquete de suscripcion (catalogo sin RLS): habilita el modulo 'comercial'.
        // duracion_dias en (0, 365] por el CHECK de V64.
        crearPaquete(PAQUETE_ID);

        // Contratos de tipo 'suscripcion' con fechas relativas al RELOJ FIJO (HOY).
        // vigencia_inicio siempre <= vigencia_fin.
        crearContratoSuscripcion(T_PRUEBA_VIGENTE, "en_prueba", HOY.minusDays(5), HOY.plusDays(10));
        crearContratoSuscripcion(T_PRUEBA_VENCIDA, "en_prueba", HOY.minusDays(20), HOY.minusDays(1));
        crearContratoSuscripcion(T_ACTIVA_VENCIDA, "activa", HOY.minusDays(30), HOY.minusDays(1));
        crearContratoSuscripcion(T_SUSPENDIDA, "suspendida", HOY.minusDays(5), HOY.plusDays(30));
        crearContratoSuscripcion(T_CANCELADA, "cancelada", HOY.minusDays(5), HOY.plusDays(30));
        crearContratoSuscripcion(T_BORDE_HOY, "en_prueba", HOY.minusDays(5), HOY);
        sembrado = true;
    }

    private void crearEmpresa(UUID id, String rfc) {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO empresa (id, nombre, rfc, estado, giro_id) "
                            + "VALUES (:id, :nombre, :rfc, 'activa', "
                            + "(SELECT id FROM giro WHERE clave='anuncios-luminosos')) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", id)
                    .setParameter("nombre", "Empresa " + rfc)
                    .setParameter("rfc", rfc)
                    .executeUpdate();
            return null;
        });
    }

    private void crearPaquete(UUID id) {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO paquete_suscripcion "
                            + "(id, nombre, max_usuarios, giro_id, modulos_habilitados, "
                            + " duracion_dias, admite_prueba, duracion_prueba_meses) "
                            + "VALUES (:id, :nombre, 50, "
                            + "(SELECT id FROM giro WHERE clave='anuncios-luminosos'), "
                            + "CAST(:modulos AS jsonb), 180, TRUE, 3) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", id)
                    .setParameter("nombre", "Paquete gating IT")
                    .setParameter("modulos", MODULOS_PAQUETE)
                    .executeUpdate();
            return null;
        });
    }

    /**
     * Inserta un Contrato ({@code suscripcion}) de tipo {@code suscripcion} (sin
     * override, hereda del Paquete) para el {@code tenant}, con el estado y la
     * vigencia dados. La tabla tiene FORCE RLS: se fija {@code app.current_tenant}
     * = tenant en la MISMA transaccion para satisfacer {@code tenant_isolation}.
     */
    private void crearContratoSuscripcion(UUID tenant, String estado,
                                          LocalDate vigenciaInicio, LocalDate vigenciaFin) {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("SET LOCAL app.current_tenant = '" + tenant + "'")
                    .executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO suscripcion "
                            + "(id, tenant_id, paquete_suscripcion_id, tipo_instrumento, estado, "
                            + " vigencia_inicio, vigencia_fin) "
                            + "VALUES (:id, :tenant, :paquete, 'suscripcion', :estado, :ini, :fin) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", tenant)
                    .setParameter("tenant", tenant)
                    .setParameter("paquete", PAQUETE_ID)
                    .setParameter("estado", estado)
                    .setParameter("ini", vigenciaInicio)
                    .setParameter("fin", vigenciaFin)
                    .executeUpdate();
            return null;
        });
    }
}

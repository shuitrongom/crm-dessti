package com.dessti.crm.estrategia.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.dessti.crm.estrategia.application.ObjetivoEstrategicoDto;
import com.dessti.crm.estrategia.application.ServicioEstrategia;
import com.dessti.crm.estrategia.domain.ObjetivoEstrategico;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantFilterActivator;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;

/**
 * Prueba de regresion del bug del filtro nulo en
 * {@link ObjetivoEstrategicoRepository#buscarConFiltros} (endpoint
 * {@code GET /estrategia/objetivos}). Arranca el contexto completo de Spring contra
 * una base de datos PostgreSQL real (Testcontainers) porque el fallo SOLO se
 * manifiesta en PostgreSQL: cuando {@code :responsable} llega {@code NULL} (filtro
 * ausente), el planificador type-checkea {@code LOWER(:responsable)} antes del
 * corto-circuito {@code :responsable IS NULL}, infiere el bind no tipado como
 * {@code bytea} y lanza {@code no existe la funcion lower(bytea)} (HTTP 500). El
 * {@code CAST(:responsable AS string)} anadido al JPQL fuerza el tipo textual del
 * bind y resuelve el 500 conservando la semantica de "no filtra".
 *
 * <p>Replica EXACTAMENTE el patron de arranque y aislamiento por tenant de
 * {@code IndicadoresTableroDatosVivosIT} (secretos de PRUEBA como propiedades de
 * sistema en bloque estatico, contenedor PostgreSQL, y ejecucion de las consultas
 * dentro de {@code enTenant} con filtro de Hibernate y variable de sesion de RLS).</p>
 *
 * <p>Nombrada con el sufijo {@code *IT} para ejecutarse bajo Failsafe (integracion) y
 * quedar excluida de {@code mvn test} (Surefire, sin Docker). Bajo {@code -o test}
 * NO se ejecuta, pero compila y valida que el JPQL mapeado siga siendo valido.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Regresion - GET /estrategia/objetivos con responsable nulo no lanza lower(bytea) (Req 58.5, 58.6)")
class ObjetivoEstrategicoFiltroNuloIT {

    static {
        // Se ejecuta al CARGAR la clase, antes de que Spring arranque, para que el
        // EnvironmentPostProcessor de validacion de secretos (Req 11, 67), que corre
        // durante la preparacion del entorno (antes de @DynamicPropertySource y de las
        // properties inline de @SpringBootTest), encuentre los secretos requeridos.
        // No referencia el contenedor: usa los literales fijos de credenciales.
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
        // La URL con puerto aleatorio solo se conoce tras arrancar el contenedor.
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
    }

    @AfterAll
    static void limpiarSecretos() {
        // Las propiedades de sistema son globales del JVM: liberarlas evita filtrar
        // los secretos de PRUEBA a otras clases del mismo fork de Failsafe.
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

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private ServicioEstrategia servicioEstrategia;

    @Autowired
    private ObjetivoEstrategicoRepository objetivoRepository;

    @Autowired
    private TenantFilterActivator tenantFilterActivator;

    @Autowired
    private TenantSessionInitializer tenantSessionInitializer;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static boolean datosSembrados;

    @BeforeAll
    static void marcar() {
        datosSembrados = false;
    }

    @Test
    @DisplayName("listarObjetivos(null, null, ...) devuelve la pagina sin lanzar (reproduce el 500)")
    void listarSinFiltrosNoLanza() {
        sembrarUnaVez();

        Pageable pageable = PageRequest.of(0, 20);
        Page<ObjetivoEstrategicoDto> pagina = enTenant(TENANT,
                () -> {
                    // Antes del fix, esta llamada con responsable=null provocaba
                    // "no existe la funcion lower(bytea)" (HTTP 500).
                    Page<ObjetivoEstrategicoDto> p =
                            servicioEstrategia.listarObjetivos(null, null, pageable);
                    // Materializa la consulta paginada dentro de la transaccion.
                    p.getContent();
                    return p;
                });

        assertThat(pagina.getTotalElements())
                .as("El listado sin filtros debe incluir el objetivo sembrado del tenant")
                .isEqualTo(1L);
        assertThat(pagina.getContent().get(0).responsable()).isEqualTo("Ana Lopez");
    }

    @Test
    @DisplayName("listarObjetivos con filtro por periodo y responsable nulo tampoco lanza")
    void listarPorPeriodoConResponsableNuloNoLanza() {
        sembrarUnaVez();

        Pageable pageable = PageRequest.of(0, 20);
        // Fecha dentro del periodo [2024-01-01, 2024-12-31] del objetivo sembrado.
        LocalDate enPeriodo = LocalDate.of(2024, 6, 15);

        assertThatCode(() -> enTenant(TENANT, () -> {
            Page<ObjetivoEstrategicoDto> p =
                    servicioEstrategia.listarObjetivos(enPeriodo, null, pageable);
            p.getContent();
            return p;
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("listarObjetivos con responsable presente hace coincidencia exacta insensible a mayusculas")
    void listarPorResponsableCoincideSinDistinguirMayusculas() {
        sembrarUnaVez();

        Pageable pageable = PageRequest.of(0, 20);
        Page<ObjetivoEstrategicoDto> pagina = enTenant(TENANT, () -> {
            // "ANA LOPEZ" debe coincidir con "Ana Lopez" (case-insensitive).
            Page<ObjetivoEstrategicoDto> p =
                    servicioEstrategia.listarObjetivos(null, "ANA LOPEZ", pageable);
            p.getContent();
            return p;
        });

        assertThat(pagina.getTotalElements())
                .as("La coincidencia exacta insensible a mayusculas debe encontrar el objetivo")
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Siembra y utilidades (patron de IndicadoresTableroDatosVivosIT)
    // ------------------------------------------------------------------

    private synchronized void sembrarUnaVez() {
        if (datosSembrados) {
            return;
        }
        // La Empresa (tenant) es la raiz del arbol multi-tenant y NO tiene RLS
        // (ver V2): se inserta con SQL nativo fuera de cualquier contexto de tenant,
        // con id FIJO para satisfacer la FK tenant_id de objetivo_estrategico.
        crearEmpresa(TENANT, "CCC010101CCC");

        enTenant(TENANT, () -> {
            objetivoRepository.save(ObjetivoEstrategico.crear(
                    "Crecer ventas 2024",
                    "Ana Lopez",
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 12, 31),
                    "Incrementar ventas 20%",
                    "sistema"));
            return null;
        });
        datosSembrados = true;
    }

    private <T> T enTenant(UUID tenant, Supplier<T> accion) {
        return transactionTemplate.execute(status -> {
            TenantContext.set(tenant);
            try {
                tenantSessionInitializer.applyTenant(tenant);
                tenantFilterActivator.enableFilter(tenant);
                T resultado = accion.get();
                entityManager.flush();
                return resultado;
            } finally {
                TenantContext.clear();
            }
        });
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
}

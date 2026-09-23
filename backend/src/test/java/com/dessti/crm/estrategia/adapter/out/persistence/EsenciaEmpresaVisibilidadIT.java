package com.dessti.crm.estrategia.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.dessti.crm.estrategia.application.EsenciaEmpresaDto;
import com.dessti.crm.estrategia.application.ServicioEstrategia;
import com.dessti.crm.platform.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de regresion del bug del 404 en {@code GET /estrategia/esencia}: una Empresa
 * que ya registro su esencia (mision/vision/valores) debe verla al consultarla (no
 * 404). Arranca el contexto completo de Spring contra una base PostgreSQL real
 * (Testcontainers) porque el fallo SOLO se manifiesta con la Row-Level Security de
 * PostgreSQL activa (Capa 2, V38): con {@code open-in-view=false}, si el servicio no
 * fija {@code app.current_tenant} en su PROPIA transaccion de lectura, la RLS
 * (deny-by-default) oculta la fila propia del tenant y {@code consultarEsencia}
 * lanzaba {@code RecursoNoEncontradoException} (HTTP 404) aun existiendo la esencia.
 *
 * <p><strong>Clave de la reproduccion:</strong> a diferencia de otras pruebas, el
 * helper {@link #enContextoTenant(UUID, Supplier)} de LECTURA solo fija el
 * {@link TenantContext} (como haria {@code TenantResolutionFilter} en el filtro web)
 * y <em>NO</em> habilita el filtro de Hibernate ni fija {@code app.current_tenant} a
 * mano: es el propio {@link ServicioEstrategia} el que debe fijar la RLS en su
 * transaccion. Asi la prueba falla con el codigo anterior y pasa con el fix.</p>
 *
 * <p>Replica el patron de arranque/aislamiento de {@code ObjetivoEstrategicoFiltroNuloIT}.
 * Nombrada {@code *IT} para ejecutarse bajo Failsafe (integracion) y quedar excluida de
 * {@code mvn test} (Surefire, sin Docker); bajo {@code -o test} NO se ejecuta, pero
 * compila.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Regresion - GET /estrategia/esencia devuelve la esencia del tenant propietario, no 404 (Req 58.1, 23)")
class EsenciaEmpresaVisibilidadIT {

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

    private static final UUID TENANT = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private ServicioEstrategia servicioEstrategia;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static boolean empresaSembrada;

    @BeforeAll
    static void marcar() {
        empresaSembrada = false;
    }

    @Test
    @DisplayName("guardarEsencia y luego consultarEsencia (en transaccion aparte) devuelve la esencia (no 404)")
    void consultarEsenciaTrasGuardarDevuelveLaFila() {
        sembrarEmpresaUnaVez();

        // 1) La Empresa registra su esencia (escritura). El servicio fija el tenant
        //    y persiste la fila con tenant_id = TENANT.
        enContextoTenant(TENANT, () -> servicioEstrategia.guardarEsencia(
                "Iluminar espacios comerciales", "Ser el referente en anuncios", "Calidad"));

        // 2) En una transaccion NUEVA, con solo el TenantContext fijado (como el filtro
        //    web), se consulta la esencia. El servicio DEBE fijar app.current_tenant en
        //    su propia transaccion de lectura para que la RLS exponga la fila propia.
        EsenciaEmpresaDto dto = enContextoTenant(TENANT, () -> servicioEstrategia.consultarEsencia());

        assertThat(dto).isNotNull();
        assertThat(dto.mision()).isEqualTo("Iluminar espacios comerciales");
        assertThat(dto.vision()).isEqualTo("Ser el referente en anuncios");
        assertThat(dto.valores()).isEqualTo("Calidad");
    }

    // ------------------------------------------------------------------
    // Siembra y utilidades
    // ------------------------------------------------------------------

    /**
     * Ejecuta la accion dentro de una transaccion con SOLO el {@link TenantContext}
     * fijado (imita a {@code TenantResolutionFilter} en el filtro web). No habilita el
     * filtro de Hibernate ni fija {@code app.current_tenant}: es el servicio quien debe
     * hacerlo en su transaccion. Asi se reproduce el bug del 404 con el codigo anterior.
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

    private synchronized void sembrarEmpresaUnaVez() {
        if (empresaSembrada) {
            return;
        }
        // La Empresa (tenant) es la raiz del arbol multi-tenant y NO tiene RLS (V2):
        // se inserta con SQL nativo fuera de contexto de tenant, con id FIJO para
        // satisfacer la FK tenant_id de esencia_empresa.
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO empresa (id, nombre, rfc, estado, giro_id) "
                            + "VALUES (:id, :nombre, :rfc, 'activa', "
                            + "(SELECT id FROM giro WHERE clave='anuncios-luminosos')) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", TENANT)
                    .setParameter("nombre", "Empresa DDD")
                    .setParameter("rfc", "DDD010101DDD")
                    .executeUpdate();
            return null;
        });
        empresaSembrada = true;
    }
}

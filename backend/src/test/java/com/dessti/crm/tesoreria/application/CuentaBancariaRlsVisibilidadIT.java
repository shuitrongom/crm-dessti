package com.dessti.crm.tesoreria.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
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

import com.dessti.crm.platform.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de infraestructura del fix de ordenacion del {@code TenantRlsAspect}
 * (Capa 2 RLS, Req 23) sobre un modulo de negocio DISTINTO del de estrategia:
 * {@code tesoreria} / {@code Cuenta_Bancaria}.
 *
 * <p><strong>Que demuestra:</strong> {@link ServicioTesoreria} NO fija
 * {@code app.current_tenant} de forma explicita (no llama a {@code applyTenant});
 * depende del aspecto transaccional para que la RLS de PostgreSQL exponga sus
 * propias filas. Antes del fix, el aspecto corria FUERA de la transaccion y el
 * {@code SET LOCAL} se descartaba, por lo que una lectura {@code @Transactional
 * (readOnly)} devolvia CERO filas (deny-by-default) aun existiendo la cuenta del
 * tenant. Con el fix (interceptor tx externo, aspecto interno) el aspecto fija el
 * tenant sobre la conexion transaccional y la lectura devuelve la fila propia.</p>
 *
 * <p><strong>Clave de la reproduccion:</strong> el helper
 * {@link #enContextoTenant(UUID, Supplier)} de LECTURA solo fija el
 * {@link TenantContext} (como haria {@code TenantResolutionFilter} en el filtro
 * web) y <em>NO</em> habilita el filtro de Hibernate ni fija
 * {@code app.current_tenant} a mano: es la infraestructura (el aspecto) quien
 * debe hacerlo en la transaccion del servicio. Asi la prueba fallaria con el
 * codigo anterior y pasa con el fix, probando que el arreglo es GENERAL (no solo
 * para los servicios que llaman applyTenant a mano).</p>
 *
 * <p>Replica el patron de arranque/aislamiento de
 * {@code EsenciaEmpresaVisibilidadIT}. Nombrada {@code *IT} para ejecutarse bajo
 * Failsafe (integracion) y quedar excluida de {@code mvn test} (Surefire, sin
 * Docker); bajo {@code -o test} NO se ejecuta, pero compila.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Infra RLS - lectura tenant-scoped SIN applyTenant explicito ve la fila propia (Req 23, tesoreria)")
class CuentaBancariaRlsVisibilidadIT {

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

    private static final UUID TENANT = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Autowired
    private ServicioTesoreria servicioTesoreria;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("crearCuentaBancaria y luego consultar/listar (en transaccion aparte) ven la cuenta del tenant")
    void lecturaTrasEscrituraVeLaFilaDelTenant() {
        sembrarEmpresa();

        // 1) La Empresa da de alta una Cuenta_Bancaria (escritura). El aspecto fija
        //    app.current_tenant en la transaccion del servicio; la fila queda con
        //    tenant_id = TENANT.
        CuentaBancariaDto creada = enContextoTenant(TENANT, () ->
                servicioTesoreria.crearCuentaBancaria(new CrearCuentaBancariaCommand(
                        "Cuenta operativa", "Banco de Prueba", null, "MXN")));

        assertThat(creada).isNotNull();
        assertThat(creada.id()).isNotNull();

        // 2) En una transaccion NUEVA, con SOLO el TenantContext fijado (como el filtro
        //    web), se consulta y lista. El aspecto DEBE fijar app.current_tenant en la
        //    transaccion de lectura para que la RLS exponga la fila propia. Con el bug
        //    anterior devolveria 404/pagina vacia.
        CuentaBancariaDto consultada = enContextoTenant(TENANT,
                () -> servicioTesoreria.consultarCuenta(creada.id()));
        assertThat(consultada).isNotNull();
        assertThat(consultada.id()).isEqualTo(creada.id());
        assertThat(consultada.nombre()).isEqualTo("Cuenta operativa");

        Pageable pageable = PageRequest.of(0, 20);
        long total = enContextoTenant(TENANT, () -> {
            var pagina = servicioTesoreria.listarCuentas(null, pageable);
            pagina.getContent();
            return pagina.getTotalElements();
        });
        assertThat(total)
                .as("El listado tenant-scoped debe incluir la cuenta sembrada del tenant")
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Siembra y utilidades (patron de EsenciaEmpresaVisibilidadIT)
    // ------------------------------------------------------------------

    /**
     * Ejecuta la accion dentro de una transaccion con SOLO el {@link TenantContext}
     * fijado (imita a {@code TenantResolutionFilter}). No habilita el filtro de
     * Hibernate ni fija {@code app.current_tenant}: es el aspecto quien debe hacerlo
     * en la transaccion del servicio. Asi se prueba que el fix es general.
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

    private void sembrarEmpresa() {
        // La Empresa (tenant) es la raiz del arbol multi-tenant y NO tiene RLS (V2):
        // se inserta con SQL nativo fuera de contexto de tenant, con id FIJO para
        // satisfacer la FK tenant_id de cuenta_bancaria.
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO empresa (id, nombre, rfc, estado, giro_id) "
                            + "VALUES (:id, :nombre, :rfc, 'activa', "
                            + "(SELECT id FROM giro WHERE clave='anuncios-luminosos')) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", TENANT)
                    .setParameter("nombre", "Empresa EEE")
                    .setParameter("rfc", "EEE010101EEE")
                    .executeUpdate();
            return null;
        });
    }
}

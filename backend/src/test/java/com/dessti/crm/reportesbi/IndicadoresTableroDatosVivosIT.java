package com.dessti.crm.reportesbi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

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

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantFilterActivator;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;
import com.dessti.crm.reportesbi.application.IndicadorDto;
import com.dessti.crm.reportesbi.application.IndicadoresAreaDto;
import com.dessti.crm.reportesbi.application.InteligenciaNegocioDto;
import com.dessti.crm.reportesbi.application.ServicioInteligenciaNegocio;
import com.dessti.crm.reportesbi.application.ServicioTablero;
import com.dessti.crm.reportesbi.application.TableroDto;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de integracion de la Tarea 56.2 (Req 22, 48, 23): arranca el contexto completo
 * de Spring contra una base de datos PostgreSQL real (Testcontainers) y verifica que, con
 * datos sembrados por area en un tenant, el <strong>Tablero</strong> (Req 22) devuelve
 * indicadores calculados <strong>distintos de cero</strong> a partir de los adaptadores
 * reales de indicadores (Tarea 56.1), respetando el aislamiento por tenant (Req 23): otro
 * tenant no ve los datos del primero. Ademas comprueba el consolidado de Inteligencia de
 * Negocio con comparativo por periodo (Req 48.1).
 *
 * <h2>Aislamiento por tenant (Capa 1)</h2>
 * <p>La siembra y las consultas se realizan dentro de transacciones en las que se fija el
 * {@link TenantContext}, se habilita el filtro global de Hibernate
 * ({@link TenantFilterActivator}) y se aplica la variable de sesion de RLS
 * ({@link TenantSessionInitializer}), replicando lo que el filtro de resolucion hace por
 * peticion en produccion. Asi las agregaciones de los adaptadores quedan acotadas al
 * tenant vigente sin que el {@code tenant_id} viaje en el filtro de indicadores.</p>
 *
 * <p>Nombrada con el sufijo {@code *IT} para ejecutarse bajo Failsafe (integracion) y
 * quedar excluida del {@code mvn test} (Surefire), que corre sin Docker.</p>
 *
 * <h2>Secretos de PRUEBA y arranque temprano</h2>
 * <p>El validador de secretos ({@code SecretosEnvironmentPostProcessor}) es un
 * {@code EnvironmentPostProcessor} que corre <strong>muy temprano</strong> (durante la
 * preparacion del entorno), <em>antes</em> de que se apliquen los valores de
 * {@link DynamicPropertySource} y las {@code properties} inline de {@link SpringBootTest}.
 * Por eso los secretos de PRUEBA (jamas reales) con valor CONSTANTE se inyectan como
 * <strong>propiedades de sistema</strong> en un bloque {@code static} que se ejecuta al
 * cargar la clase: las propiedades de sistema son un {@code PropertySource} estandar
 * visible para los {@code EnvironmentPostProcessor}. Solo la URL con puerto aleatorio
 * (conocida tras arrancar el contenedor) queda en {@link DynamicPropertySource}. Las
 * propiedades de sistema se liberan en {@link #limpiarSecretos()} ({@code @AfterAll})
 * para no filtrarlas a otras pruebas del mismo <em>fork</em>.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Tarea 56.2 - Tablero/BI con datos vivos y aislamiento por tenant (Req 22, 48, 23)")
class IndicadoresTableroDatosVivosIT {

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
        // Nombres de variable que application.yml interpola en esa misma fase temprana.
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
        // El validador temprano de secretos no revisa la URL (solo usuario/clave y
        // la llave JWT, ya cubiertos por propiedades de sistema); el DataSource se
        // crea tarde durante el refresh, por lo que la URL puede quedar aqui. El
        // usuario/clave del contenedor coinciden con los literales fijos y se
        // repiten aqui de forma inofensiva para alimentar tambien el bean DataSource.
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

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    // Filas padre FIJAS del tenant A que satisfacen las FK NOT NULL de
    // orden_fabricacion (cotizacion_id -> cotizacion(id), cliente_id -> cliente(id))
    // y la UNIQUE(tenant_id, cotizacion_id): un Cliente y dos Cotizaciones distintas.
    private static final UUID CLIENTE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COTIZACION_A1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COTIZACION_A2 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private ServicioTablero servicioTablero;

    @Autowired
    private ServicioInteligenciaNegocio servicioInteligenciaNegocio;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private OrdenFabricacionRepository ordenFabricacionRepository;

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
    @DisplayName("El Tablero del tenant A muestra indicadores vivos (no cero) y el tenant B queda aislado")
    void tableroConDatosVivosYAislamiento() {
        sembrarUnaVez();

        // --- Tenant A: ve sus datos vivos (no cero) ---
        TableroDto tableroA = enTenant(TENANT_A,
                () -> servicioTablero.consultarTablero(null, null, null, false));

        Map<String, IndicadorDto> inventarioA = indicadoresDe(tableroA, AreaIndicador.INVENTARIO);
        // Tenant A sembro 3 materiales con stock minimo > 0 (todos por debajo del minimo).
        assertThat(inventarioA.get("materiales_bajo_stock_minimo").valor())
                .as("El tenant A debe ver sus materiales bajo stock minimo")
                .isEqualByComparingTo("3");
        assertThat(inventarioA.get("materiales_activos").valor()).isEqualByComparingTo("3");

        Map<String, IndicadorDto> produccionA = indicadoresDe(tableroA, AreaIndicador.PRODUCCION);
        assertThat(produccionA.get("ordenes_fabricacion_pendientes").valor())
                .as("El tenant A debe ver sus ordenes de fabricacion pendientes")
                .isEqualByComparingTo("2");

        // --- Tenant B: aislamiento (no ve los datos de A) ---
        TableroDto tableroB = enTenant(TENANT_B,
                () -> servicioTablero.consultarTablero(null, null, null, false));

        Map<String, IndicadorDto> inventarioB = indicadoresDe(tableroB, AreaIndicador.INVENTARIO);
        assertThat(inventarioB.get("materiales_bajo_stock_minimo").valor())
                .as("El tenant B solo debe ver su unico material bajo stock minimo, no los de A")
                .isEqualByComparingTo("1");

        Map<String, IndicadorDto> produccionB = indicadoresDe(tableroB, AreaIndicador.PRODUCCION);
        assertThat(produccionB.get("ordenes_fabricacion_pendientes").valor())
                .as("El tenant B no debe ver las ordenes de fabricacion de A")
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El consolidado de BI del tenant A calcula comparativo por periodo (Req 48.1)")
    void consolidadoConComparativoPorPeriodo() {
        sembrarUnaVez();

        LocalDate desde = LocalDate.now().minusDays(3);
        LocalDate hasta = LocalDate.now();

        InteligenciaNegocioDto consolidado = enTenant(TENANT_A,
                () -> servicioInteligenciaNegocio.consolidado(desde, hasta, null, null, false));

        // Al acotar el periodo, el consolidado deriva el periodo anterior de igual longitud.
        assertThat(consolidado.desdeComparativo()).isNotNull();
        assertThat(consolidado.hastaComparativo()).isEqualTo(desde.minusDays(1));

        Map<String, IndicadorDto> inventario =
                indicadoresDeConsolidado(consolidado, AreaIndicador.INVENTARIO);
        // Las existencias son estado actual (no acotadas por fecha): el tenant A las ve.
        assertThat(inventario.get("materiales_bajo_stock_minimo").valor())
                .isEqualByComparingTo("3");
    }

    // ------------------------------------------------------------------
    // Siembra y utilidades
    // ------------------------------------------------------------------

    private synchronized void sembrarUnaVez() {
        if (datosSembrados) {
            return;
        }
        // Las dos Empresas (tenants) son la RAIZ del arbol multi-tenant: toda tabla
        // tenant-scoped (Material, OrdenFabricacion, ...) tiene una FK
        // tenant_id -> empresa(id). Por eso sus filas padre DEBEN existir ANTES de
        // sembrar cualquier dato tenant-scoped, o el INSERT viola
        // "fk_material_empresa". La tabla empresa NO tiene RLS (es la entidad tenant;
        // ver V2__rls_multi_tenant.sql), asi que se inserta con SQL nativo fuera de
        // cualquier contexto/filtro de tenant. Se usa un id FIJO (TENANT_A/TENANT_B),
        // por lo que no sirve Empresa.crear(...), que genera un id aleatorio interno.
        crearEmpresa(TENANT_A, "AAA010101AAA");
        crearEmpresa(TENANT_B, "BBB010101BBB");

        // Tenant A: 3 materiales (stock minimo > 0 => bajo minimo) y 2 ordenes de fabricacion.
        enTenant(TENANT_A, () -> {
            materialRepository.save(Material.crear("Acrilico A", "m2", new BigDecimal("10"), "sistema"));
            materialRepository.save(Material.crear("Vinil A", "m2", new BigDecimal("5"), "sistema"));
            materialRepository.save(Material.crear("Tornillo A", "pieza", new BigDecimal("100"), "sistema"));
            // orden_fabricacion tiene FK NOT NULL a cotizacion(id) y a cliente(id) mas
            // UNIQUE(tenant_id, cotizacion_id): hay que sembrar ANTES sus filas padre
            // con ids FIJOS. cliente y cotizacion son tenant-scoped CON RLS (V11/V14),
            // por lo que estos INSERT nativos deben correr con app.current_tenant fijada
            // = por eso van DENTRO de enTenant(TENANT_A) (a diferencia de crearEmpresa,
            // que es plano porque empresa NO tiene RLS). La WITH CHECK de RLS exige
            // tenant_id = current_setting('app.current_tenant'), que enTenant ya aplica.
            sembrarClienteYCotizacionesA();
            // Ambas OF usan el mismo Cliente (CLIENTE_A) y cotizaciones distintas para
            // respetar la UNIQUE(tenant_id, cotizacion_id). generar(...) las deja en
            // estado 'pendiente' por defecto: cuentan como pendientes del tenant A.
            ordenFabricacionRepository.save(
                    OrdenFabricacion.generar(COTIZACION_A1, CLIENTE_A, "sistema"));
            ordenFabricacionRepository.save(
                    OrdenFabricacion.generar(COTIZACION_A2, CLIENTE_A, "sistema"));
            return null;
        });
        // Tenant B: 1 material (bajo minimo), sin ordenes de fabricacion.
        enTenant(TENANT_B, () -> {
            materialRepository.save(Material.crear("Acrilico B", "m2", new BigDecimal("7"), "sistema"));
            return null;
        });
        datosSembrados = true;
    }

    /**
     * Ejecuta {@code accion} en una transaccion con el contexto del tenant indicado, el
     * filtro global de Hibernate habilitado y la variable de sesion de RLS aplicada,
     * replicando el comportamiento por peticion de produccion.
     */
    private <T> T enTenant(UUID tenant, Supplier<T> accion) {
        return transactionTemplate.execute(status -> {
            TenantContext.set(tenant);
            try {
                tenantSessionInitializer.applyTenant(tenant);
                tenantFilterActivator.enableFilter(tenant);
                T resultado = accion.get();
                // Materializa las escrituras pendientes antes de cerrar la transaccion.
                entityManager.flush();
                return resultado;
            } finally {
                TenantContext.clear();
            }
        });
    }

    /**
     * Inserta la fila padre {@code empresa} (raiz del tenant) con un id FIJO mediante
     * SQL nativo, en su propia transaccion y SIN fijar el filtro/contexto de tenant.
     * <p>La tabla {@code empresa} es la entidad tenant (su PK actua como
     * {@code tenant_id}) y NO tiene Row-Level Security (decision documentada en
     * {@code V2__rls_multi_tenant.sql}), por lo que un INSERT plano de plataforma
     * funciona sin {@code app.current_tenant}. Se usa un id explicito porque
     * {@link Empresa#crear} genera un id aleatorio y aqui necesitamos exactamente
     * {@code TENANT_A}/{@code TENANT_B} para satisfacer las FKs de los datos
     * tenant-scoped. Es idempotente ({@code ON CONFLICT (id) DO NOTHING}).
     *
     * @param id  id fijo de la Empresa (== tenant_id de los datos sembrados).
     * @param rfc RFC valido (VARCHAR(13), NOT NULL) para la fila.
     */
    private void crearEmpresa(UUID id, String rfc) {
        transactionTemplate.execute(status -> {
            // Se listan las columnas NOT NULL sin DEFAULT (nombre, rfc) mas el id fijo y
            // giro_id; estado/version/timestamps toman sus DEFAULT del esquema (V1).
            // giro_id es OBLIGATORIO desde V51 (empresa.giro_id UUID NOT NULL con FK a
            // giro): se enlaza por subconsulta al Giro `anuncios-luminosos` sembrado por
            // V50, mas robusto que hardcodear su UUID fijo.
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

    /**
     * Siembra, con ids FIJOS y SQL nativo, las filas padre tenant-scoped que las FK NOT
     * NULL de {@code orden_fabricacion} exigen para el tenant A: un {@code cliente}
     * ({@link #CLIENTE_A}) y dos {@code cotizacion} distintas ({@link #COTIZACION_A1},
     * {@link #COTIZACION_A2}). Las dos cotizaciones garantizan
     * {@code UNIQUE(tenant_id, cotizacion_id)} en las dos ordenes.
     * <p><strong>DEBE invocarse DENTRO de {@code enTenant(TENANT_A, ...)}</strong>: ambas
     * tablas tienen Row-Level Security con {@code WITH CHECK (tenant_id =
     * current_setting('app.current_tenant'))} (V11/V14). {@code enTenant} fija esa
     * variable de sesion, por lo que el INSERT con {@code tenant_id = TENANT_A} pasa la
     * politica. Solo se listan las columnas NOT NULL sin DEFAULT (mas {@code estado},
     * dado explicito con un valor permitido por su CHECK); {@code activo}, {@code version},
     * {@code subtotal}, {@code total} y los timestamps toman sus DEFAULT del esquema. Es
     * idempotente ({@code ON CONFLICT (id) DO NOTHING}).
     */
    private void sembrarClienteYCotizacionesA() {
        // cliente: NOT NULL sin DEFAULT => tenant_id, nombre, rfc (V11). El resto usa DEFAULT.
        entityManager.createNativeQuery(
                "INSERT INTO cliente (id, tenant_id, nombre, rfc) "
                        + "VALUES (:id, :tenant, :nombre, :rfc) "
                        + "ON CONFLICT (id) DO NOTHING")
                .setParameter("id", CLIENTE_A)
                .setParameter("tenant", TENANT_A)
                .setParameter("nombre", "Cliente A")
                .setParameter("rfc", "CLA010101AAA")
                .executeUpdate();

        // cotizacion: NOT NULL sin DEFAULT => tenant_id, cliente_id (V14). estado se da
        // explicito con un valor permitido por ck_cotizacion_estado; el resto usa DEFAULT.
        // El valor de estado solo debe satisfacer su CHECK: el indicador de OF cuenta
        // ordenes por su propio estado, no por el de la cotizacion.
        for (UUID cotizacionId : List.of(COTIZACION_A1, COTIZACION_A2)) {
            entityManager.createNativeQuery(
                    "INSERT INTO cotizacion (id, tenant_id, cliente_id, estado) "
                            + "VALUES (:id, :tenant, :cliente, 'aprobada') "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", cotizacionId)
                    .setParameter("tenant", TENANT_A)
                    .setParameter("cliente", CLIENTE_A)
                    .executeUpdate();
        }
    }

    private static Map<String, IndicadorDto> indicadoresDe(TableroDto tablero, AreaIndicador area) {
        return tablero.areas().stream()
                .filter(a -> a.area().equals(area.etiqueta()))
                .findFirst()
                .map(IndicadoresTableroDatosVivosIT::indexar)
                .orElseThrow();
    }

    private static Map<String, IndicadorDto> indicadoresDeConsolidado(
            InteligenciaNegocioDto consolidado, AreaIndicador area) {
        return consolidado.areas().stream()
                .filter(a -> a.area().equals(area.etiqueta()))
                .findFirst()
                .map(IndicadoresTableroDatosVivosIT::indexar)
                .orElseThrow();
    }

    private static Map<String, IndicadorDto> indexar(IndicadoresAreaDto area) {
        return area.indicadores().stream()
                .collect(Collectors.toMap(IndicadorDto::clave, d -> d));
    }
}

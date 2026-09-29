package com.dessti.crm.comercial.producto.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
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

import com.dessti.crm.comercial.producto.application.ServicioSeleccionPrecio;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;
import com.dessti.crm.comercial.producto.domain.ListaPrecios;
import com.dessti.crm.comercial.producto.domain.PrecioProducto;
import com.dessti.crm.comercial.producto.domain.Producto;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.tenant.TenantFilterActivator;
import com.dessti.crm.platform.tenant.TenantSessionInitializer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de integracion (Req 59.12): la sugerencia de precio NUNCA propone el
 * precio de un Producto <strong>inactivo</strong> (dado de baja logica, Req 59.6),
 * aun cuando tenga un precio asignado en una Lista_Precios activa y vigente.
 *
 * <p>Arranca el contexto completo de Spring contra una base PostgreSQL real
 * (Testcontainers) porque la regla vive en el JPQL de
 * {@link PrecioProductoRepository#buscarPreciosVigentes} (el JOIN con
 * {@code Producto} filtrando {@code pr.activo = true}); un doble de Mockito no
 * ejercitaria ese filtro. Sigue el patron de {@code ObjetivoEstrategicoFiltroNuloIT}.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Req 59.12 - La sugerencia de precio excluye Productos inactivos (JPQL contra PostgreSQL real)")
class SeleccionPrecioProductoInactivoIT {

    static {
        // Se ejecuta al CARGAR la clase, antes de que Spring arranque, para que el
        // EnvironmentPostProcessor de validacion de secretos (Req 11, 67) encuentre
        // los secretos requeridos. No referencia el contenedor: literales fijos.
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
    private static final LocalDate HOY = LocalDate.of(2026, 6, 1);
    private static final String ACTOR = "ventas";

    @Autowired
    private ServicioSeleccionPrecio servicioSeleccionPrecio;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ListaPreciosRepository listaPreciosRepository;

    @Autowired
    private PrecioProductoRepository precioProductoRepository;

    @Autowired
    private TenantFilterActivator tenantFilterActivator;

    @Autowired
    private TenantSessionInitializer tenantSessionInitializer;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static UUID productoActivoId;
    private static UUID productoInactivoId;
    private static boolean datosSembrados;

    @BeforeAll
    static void reset() {
        datosSembrados = false;
    }

    @Test
    @DisplayName("un Producto ACTIVO con precio en lista vigente SI sugiere precio (control positivo)")
    void productoActivoSugierePrecio() {
        sembrarUnaVez();

        Optional<BigDecimal> precio = enTenant(TENANT, () ->
                servicioSeleccionPrecio.sugerirPrecioUnitario(
                        new ConsultaSugerenciaPrecio(productoActivoId, null, HOY)));

        assertThat(precio)
                .as("un Producto activo con precio en lista vigente debe sugerir su precio")
                .contains(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("un Producto INACTIVO con precio en lista vigente NO sugiere precio (Req 59.12)")
    void productoInactivoNoSugierePrecio() {
        sembrarUnaVez();

        Optional<BigDecimal> precio = enTenant(TENANT, () ->
                servicioSeleccionPrecio.sugerirPrecioUnitario(
                        new ConsultaSugerenciaPrecio(productoInactivoId, null, HOY)));

        assertThat(precio)
                .as("un Producto dado de baja NO debe sugerir precio aunque tenga uno en lista vigente")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Siembra y utilidades (patron de ObjetivoEstrategicoFiltroNuloIT)
    // ------------------------------------------------------------------

    private synchronized void sembrarUnaVez() {
        if (datosSembrados) {
            return;
        }
        crearEmpresa(TENANT, "DDD010101DDD");

        enTenant(TENANT, () -> {
            // Lista general activa y vigente que cubre HOY.
            ListaPrecios lista = listaPreciosRepository.save(ListaPrecios.crear(
                    "General 2026", 1, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), ACTOR));

            // Producto activo con precio en la lista vigente.
            Producto activo = productoRepository.save(Producto.crear(
                    "Anuncio activo", "pieza", "desc", null, null, null, null, ACTOR));
            precioProductoRepository.save(PrecioProducto.crear(
                    lista.getId(), activo.getId(), new BigDecimal("100.00"), ACTOR));
            productoActivoId = activo.getId();

            // Producto con precio en la MISMA lista vigente, pero dado de baja logica.
            Producto inactivo = productoRepository.save(Producto.crear(
                    "Anuncio retirado", "pieza", "desc", null, null, null, null, ACTOR));
            precioProductoRepository.save(PrecioProducto.crear(
                    lista.getId(), inactivo.getId(), new BigDecimal("250.00"), ACTOR));
            inactivo.desactivar(ACTOR);
            productoRepository.save(inactivo);
            productoInactivoId = inactivo.getId();
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

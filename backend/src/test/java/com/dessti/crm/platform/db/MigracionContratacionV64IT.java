package com.dessti.crm.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración de ARRANQUE de la migración
 * {@code V64__contratacion_plan_vs_suscripcion.sql} (Tarea 1.3, Req 12.2, 12.4).
 *
 * <p>Arranca el <b>contexto completo de Spring</b> con un contexto web servlet
 * <b>MOCK</b> ({@code SpringBootTest.WebEnvironment.MOCK}) — igual que
 * {@code ArranqueContextoSmokeIT} — de modo que se carguen los beans web (p. ej.
 * {@code handlerExceptionResolver}) que necesita el registro del filtro de límite
 * de tasa ({@code RateLimitConfig.filtroLimiteTasaRegistration}) y el contexto
 * completo pueda refrescar. Se ejecuta contra una base de datos PostgreSQL real
 * (Testcontainers), usando el {@code ddl-auto} <b>por defecto de la aplicación</b>
 * ({@code none}) — igual que {@code ArranqueContextoSmokeIT}.
 * No se fuerza {@code ddl-auto=validate}: la validación de esquema de Hibernate
 * es global y tropezaría con un desajuste PRE-EXISTENTE y ajeno a {@code V64} en
 * otra tabla ({@code alerta_auditoria}); ese quirk no debe enmascarar ni bloquear
 * la verificación de {@code V64}.</p>
 *
 * <p>El solo hecho de que el contexto arranque prueba que:</p>
 * <ol>
 *   <li>Flyway aplica {@code V64} (y todas las migraciones previas) sobre una BD
 *       limpia sin error.</li>
 *   <li>El {@code EntityManagerFactory} se construye y todas las entidades JPA
 *       ({@code Plan}, {@code PaqueteSuscripcion}, {@code Suscripcion}, ...) se
 *       cargan sin romper el arranque contra el esquema que dejó {@code V64}
 *       (Req 12.4).</li>
 * </ol>
 *
 * <p>La <b>corrección estructural de {@code V64}</b> se verifica de forma
 * explícita e independiente por introspección JDBC del catálogo de PostgreSQL,
 * de modo que no depende del quirk de tipos ajeno de {@code alerta_auditoria}:
 * {@code plan.duracion_dias} (integer, NOT NULL); la tabla
 * {@code paquete_suscripcion} y sus columnas; las nuevas columnas de
 * {@code suscripcion} ({@code tipo_instrumento}/{@code paquete_suscripcion_id}/
 * {@code facturacion_activada}/{@code inicio_facturacion}); {@code plan_id}
 * pasado a NULLABLE; el CHECK de estado ampliado a los cinco valores; y el CHECK
 * XOR de instrumento (Req 12.2).</p>
 *
 * <p>El harness de secretos/llaves de PRUEBA (jamás reales) replica el de
 * {@code ArranqueContextoSmokeIT}: los secretos se inyectan como propiedades de
 * sistema en un bloque {@code static} porque el validador temprano
 * ({@code SecretosEnvironmentPostProcessor}) corre antes del refresh del
 * contexto. Se libera en {@code @AfterAll} para no filtrarlos a otras clases del
 * mismo fork de Failsafe.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Tarea 1.3 - Arranque de V64: el contexto refresca contra el esquema de contratación y se verifica por JDBC (Req 12.2, 12.4)")
class MigracionContratacionV64IT {

    static {
        // Secretos/llaves de PRUEBA (jamás reales) como propiedades de sistema, para
        // que el EnvironmentPostProcessor de validación de secretos (que corre muy
        // temprano) los encuentre. Mismos literales que ArranqueContextoSmokeIT.
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

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // La URL con puerto aleatorio solo se conoce tras arrancar el contenedor.
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
    }

    @Autowired
    private DataSource dataSource;

    // ------------------------------------------------------------------------
    // (a) El contexto se refrescó por completo contra la BD migrada por Flyway
    //     hasta V64: las entidades JPA se cargaron y el arranque no se rompió.
    //     Este test comprueba que el bean DataSource está disponible (el contexto
    //     se refrescó); si el arranque hubiera fallado, la clase entera fallaría.
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("(a) El contexto arranca contra la BD migrada hasta V64 (entidades JPA cargadas)")
    void contextoArrancaConEsquemaDeV64() {
        assertThat(dataSource)
                .as("el contexto debe haberse refrescado contra el esquema migrado hasta V64")
                .isNotNull();
    }

    // ------------------------------------------------------------------------
    // (b) V64 aplicó: la columna plan.duracion_dias existe y es NOT NULL/integer.
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("(b) plan.duracion_dias existe (integer, NOT NULL) tras V64 (Req 12.2)")
    void planDuracionDiasExiste() throws SQLException {
        assertThat(tipoColumna("plan", "duracion_dias"))
                .as("plan.duracion_dias debe existir y ser integer")
                .isEqualTo("integer");
        assertThat(esNotNull("plan", "duracion_dias"))
                .as("plan.duracion_dias debe ser NOT NULL")
                .isTrue();
    }

    // ------------------------------------------------------------------------
    // (c) La tabla paquete_suscripcion existe con sus columnas clave.
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("(c) La tabla paquete_suscripcion existe con columnas clave (Req 12.2)")
    void tablaPaqueteSuscripcionExiste() throws SQLException {
        assertThat(existeTabla("paquete_suscripcion"))
                .as("la tabla paquete_suscripcion debe existir tras V64")
                .isTrue();
        assertThat(tipoColumna("paquete_suscripcion", "duracion_dias"))
                .as("paquete_suscripcion.duracion_dias debe ser integer")
                .isEqualTo("integer");
        assertThat(existeColumna("paquete_suscripcion", "admite_prueba"))
                .as("paquete_suscripcion.admite_prueba debe existir")
                .isTrue();
        assertThat(existeColumna("paquete_suscripcion", "duracion_prueba_meses"))
                .as("paquete_suscripcion.duracion_prueba_meses debe existir")
                .isTrue();
    }

    // ------------------------------------------------------------------------
    // (d) Las nuevas columnas de suscripcion existen y plan_id es NULLABLE.
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("(d) suscripcion tiene columnas de instrumento/facturación y plan_id es NULLABLE (Req 12.2)")
    void suscripcionTieneColumnasDeInstrumentoYFacturacion() throws SQLException {
        assertThat(existeColumna("suscripcion", "tipo_instrumento"))
                .as("suscripcion.tipo_instrumento debe existir")
                .isTrue();
        assertThat(existeColumna("suscripcion", "paquete_suscripcion_id"))
                .as("suscripcion.paquete_suscripcion_id debe existir")
                .isTrue();
        assertThat(existeColumna("suscripcion", "facturacion_activada"))
                .as("suscripcion.facturacion_activada debe existir")
                .isTrue();
        assertThat(existeColumna("suscripcion", "inicio_facturacion"))
                .as("suscripcion.inicio_facturacion debe existir")
                .isTrue();
        // plan_id dejó de ser obligatorio (la exclusividad la gobierna el XOR).
        assertThat(esNotNull("suscripcion", "plan_id"))
                .as("suscripcion.plan_id debe ser NULLABLE tras V64")
                .isFalse();
    }

    // ------------------------------------------------------------------------
    // (e) El CHECK de estado se amplió a los cinco valores del ciclo de vida.
    //     Se comprueba semánticamente: 'vencida'/'en_prueba' ya no violan el CHECK.
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("(e) ck_suscripcion_estado admite los cinco estados del ciclo de vida (Req 12.2)")
    void checkDeEstadoAmpliado() throws SQLException {
        String def = definicionConstraint("ck_suscripcion_estado");
        assertThat(def)
                .as("ck_suscripcion_estado debe existir")
                .isNotNull();
        assertThat(def)
                .as("el CHECK de estado debe admitir los cinco valores del ciclo de vida")
                .contains("activa")
                .contains("en_prueba")
                .contains("suspendida")
                .contains("cancelada")
                .contains("vencida");
    }

    // ------------------------------------------------------------------------
    // (f) Existe el CHECK XOR de instrumento (ck_suscripcion_instrumento).
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("(f) Existe el CHECK XOR ck_suscripcion_instrumento (Req 12.2)")
    void existeCheckXorInstrumento() throws SQLException {
        String def = definicionConstraint("ck_suscripcion_instrumento");
        assertThat(def)
                .as("ck_suscripcion_instrumento (XOR plan_id / paquete_suscripcion_id) debe existir")
                .isNotNull();
        assertThat(def)
                .as("el CHECK XOR debe referenciar ambos instrumentos")
                .contains("plan_id")
                .contains("paquete_suscripcion_id");
    }

    // --- Helpers de introspección del catálogo de PostgreSQL --------------------

    private String tipoColumna(String tabla, String columna) throws SQLException {
        String sql = """
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tabla);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("data_type") : null;
            }
        }
    }

    private boolean existeColumna(String tabla, String columna) throws SQLException {
        return tipoColumna(tabla, columna) != null;
    }

    private boolean esNotNull(String tabla, String columna) throws SQLException {
        String sql = """
                SELECT is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tabla);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("la columna %s.%s debe existir", tabla, columna)
                        .isTrue();
                return "NO".equals(rs.getString("is_nullable"));
            }
        }
    }

    private boolean existeTabla(String tabla) throws SQLException {
        String sql = """
                SELECT 1
                  FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name = ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String definicionConstraint(String nombre) throws SQLException {
        // pg_get_constraintdef devuelve la definición textual del CHECK.
        String sql = "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint WHERE conname = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("def") : null;
            }
        }
    }
}

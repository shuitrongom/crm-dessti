package com.dessti.crm.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración de arranque de migraciones con Testcontainers (Tarea 2.2).
 *
 * <p>Verifica que <b>Flyway aplica TODAS las migraciones existentes</b>
 * ({@code src/main/resources/db/migration}, V1..V4) sobre una base de datos
 * PostgreSQL <b>limpia</b>, cumpliendo el Requisito 23 (esquema multi-tenant
 * base) y validando que las convenciones del esquema (tablas de plataforma,
 * seguridad y auditoría) quedan efectivamente creadas.</p>
 *
 * <h2>Enfoque</h2>
 * <p>La prueba ejecuta Flyway <b>directamente</b> contra un contenedor
 * {@code postgres:16-alpine} gestionado por Testcontainers, apuntando al mismo
 * classpath de migraciones que usa la aplicación ({@code classpath:db/migration}).
 * Se prefiere este enfoque acotado sobre un {@code @SpringBootTest} completo
 * porque:</p>
 * <ul>
 *   <li>El objetivo de la tarea 2.2 es exclusivamente comprobar que <b>las
 *       migraciones aplican sobre una BD limpia</b>; no requiere el resto del
 *       contexto (validación JPA {@code ddl-auto: validate}, filtros de tenant,
 *       cifrado, seguridad).</li>
 *   <li>Es más rápido y robusto: un único contenedor por clase, sin dependencias
 *       de beans que aún necesitan configuración de fases posteriores.</li>
 *   <li>Ejercita exactamente el mismo motor (Flyway) y las mismas migraciones
 *       que el arranque real de la aplicación.</li>
 * </ul>
 *
 * <h2>Secretos y llaves de PRUEBA</h2>
 * <p>Aunque este enfoque acotado NO arranca el contexto de Spring (por lo que no
 * se disparan {@code SecretosEnvironmentPostProcessor} ni el proveedor de llaves
 * de cifrado), se documentan aquí, por trazabilidad con las tareas 6.1/6.2, los
 * valores que se usarían para un arranque completo en pruebas. <b>Son valores de
 * PRUEBA, jamás reales:</b></p>
 * <ul>
 *   <li>{@code DB_USER}/{@code DB_PASSWORD}: las credenciales del contenedor
 *       ({@link #USUARIO_PRUEBA}/{@link #PASSWORD_PRUEBA}).</li>
 *   <li>{@code JWT_SIGNING_KEY}: {@link #JWT_SIGNING_KEY_PRUEBA} (cadena de
 *       prueba, no un secreto real).</li>
 *   <li>{@code CRM_ENC_KEY_ACTIVE}={@code v1} y {@code CRM_ENC_KEY_V1}=
 *       {@link #LLAVE_AES_256_PRUEBA} (llave AES-256 de PRUEBA, 32 bytes en
 *       Base64).</li>
 * </ul>
 * <p>El contenedor gestiona su propio ciclo de vida (arranque/parada) vía
 * Testcontainers; no se requiere {@code docker run} manual.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigracionesFlywayIT {

    // --- Credenciales del contenedor de PRUEBA (coinciden con DB_USER/DB_PASSWORD) ---
    private static final String USUARIO_PRUEBA = "crm_test";
    private static final String PASSWORD_PRUEBA = "crm_test_pwd";
    private static final String BD_PRUEBA = "crm";

    // --- Valores de PRUEBA para secretos de arranque (tareas 6.1/6.2); NO reales. ---
    // Documentados por trazabilidad; este IT acotado no arranca el contexto Spring.
    @SuppressWarnings("unused")
    private static final String JWT_SIGNING_KEY_PRUEBA =
            "clave-de-firma-jwt-solo-para-pruebas-no-usar-en-produccion";
    /** Llave AES-256 de PRUEBA: 32 bytes (todos 0x00) codificados en Base64. */
    @SuppressWarnings("unused")
    private static final String LLAVE_AES_256_PRUEBA =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    /** Versiones de migración que deben existir y haberse aplicado con éxito. */
    private static final List<String> VERSIONES_ESPERADAS = List.of("1", "2", "3", "4");

    /** Tablas clave que deben existir tras aplicar todas las migraciones. */
    private static final List<String> TABLAS_CLAVE = List.of(
            "empresa", "usuario", "rol", "registro_auditoria", "alerta_auditoria");

    /**
     * Un único contenedor PostgreSQL reutilizado por toda la clase (rápido).
     * Testcontainers arranca el contenedor antes de las pruebas y lo detiene al
     * finalizar automáticamente.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName(BD_PRUEBA)
                    .withUsername(USUARIO_PRUEBA)
                    .withPassword(PASSWORD_PRUEBA);

    private DataSource dataSource;
    private MigrateResult resultadoMigracion;

    @BeforeAll
    void aplicarMigracionesSobreBdLimpia() {
        this.dataSource = crearDataSource();

        Flyway flyway = Flyway.configure()
                .dataSource(this.dataSource)
                // Mismo classpath de migraciones que usa la aplicación en producción.
                .locations("classpath:db/migration")
                .load();

        // BD limpia: la migración parte de un esquema sin tablas de negocio.
        this.resultadoMigracion = flyway.migrate();
    }

    private static DataSource crearDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    @Test
    void aplicaTodasLasMigracionesSinError() {
        // (a) Flyway aplicó las migraciones sin lanzar excepción y reporta éxito.
        assertThat(resultadoMigracion).isNotNull();
        assertThat(resultadoMigracion.success).isTrue();
        // Se aplicaron al menos V1..V4 (4 migraciones) sobre la BD limpia.
        assertThat(resultadoMigracion.migrationsExecuted)
                .as("número de migraciones ejecutadas sobre la BD limpia")
                .isGreaterThanOrEqualTo(VERSIONES_ESPERADAS.size());
    }

    @Test
    void flywaySchemaHistoryContieneLasVersionesEsperadasConExito() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        List<String> versionesAplicadas = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() != null) {
                assertThat(info.getState())
                        .as("estado de la migración %s en flyway_schema_history", info.getVersion())
                        .isIn(MigrationState.SUCCESS, MigrationState.BASELINE);
                versionesAplicadas.add(info.getVersion().getVersion());
            }
        }

        // (b) flyway_schema_history contiene, al menos, V1..V4 con success=true.
        assertThat(versionesAplicadas).containsAll(VERSIONES_ESPERADAS);
    }

    @Test
    void existenLasTablasClaveTrasLasMigraciones() throws SQLException {
        List<String> tablasEncontradas = new ArrayList<>();
        String sql = """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                """;
        try (Connection conexion = dataSource.getConnection();
             Statement stmt = conexion.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tablasEncontradas.add(rs.getString("table_name"));
            }
        }

        // (c) Tablas clave de plataforma, seguridad y auditoría creadas (Req 23, 10).
        assertThat(tablasEncontradas).containsAll(TABLAS_CLAVE);
        // La tabla de control de Flyway también debe existir.
        assertThat(tablasEncontradas).contains("flyway_schema_history");
    }

    @AfterAll
    void cerrar() {
        // El contenedor lo detiene Testcontainers (@Container). No hay recursos
        // adicionales que liberar: PGSimpleDataSource no mantiene un pool abierto.
        this.dataSource = null;
    }
}

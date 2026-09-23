package com.dessti.crm.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * Prueba de integración del BACKFILL de la migración {@code V51__empresa_giro.sql}
 * con Testcontainers (Tarea 4.7, Req 11.2, 11.3, 11.5, 2.3).
 *
 * <p>La migración {@code V51} enlaza cada {@code empresa} (tenant) con un
 * {@code giro} del catálogo introducido en {@code V50}. Su comportamiento
 * <b>específico</b> es el <b>backfill de Empresas PREEXISTENTES sin Giro</b>: la
 * secuencia "add-nullable → FK → backfill → set-not-null" solo puede poblar
 * {@code giro_id} de filas de {@code empresa} que existían <b>antes</b> de
 * aplicar {@code V51} (Req 11.2). Como Flyway aplica todas las migraciones de una
 * sola vez, para ejercitar realmente el backfill hay que <b>simular ese
 * escenario</b>.</p>
 *
 * <h2>Enfoque (OPCIÓN A — migración por fases con {@code target})</h2>
 * <ol>
 *   <li>Contenedor {@code postgres:16-alpine} limpio (BD sin tablas de negocio).</li>
 *   <li>{@code Flyway...target("50").migrate()}: aplica <b>solo hasta V50</b>. En
 *       este punto el catálogo {@code giro} está sembrado con
 *       {@code anuncios-luminosos}, pero {@code empresa} <b>aún NO tiene</b> la
 *       columna {@code giro_id} (esa la añade V51).</li>
 *   <li>Se insertan 2 Empresas por SQL nativo <b>sin</b> {@code giro_id}, tal como
 *       estaban las Empresas preexistentes antes de V51 ({@code empresa} es dato
 *       de plataforma sin RLS, se inserta directo).</li>
 *   <li>{@code Flyway...migrate()}: aplica {@code V51} (y siguientes). El backfill
 *       debe poblar {@code giro_id} de esas 2 Empresas con el Giro
 *       {@code anuncios-luminosos} y luego imponer {@code NOT NULL}.</li>
 *   <li>Verificaciones: (a) ambas Empresas quedan con {@code giro_id} = id del
 *       Giro {@code anuncios-luminosos} (Req 11.2); (b) la columna es
 *       {@code NOT NULL} (Req 2.3); (c) existen la FK {@code fk_empresa_giro} y el
 *       índice {@code ix_empresa_giro_id} (Req 11.3, no destructiva y versionada);
 *       (d) Flyway reporta éxito y {@code V51} figura aplicada.</li>
 * </ol>
 *
 * <p>El propio hecho de que este IT aplique limpio contribuye al Req 11.5 ("la
 * suite pasa"): forma parte de la suite de integración (Failsafe) por terminar en
 * {@code IT}.</p>
 *
 * <p>Se ejecuta Flyway <b>directamente</b> contra el contenedor (mismo enfoque
 * acotado y robusto que {@code MigracionesFlywayIT}), sin arrancar el contexto de
 * Spring, porque el objetivo de la tarea es exclusivamente el comportamiento de la
 * migración V51 sobre datos preexistentes.</p>
 *
 * <h2>Credenciales de PRUEBA</h2>
 * <p>Las credenciales del contenedor ({@link #USUARIO_PRUEBA}/{@link #PASSWORD_PRUEBA}/
 * {@link #BD_PRUEBA}) son valores de PRUEBA, jamás reales; coinciden con las de
 * {@code MigracionesFlywayIT}. El contenedor gestiona su ciclo de vida vía
 * Testcontainers.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigracionEmpresaGiroV51IT {

    // --- Credenciales del contenedor de PRUEBA (idénticas a MigracionesFlywayIT) ---
    private static final String USUARIO_PRUEBA = "crm_test";
    private static final String PASSWORD_PRUEBA = "crm_test_pwd";
    private static final String BD_PRUEBA = "crm";

    /** Clave natural del Giro sembrado en V50 al que apunta el backfill (Req 11.2). */
    private static final String CLAVE_GIRO_SEMBRADO = "anuncios-luminosos";

    /** Versión hasta la que se migra en la fase 1 (Empresa aún SIN giro_id). */
    private static final String VERSION_ANTES_DE_V51 = "50";

    // --- Empresas de PRUEBA preexistentes (insertadas antes de V51, sin giro) ---
    // Identificadores fijos para verificación reproducible; RFC válido y único por
    // empresa: 12 caracteres (persona moral, cabe en VARCHAR(13)) y distintos entre sí.
    private static final UUID EMPRESA_PREEXISTENTE_1 =
            UUID.fromString("e0000000-0000-0000-0000-0000000000a1");
    private static final UUID EMPRESA_PREEXISTENTE_2 =
            UUID.fromString("e0000000-0000-0000-0000-0000000000a2");
    private static final String RFC_EMPRESA_1 = "PRE010101AA1";
    private static final String RFC_EMPRESA_2 = "PRB020202BB2";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName(BD_PRUEBA)
                    .withUsername(USUARIO_PRUEBA)
                    .withPassword(PASSWORD_PRUEBA);

    private DataSource dataSource;
    private MigrateResult resultadoV51EnAdelante;
    private UUID idGiroAnunciosLuminosos;

    /**
     * Reproduce el escenario "Empresas preexistentes sin Giro" y aplica V51:
     * fase 1 (hasta V50) → inserción de Empresas sin giro_id → fase 2 (V51+).
     */
    @BeforeAll
    void reproducirBackfillDeEmpresasPreexistentes() throws SQLException {
        this.dataSource = crearDataSource();

        // --- FASE 1: aplicar SOLO hasta V50 (empresa AÚN sin columna giro_id) ---
        MigrateResult hastaV50 = Flyway.configure()
                .dataSource(this.dataSource)
                .locations("classpath:db/migration")
                .target(VERSION_ANTES_DE_V51)
                .load()
                .migrate();
        assertThat(hastaV50.success)
                .as("Flyway debe aplicar con éxito hasta V50")
                .isTrue();

        // El catálogo giro ya está sembrado; capturamos el id del giro objetivo.
        this.idGiroAnunciosLuminosos = leerIdGiro(CLAVE_GIRO_SEMBRADO);
        assertThat(idGiroAnunciosLuminosos)
                .as("V50 debe haber sembrado el giro '%s'", CLAVE_GIRO_SEMBRADO)
                .isNotNull();

        // Confirmación del escenario: en V50 empresa NO tiene aún giro_id.
        assertThat(existeColumnaGiroIdEnEmpresa())
                .as("tras V50, empresa NO debe tener aún la columna giro_id (la añade V51)")
                .isFalse();

        // --- Insertar 2 Empresas preexistentes SIN giro_id (como antes de V51) ---
        insertarEmpresaSinGiro(EMPRESA_PREEXISTENTE_1, "Empresa Preexistente Uno", RFC_EMPRESA_1);
        insertarEmpresaSinGiro(EMPRESA_PREEXISTENTE_2, "Empresa Preexistente Dos", RFC_EMPRESA_2);

        // --- FASE 2: aplicar V51 (y siguientes). El backfill debe poblar giro_id ---
        this.resultadoV51EnAdelante = Flyway.configure()
                .dataSource(this.dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static DataSource crearDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private UUID leerIdGiro(String clave) throws SQLException {
        String sql = "SELECT id FROM giro WHERE clave = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject("id") : null;
            }
        }
    }

    private boolean existeColumnaGiroIdEnEmpresa() throws SQLException {
        String sql = """
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'empresa'
                   AND column_name = 'giro_id'
                """;
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }

    private void insertarEmpresaSinGiro(UUID id, String nombre, String rfc) throws SQLException {
        // empresa es dato de plataforma (sin RLS): se inserta directo. estado='activa'.
        String sql = "INSERT INTO empresa (id, nombre, rfc, estado) VALUES (?, ?, ?, 'activa')";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, nombre);
            ps.setString(3, rfc);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------------
    // (d) Flyway reporta éxito y V51 figura aplicada.
    // ------------------------------------------------------------------------
    @Test
    void aplicaV51ConExitoSobreEmpresasPreexistentes() {
        assertThat(resultadoV51EnAdelante).isNotNull();
        assertThat(resultadoV51EnAdelante.success)
                .as("Flyway debe aplicar V51 (y siguientes) con éxito")
                .isTrue();
        assertThat(resultadoV51EnAdelante.migrationsExecuted)
                .as("debe haberse ejecutado al menos V51 en la fase 2")
                .isGreaterThanOrEqualTo(1);

        // V51 debe figurar como aplicada con éxito en el historial de Flyway.
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        List<String> versionesAplicadas = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() != null) {
                assertThat(info.getState())
                        .as("estado de la migración %s", info.getVersion())
                        .isIn(MigrationState.SUCCESS, MigrationState.BASELINE);
                versionesAplicadas.add(info.getVersion().getVersion());
            }
        }
        assertThat(versionesAplicadas)
                .as("V51 debe figurar aplicada en flyway_schema_history")
                .contains("51");
    }

    // ------------------------------------------------------------------------
    // (a) El backfill pobló giro_id de las Empresas preexistentes con el id del
    //     giro 'anuncios-luminosos' (Req 11.2).
    // ------------------------------------------------------------------------
    @Test
    void backfillAsignaAnunciosLuminososAEmpresasPreexistentes() throws SQLException {
        assertThat(giroIdDeEmpresa(EMPRESA_PREEXISTENTE_1))
                .as("empresa preexistente 1 debe quedar con el giro anuncios-luminosos (Req 11.2)")
                .isEqualTo(idGiroAnunciosLuminosos);
        assertThat(giroIdDeEmpresa(EMPRESA_PREEXISTENTE_2))
                .as("empresa preexistente 2 debe quedar con el giro anuncios-luminosos (Req 11.2)")
                .isEqualTo(idGiroAnunciosLuminosos);

        // Ninguna empresa debe quedar sin giro tras el backfill.
        assertThat(contarEmpresasSinGiro())
                .as("ninguna empresa debe quedar con giro_id NULL tras el backfill")
                .isZero();
    }

    // ------------------------------------------------------------------------
    // (b) La columna giro_id es NOT NULL tras el backfill (Req 2.3).
    // ------------------------------------------------------------------------
    @Test
    void columnaGiroIdEsNotNull() throws SQLException {
        String sql = """
                SELECT is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'empresa'
                   AND column_name = 'giro_id'
                """;
        String isNullable;
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next())
                    .as("la columna empresa.giro_id debe existir tras V51")
                    .isTrue();
            isNullable = rs.getString("is_nullable");
        }
        assertThat(isNullable)
                .as("empresa.giro_id debe ser NOT NULL (Req 2.3)")
                .isEqualTo("NO");
    }

    // ------------------------------------------------------------------------
    // (c) Existen la FK fk_empresa_giro y el índice ix_empresa_giro_id
    //     (migración no destructiva y versionada, Req 11.3).
    // ------------------------------------------------------------------------
    @Test
    void existenFkEIndiceDeGiroEnEmpresa() throws SQLException {
        assertThat(existeConstraint("fk_empresa_giro", 'f'))
                .as("debe existir la FK fk_empresa_giro sobre empresa.giro_id (Req 11.3)")
                .isTrue();
        assertThat(existeIndice("ix_empresa_giro_id"))
                .as("debe existir el índice ix_empresa_giro_id (Req 11.3)")
                .isTrue();
    }

    private UUID giroIdDeEmpresa(UUID empresaId) throws SQLException {
        String sql = "SELECT giro_id FROM empresa WHERE id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("la empresa %s debe existir", empresaId)
                        .isTrue();
                return (UUID) rs.getObject("giro_id");
            }
        }
    }

    private int contarEmpresasSinGiro() throws SQLException {
        String sql = "SELECT count(*) FROM empresa WHERE giro_id IS NULL";
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean existeConstraint(String nombre, char tipo) throws SQLException {
        // pg_constraint.contype: 'f' = foreign key.
        String sql = "SELECT 1 FROM pg_constraint WHERE conname = ? AND contype = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, String.valueOf(tipo));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existeIndice(String nombre) throws SQLException {
        String sql = "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @AfterAll
    void cerrar() {
        // El contenedor lo detiene Testcontainers (@Container). PGSimpleDataSource
        // no mantiene un pool abierto; nada adicional que liberar.
        this.dataSource = null;
    }
}

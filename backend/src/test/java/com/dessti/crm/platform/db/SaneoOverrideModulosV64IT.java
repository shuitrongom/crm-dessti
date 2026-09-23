package com.dessti.crm.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Prueba de integración del SANEO del override de módulos de la migración
 * {@code V64__contratacion_plan_vs_suscripcion.sql} (Tarea 1.4, Req 11.3).
 *
 * <p>El paso 7 de {@code V64} recorta el {@code modulos_habilitados} (override)
 * de cada Contrato ({@code suscripcion}) a la <b>intersección</b> con los
 * {@code modulos_habilitados} del {@code plan} referenciado, solo cuando el
 * override contiene módulos <b>ajenos</b> al Plan. Es idempotente: tras el
 * recorte, la condición {@code EXISTS} del {@code WHERE} deja de cumplirse y una
 * segunda ejecución no cambia nada.</p>
 *
 * <h2>Enfoque</h2>
 * <ol>
 *   <li>Contenedor {@code postgres:16-alpine} limpio; Flyway aplica TODAS las
 *       migraciones (incl. V64) sobre BD limpia.</li>
 *   <li>Se siembra un Plan con módulos {@code [comercial, facturacion]} y una
 *       Empresa (tenant), luego un Contrato ({@code suscripcion}) cuyo override
 *       es un <b>SUPERSET</b> del Plan: {@code [comercial, facturacion, compras,
 *       ajeno]}. Como {@code suscripcion} tiene {@code FORCE ROW LEVEL SECURITY}
 *       (V2), el INSERT/UPDATE se hace dentro de una transacción con
 *       {@code SET LOCAL app.current_tenant} = tenant sembrado.</li>
 *   <li>Se ejecuta el <b>MISMO UPDATE</b> de saneo de V64 (copiado literalmente
 *       de la migración) contra los datos sembrados.</li>
 *   <li>Verificaciones: (a) el override queda EXACTAMENTE como la intersección
 *       {@code [comercial, facturacion]} (se preservan solo los módulos comunes,
 *       Req 11.3); (b) re-ejecutar el UPDATE afecta 0 filas y deja el override
 *       idéntico (idempotencia); (c) un Contrato ya consistente NO se modifica.</li>
 * </ol>
 *
 * <p>Se ejecuta Flyway <b>directamente</b> contra el contenedor (mismo enfoque
 * acotado que {@code MigracionesFlywayIT}/{@code MigracionEmpresaGiroV51IT}/
 * {@code MigracionAdminEmpresaPermisosV58IT}); el objetivo es exclusivamente el
 * comportamiento del UPDATE de saneo de V64 sobre datos inconsistentes.</p>
 *
 * <h2>Credenciales de PRUEBA</h2>
 * <p>Las credenciales del contenedor son valores de PRUEBA, jamás reales;
 * coinciden con las de {@code MigracionesFlywayIT}. El contenedor gestiona su
 * ciclo de vida vía Testcontainers.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaneoOverrideModulosV64IT {

    // --- Credenciales del contenedor de PRUEBA (idénticas a MigracionesFlywayIT) ---
    private static final String USUARIO_PRUEBA = "crm_test";
    private static final String PASSWORD_PRUEBA = "crm_test_pwd";
    private static final String BD_PRUEBA = "crm";

    // Identificadores fijos para verificación reproducible.
    private static final UUID EMPRESA_INCONSISTENTE =
            UUID.fromString("d0000000-0000-0000-0000-0000000000c1");
    private static final UUID EMPRESA_CONSISTENTE =
            UUID.fromString("d0000000-0000-0000-0000-0000000000c2");
    private static final UUID PLAN_ID =
            UUID.fromString("d0000000-0000-0000-0000-0000000000b1");
    private static final UUID CONTRATO_INCONSISTENTE =
            UUID.fromString("d0000000-0000-0000-0000-0000000000a1");
    private static final UUID CONTRATO_CONSISTENTE =
            UUID.fromString("d0000000-0000-0000-0000-0000000000a2");

    // Módulos del Plan y overrides sembrados.
    private static final String PLAN_MODULOS = "[\"comercial\", \"facturacion\"]";
    private static final String OVERRIDE_SUPERSET =
            "[\"comercial\", \"facturacion\", \"compras\", \"ajeno\"]";
    private static final String OVERRIDE_CONSISTENTE = "[\"comercial\"]";

    /**
     * UPDATE de saneo COPIADO LITERALMENTE del paso 7 de
     * {@code V64__contratacion_plan_vs_suscripcion.sql}. Se ejecuta tal cual para
     * ejercitar el comportamiento real de la migración sobre los datos sembrados.
     */
    private static final String SANEO_OVERRIDE_SQL = """
            UPDATE suscripcion s
               SET modulos_habilitados = (
                   SELECT COALESCE(jsonb_agg(m), '[]'::jsonb)
                     FROM jsonb_array_elements_text(s.modulos_habilitados) AS m
                    WHERE m IN (SELECT jsonb_array_elements_text(p.modulos_habilitados)
                                  FROM plan p
                                 WHERE p.id = s.plan_id)
               )
             WHERE s.modulos_habilitados IS NOT NULL
               AND s.plan_id IS NOT NULL
               AND EXISTS (
                   SELECT 1
                     FROM jsonb_array_elements_text(s.modulos_habilitados) AS m
                    WHERE m NOT IN (SELECT jsonb_array_elements_text(p.modulos_habilitados)
                                      FROM plan p
                                     WHERE p.id = s.plan_id)
               )
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName(BD_PRUEBA)
                    .withUsername(USUARIO_PRUEBA)
                    .withPassword(PASSWORD_PRUEBA);

    private DataSource dataSource;

    @BeforeAll
    void aplicarMigracionesYSembrar() throws SQLException {
        this.dataSource = crearDataSource();

        MigrateResult resultado = Flyway.configure()
                .dataSource(this.dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertThat(resultado.success)
                .as("Flyway debe aplicar TODAS las migraciones (incl. V64) con éxito")
                .isTrue();

        // Catálogo de plataforma (sin RLS): plan y empresa se insertan directo.
        // empresa.giro_id es NOT NULL (FK a giro, V51). Reutilizamos el Giro
        // sembrado por V50/V51 ('anuncios-luminosos') en lugar de insertar uno
        // nuevo, para no acoplarnos a las columnas del catálogo ni duplicar datos.
        UUID giroId = obtenerGiroSembrado();
        insertarPlan(PLAN_ID, "Plan Prueba Saneo", PLAN_MODULOS);
        insertarEmpresa(EMPRESA_INCONSISTENTE, "Demo Override Superset", "SUP010101AA1", giroId);
        insertarEmpresa(EMPRESA_CONSISTENTE, "Demo Override Consistente", "CON020202BB2", giroId);

        // Contratos (suscripcion) tienen FORCE RLS: insertar con tenant fijado.
        insertarContratoConTenant(CONTRATO_INCONSISTENTE, EMPRESA_INCONSISTENTE, PLAN_ID, OVERRIDE_SUPERSET);
        insertarContratoConTenant(CONTRATO_CONSISTENTE, EMPRESA_CONSISTENTE, PLAN_ID, OVERRIDE_CONSISTENTE);
    }

    private static DataSource crearDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private void insertarPlan(UUID id, String nombre, String modulosJson) throws SQLException {
        // plan tiene duracion_dias NOT NULL (sin default tras V64): aportamos > 365.
        String sql = "INSERT INTO plan (id, nombre, max_usuarios, modulos_habilitados, duracion_dias) "
                + "VALUES (?, ?, 50, ?::jsonb, 730)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, nombre);
            ps.setString(3, modulosJson);
            ps.executeUpdate();
        }
    }

    /**
     * Recupera el id del Giro sembrado por V50/V51 ({@code 'anuncios-luminosos'})
     * para satisfacer la FK {@code empresa.giro_id} (NOT NULL desde V51). Se
     * reutiliza el Giro del catálogo en vez de insertar uno nuevo (menos
     * acoplamiento al esquema de {@code giro}).
     */
    private UUID obtenerGiroSembrado() throws SQLException {
        String sql = "SELECT id FROM giro WHERE clave = 'anuncios-luminosos'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next())
                    .as("V50/V51 deben haber sembrado el Giro 'anuncios-luminosos'")
                    .isTrue();
            return rs.getObject("id", UUID.class);
        }
    }

    private void insertarEmpresa(UUID id, String nombre, String rfc, UUID giroId) throws SQLException {
        // empresa es dato de plataforma (sin RLS): se inserta directo. giro_id es
        // NOT NULL (FK a giro, V51): se aporta el Giro sembrado del catálogo.
        String sql = "INSERT INTO empresa (id, nombre, rfc, estado, giro_id) VALUES (?, ?, ?, 'activa', ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, nombre);
            ps.setString(3, rfc);
            ps.setObject(4, giroId);
            ps.executeUpdate();
        }
    }

    /**
     * Inserta un Contrato de tipo 'plan' con override. Como suscripcion tiene
     * FORCE ROW LEVEL SECURITY, se fija app.current_tenant = tenant en la MISMA
     * transacción para satisfacer la política tenant_isolation (WITH CHECK).
     */
    private void insertarContratoConTenant(UUID id, UUID tenantId, UUID planId, String overrideJson)
            throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                fijarTenant(c, tenantId);
                String sql = "INSERT INTO suscripcion "
                        + "(id, tenant_id, plan_id, tipo_instrumento, estado, vigencia_inicio, modulos_habilitados) "
                        + "VALUES (?, ?, ?, 'plan', 'activa', CURRENT_DATE, ?::jsonb)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, planId);
                    ps.setString(4, overrideJson);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void fijarTenant(Connection c, UUID tenantId) throws SQLException {
        // SET LOCAL requiere estar en transacción; usa literal seguro (UUID validado).
        try (Statement st = c.createStatement()) {
            st.execute("SET LOCAL app.current_tenant = '" + tenantId + "'");
        }
    }

    /** Ejecuta el UPDATE de saneo de V64 dentro de una transacción con el tenant fijado. */
    private int ejecutarSaneo(UUID tenantId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                fijarTenant(c, tenantId);
                int filas;
                try (Statement st = c.createStatement()) {
                    filas = st.executeUpdate(SANEO_OVERRIDE_SQL);
                }
                c.commit();
                return filas;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private List<String> overrideDe(UUID contratoId, UUID tenantId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                fijarTenant(c, tenantId);
                // Desagrega el jsonb array a filas de texto, ordenadas para comparación estable.
                String sql = """
                        SELECT jsonb_array_elements_text(modulos_habilitados) AS m
                          FROM suscripcion
                         WHERE id = ?
                         ORDER BY m
                        """;
                List<String> modulos = new java.util.ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setObject(1, contratoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            modulos.add(rs.getString("m"));
                        }
                    }
                }
                c.commit();
                return modulos;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------------------
    // (a) El saneo recorta el override SUPERSET a la intersección con el Plan.
    //     La migración ya corrió durante Flyway, pero los datos se sembraron
    //     DESPUÉS; re-ejecutamos el MISMO UPDATE para ejercitarlo (Req 11.3).
    // ------------------------------------------------------------------------
    @Test
    void saneoRecortaOverrideALaInterseccionConElPlan() throws SQLException {
        // Estado inicial sembrado: override es superset del Plan.
        assertThat(overrideDe(CONTRATO_INCONSISTENTE, EMPRESA_INCONSISTENTE))
                .as("precondición: el override sembrado es un superset del Plan")
                .containsExactlyInAnyOrder("comercial", "facturacion", "compras", "ajeno");

        int filasAfectadas = ejecutarSaneo(EMPRESA_INCONSISTENTE);
        assertThat(filasAfectadas)
                .as("el saneo debe afectar exactamente el Contrato inconsistente")
                .isEqualTo(1);

        // El override queda como la intersección con plan.modulos_habilitados.
        assertThat(overrideDe(CONTRATO_INCONSISTENTE, EMPRESA_INCONSISTENTE))
                .as("el override debe quedar recortado a la intersección [comercial, facturacion] (Req 11.3)")
                .containsExactlyInAnyOrder("comercial", "facturacion");
    }

    // ------------------------------------------------------------------------
    // (b) Idempotencia: re-ejecutar el saneo no cambia el resultado (0 filas).
    // ------------------------------------------------------------------------
    @Test
    void saneoEsIdempotente() throws SQLException {
        // Primera pasada (por si este test corre antes que (a): deja consistente).
        ejecutarSaneo(EMPRESA_INCONSISTENTE);
        List<String> trasPrimera = overrideDe(CONTRATO_INCONSISTENTE, EMPRESA_INCONSISTENTE);

        // Segunda pasada: ya no hay módulos ajenos, la condición EXISTS no aplica.
        int filasSegunda = ejecutarSaneo(EMPRESA_INCONSISTENTE);
        assertThat(filasSegunda)
                .as("re-ejecutar el saneo sobre datos ya saneados no debe afectar filas (idempotencia)")
                .isZero();

        assertThat(overrideDe(CONTRATO_INCONSISTENTE, EMPRESA_INCONSISTENTE))
                .as("el override no debe cambiar en la segunda ejecución")
                .containsExactlyInAnyOrderElementsOf(trasPrimera)
                .containsExactlyInAnyOrder("comercial", "facturacion");
    }

    // ------------------------------------------------------------------------
    // (c) Un Contrato ya consistente (override ⊆ Plan) NO se modifica.
    // ------------------------------------------------------------------------
    @Test
    void saneoNoTocaContratoYaConsistente() throws SQLException {
        List<String> antes = overrideDe(CONTRATO_CONSISTENTE, EMPRESA_CONSISTENTE);
        assertThat(antes)
                .as("precondición: el override consistente es subconjunto del Plan")
                .containsExactly("comercial");

        ejecutarSaneo(EMPRESA_CONSISTENTE);

        assertThat(overrideDe(CONTRATO_CONSISTENTE, EMPRESA_CONSISTENTE))
                .as("el saneo no debe tocar un Contrato ya consistente")
                .containsExactly("comercial");
    }

    @AfterAll
    void cerrar() {
        this.dataSource = null;
    }
}

package com.dessti.crm.platform.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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

/**
 * Prueba de integración de la migración
 * {@code V58__admin_empresa_permisos_modulos_negocio.sql} con Testcontainers.
 *
 * <p>V58 concede al rol predefinido {@code admin_empresa}
 * ({@code a0000000-0000-0000-0000-000000000002}, sembrado en V5) la UNIÓN de los
 * permisos de TODOS los roles predefinidos de nivel EMPRESA, de modo que el
 * administrador vea/acceda AUTOMÁTICAMENTE cualquier bloque de negocio que la
 * Empresa contrate. El gating por módulo ({@code moduloHabilitado}) sigue
 * restringiendo la visibilidad al conjunto contratado: tener el permiso NO basta.</p>
 *
 * <h2>Verificaciones</h2>
 * <ol>
 *   <li><b>Concede permisos operacionales de negocio</b> al {@code admin_empresa}
 *       (muestras representativas de comercial, facturación, compras y rh-nómina:
 *       {@code cliente:listar}, {@code factura:listar}, {@code requisicion_compra:listar},
 *       {@code empleado:listar}).</li>
 *   <li><b>NO concede permisos de PLATAFORMA</b> (reservados a {@code super_admin}):
 *       {@code empresa:crear}, {@code plan:listar}, {@code factura_renta:listar}.</li>
 *   <li><b>Cobertura por unión</b>: el {@code admin_empresa} posee, como mínimo,
 *       todos los permisos que tiene el rol {@code ventas} (invariante de la unión).</li>
 * </ol>
 *
 * <p>Se ejecuta Flyway <b>directamente</b> contra un contenedor
 * {@code postgres:16-alpine} (mismo enfoque acotado y robusto que
 * {@code MigracionesFlywayIT}/{@code MigracionEmpresaGiroV51IT}), sin arrancar el
 * contexto de Spring, porque el objetivo es exclusivamente el efecto de V58 sobre
 * el catálogo de roles/permisos sembrado.</p>
 *
 * <h2>Credenciales de PRUEBA</h2>
 * <p>Las credenciales del contenedor son valores de PRUEBA, jamás reales; coinciden
 * con las de {@code MigracionesFlywayIT}. El contenedor gestiona su ciclo de vida
 * vía Testcontainers.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigracionAdminEmpresaPermisosV58IT {

    // --- Credenciales del contenedor de PRUEBA (idénticas a MigracionesFlywayIT) ---
    private static final String USUARIO_PRUEBA = "crm_test";
    private static final String PASSWORD_PRUEBA = "crm_test_pwd";
    private static final String BD_PRUEBA = "crm";

    /** UUID fijo del rol admin_empresa (V5). */
    private static final String ROL_ADMIN_EMPRESA = "a0000000-0000-0000-0000-000000000002";
    /** UUID fijo del rol ventas (V5), usado para el invariante de la unión. */
    private static final String ROL_VENTAS = "a0000000-0000-0000-0000-000000000005";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName(BD_PRUEBA)
                    .withUsername(USUARIO_PRUEBA)
                    .withPassword(PASSWORD_PRUEBA);

    private DataSource dataSource;

    @BeforeAll
    void aplicarTodasLasMigraciones() {
        this.dataSource = crearDataSource();
        MigrateResult resultado = Flyway.configure()
                .dataSource(this.dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertThat(resultado.success)
                .as("Flyway debe aplicar TODAS las migraciones (incl. V58) con éxito")
                .isTrue();
    }

    private static DataSource crearDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    // ------------------------------------------------------------------------
    // (1) V58 concede al admin_empresa los permisos OPERACIONALES de negocio.
    // ------------------------------------------------------------------------
    @Test
    void adminEmpresaRecibePermisosOperacionalesDeNegocio() throws SQLException {
        // Muestras representativas de distintos módulos vendibles.
        assertThat(adminEmpresaTiene("cliente", "listar"))
                .as("comercial: admin_empresa debe tener cliente:listar")
                .isTrue();
        assertThat(adminEmpresaTiene("factura", "listar"))
                .as("facturación: admin_empresa debe tener factura:listar")
                .isTrue();
        assertThat(adminEmpresaTiene("requisicion_compra", "listar"))
                .as("compras: admin_empresa debe tener requisicion_compra:listar")
                .isTrue();
        assertThat(adminEmpresaTiene("empleado", "listar"))
                .as("rh-nómina: admin_empresa debe tener empleado:listar")
                .isTrue();
    }

    // ------------------------------------------------------------------------
    // (2) V58 NO concede permisos de PLATAFORMA (reservados a super_admin).
    // ------------------------------------------------------------------------
    @Test
    void adminEmpresaNoRecibePermisosDePlataforma() throws SQLException {
        assertThat(adminEmpresaTiene("empresa", "crear"))
                .as("admin_empresa NO debe poder crear Empresas (plataforma)")
                .isFalse();
        assertThat(adminEmpresaTiene("plan", "listar"))
                .as("admin_empresa NO debe poder listar Planes (plataforma)")
                .isFalse();
        assertThat(adminEmpresaTiene("factura_renta", "listar"))
                .as("admin_empresa NO debe poder listar Facturas de Renta (plataforma)")
                .isFalse();
    }

    // ------------------------------------------------------------------------
    // (3) Invariante de la unión: admin_empresa contiene todos los permisos de
    //     un rol operativo representativo (ventas). Bloquea regresiones si un
    //     futuro cambio rompiera la lógica de unión.
    // ------------------------------------------------------------------------
    @Test
    void adminEmpresaContieneAlMenosLosPermisosDeVentas() throws SQLException {
        List<String> permisosVentas = permisosDeRol(ROL_VENTAS);
        List<String> permisosAdmin = permisosDeRol(ROL_ADMIN_EMPRESA);

        assertThat(permisosVentas)
                .as("el rol ventas debe tener permisos sembrados")
                .isNotEmpty();
        assertThat(permisosAdmin)
                .as("admin_empresa debe contener TODOS los permisos de ventas (unión)")
                .containsAll(permisosVentas);
    }

    private boolean adminEmpresaTiene(String recurso, String operacion) throws SQLException {
        String sql = """
                SELECT 1
                  FROM rol_permiso rp
                  JOIN permiso p ON p.id = rp.permiso_id
                 WHERE rp.rol_id = ?::uuid
                   AND p.recurso = ?
                   AND p.operacion = ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ROL_ADMIN_EMPRESA);
            ps.setString(2, recurso);
            ps.setString(3, operacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> permisosDeRol(String rolId) throws SQLException {
        String sql = """
                SELECT p.recurso || ':' || p.operacion AS clave
                  FROM rol_permiso rp
                  JOIN permiso p ON p.id = rp.permiso_id
                 WHERE rp.rol_id = ?::uuid
                """;
        List<String> claves = new java.util.ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, rolId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claves.add(rs.getString("clave"));
                }
            }
        }
        return claves;
    }

    @AfterAll
    void cerrar() {
        this.dataSource = null;
    }
}

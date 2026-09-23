package com.dessti.crm.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración de la Tarea 4.6: verifica que las políticas de
 * <b>Row-Level Security (RLS)</b> definidas en {@code V2__rls_multi_tenant.sql}
 * impiden el acceso cruzado entre empresas (tenants) <b>aun omitiendo el filtro
 * de aplicación de Hibernate</b> (Capa 1). Cubre el Requisito 23 (aislamiento
 * multi-empresa).
 *
 * <h2>Escenario "aun omitiendo el filtro de aplicación"</h2>
 * La prueba va <b>directo a SQL</b> (JDBC crudo) sin el filtro global de
 * Hibernate. La única defensa que actúa es RLS en PostgreSQL. Se replica lo que
 * hace {@link TenantSessionInitializer} en producción: fijar la variable de
 * sesión {@code app.current_tenant} por transacción con
 * {@code set_config('app.current_tenant', ?, true)} (equivalente a
 * {@code SET LOCAL}).
 *
 * <h2>Rol NO superusuario (clave de validez)</h2>
 * Testcontainers se conecta por defecto con el usuario administrador del
 * contenedor, que es <b>superusuario</b> y por tanto <b>omite RLS</b>: usarlo
 * invalidaría la prueba. Por eso se aprovisiona el rol {@code crm_app}
 * ({@code NOSUPERUSER, NOBYPASSRLS}) —igual que {@code db/roles/app_role.sql}—,
 * se le otorgan privilegios DML y todas las verificaciones de aislamiento se
 * ejecutan con un {@link DataSource} secundario conectado con ese rol. Como las
 * tablas tienen {@code FORCE ROW LEVEL SECURITY}, las políticas aplican incluso
 * al dueño, pero el rol no privilegiado es la garantía definitiva.
 */
@Testcontainers
@DisplayName("Tarea 4.6 - RLS multi-tenant con Testcontainers (Req 23)")
class RlsMultiTenantIT {

    private static final String APP_ROLE = "crm_app";
    private static final String APP_PASSWORD = "crm_app_secret_test";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("crm")
                    .withUsername("crm_owner")
                    .withPassword("owner_secret_test");

    /** DataSource conectado como el rol NO superusuario de la aplicación (RLS aplica). */
    private static DataSource appDataSource;

    private static UUID empresaA;
    private static UUID empresaB;
    private static UUID planId;

    @BeforeAll
    static void prepararEsquemaYDatos() throws SQLException {
        // 1) Aplicar TODAS las migraciones (Flyway) como el dueño/migrador para
        //    obtener el esquema con RLS habilitada (V2) y las demás tablas.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        // 2) Crear el rol de aplicación NO superusuario / NO bypassrls y otorgar DML,
        //    replicando db/roles/app_role.sql. Se ejecuta como el owner del contenedor.
        try (Connection ownerConn = ownerConnection(); Statement st = ownerConn.createStatement()) {
            st.execute("DROP ROLE IF EXISTS " + APP_ROLE);
            st.execute("CREATE ROLE " + APP_ROLE
                    + " LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE"
                    + " PASSWORD '" + APP_PASSWORD + "'");
            st.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + APP_ROLE);
            st.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO " + APP_ROLE);
        }

        appDataSource = appDataSource();

        // 3) Sembrar datos base como owner (empresas + plan) para tener FKs válidas.
        //    empresa NO tiene RLS (es la entidad tenant), por lo que se inserta sin
        //    contexto. También el plan (catálogo de plataforma, sin RLS).
        empresaA = UUID.randomUUID();
        empresaB = UUID.randomUUID();
        planId = UUID.randomUUID();

        try (Connection ownerConn = ownerConnection()) {
            // giro_id es OBLIGATORIO desde V51 (empresa.giro_id UUID NOT NULL con FK a
            // giro): se enlaza por subconsulta al Giro `anuncios-luminosos` sembrado por
            // V50. La subconsulta resuelve el giro sin necesidad de un parametro extra.
            try (PreparedStatement ps = ownerConn.prepareStatement(
                    "INSERT INTO empresa (id, nombre, rfc, estado, giro_id) "
                            + "VALUES (?, ?, ?, 'activa', "
                            + "(SELECT id FROM giro WHERE clave='anuncios-luminosos'))")) {
                ps.setObject(1, empresaA);
                ps.setString(2, "Empresa A");
                ps.setString(3, "AAA010101AAA");
                ps.executeUpdate();

                ps.setObject(1, empresaB);
                ps.setString(2, "Empresa B");
                ps.setString(3, "BBB020202BBB");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = ownerConn.prepareStatement(
                    "INSERT INTO plan (id, nombre, max_usuarios) VALUES (?, 'Plan Test', 100)")) {
                ps.setObject(1, planId);
                ps.executeUpdate();
            }
        }
    }

    @AfterAll
    static void limpiar() {
        // Testcontainers detiene el contenedor automáticamente al finalizar.
    }

    // ------------------------------------------------------------------------
    // Prueba principal: aislamiento por RLS con el rol NO superusuario.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Con app.current_tenant fijado, cada empresa ve SUS filas + catalogo de plataforma (tenant NULL) pero NUNCA las de otra empresa; suscripcion es estrictamente tenant-scoped; WITH CHECK rechaza")
    void rlsAislaFilasTenantScopedAunSinFiltroDeAplicacion() throws SQLException {
        // --- Sembrar filas tenant-scoped de A y B usando el rol de aplicación,
        //     fijando el contexto correspondiente en cada transacción (como en producción). ---
        insertarFilasTenant(empresaA, "userA@test", "Rol A");
        insertarFilasTenant(empresaB, "userB@test", "Rol B");

        // --- Contexto tenant A ---
        // `usuario`: este escenario no siembra usuarios de plataforma (tenant NULL),
        // por lo que bajo el contexto A solo se ven usuarios de A. El aislamiento
        // frente a B es lo esencial (RLS consciente de plataforma, V48).
        List<UUID> usuariosVistosPorA = usuariosConContexto(empresaA);
        assertThat(usuariosVistosPorA)
                .as("Con contexto A, se ven usuarios de A y NUNCA de otra empresa")
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t).isEqualTo(empresaA));

        // `rol`: catalogo COMPARTIDO. Bajo el contexto A se ven los roles de A MAS
        // los roles predefinidos de plataforma (tenant_id NULL, sembrados por V5/
        // V45/V47), pero NUNCA los roles de otra empresa (V48: RLS consciente de
        // plataforma). Se valida que: (1) aparece al menos un rol de A, (2) aparece
        // al menos un rol de plataforma (NULL), (3) no aparece ningun rol de B.
        List<UUID> rolesVistosPorA = tenantIdsDe("rol", empresaA);
        assertThat(rolesVistosPorA)
                .as("Con contexto A se ven roles de A y del catalogo de plataforma (NULL)")
                .contains(empresaA)
                .contains((UUID) null)
                .doesNotContain(empresaB);

        // `suscripcion`: NO tiene filas de plataforma y conserva la politica
        // estricta de V2 (sin cambios). Bajo el contexto A, solo filas de A.
        List<UUID> suscripcionesVistasPorA = tenantIdsDe("suscripcion", empresaA);
        assertThat(suscripcionesVistasPorA)
                .as("suscripcion es estrictamente tenant-scoped: solo filas de A")
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t).isEqualTo(empresaA));

        // --- Contexto tenant B: analogo; nunca ve filas de A ---
        List<UUID> usuariosVistosPorB = usuariosConContexto(empresaB);
        assertThat(usuariosVistosPorB)
                .as("Con contexto B, se ven usuarios de B y NUNCA de otra empresa")
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t).isEqualTo(empresaB));

        List<UUID> rolesVistosPorB = tenantIdsDe("rol", empresaB);
        assertThat(rolesVistosPorB)
                .as("Con contexto B se ven roles de B y del catalogo de plataforma (NULL)")
                .contains(empresaB)
                .contains((UUID) null)
                .doesNotContain(empresaA);

        // Aislamiento cruzado (lo esencial del Req 23): A no ve filas de B ni viceversa.
        assertThat(usuariosVistosPorA).doesNotContain(empresaB);
        assertThat(usuariosVistosPorB).doesNotContain(empresaA);

        // --- suscripcion sin app.current_tenant fijado: fail-safe estricto (V2 sin
        //     cambios), no se ve NINGUNA fila tenant-scoped. Las tablas rol/usuario
        //     permiten lectura en la ventana de autenticacion (V48), por lo que su
        //     fail-safe se valida de forma distinta (ver rolUsuarioLecturaPlataforma). ---
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false); // transacción sin fijar el tenant
            assertThat(contarFilas(appConn, "suscripcion"))
                    .as("Sin app.current_tenant, RLS oculta todas las filas de suscripcion (fail-safe estricto V2)")
                    .isZero();
            appConn.rollback();
        }

        // --- WITH CHECK: con contexto A, insertar una fila de tenant B se rechaza ---
        assertThatThrownBy(() -> insertarUsuarioConContexto(empresaA, empresaB, "intruso@test"))
                .as("WITH CHECK debe rechazar insertar una fila cuyo tenant_id != app.current_tenant")
                .isInstanceOf(SQLException.class);
    }

    // ------------------------------------------------------------------------
    // Verificación complementaria: el rol de aplicación NO es superusuario.
    // Si lo fuera, RLS se omitiría y la prueba anterior no probaría nada.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("El rol de aplicación no es superusuario ni tiene BYPASSRLS (RLS realmente aplica)")
    void rolDeAplicacionNoOmiteRls() throws SQLException {
        try (Connection appConn = appDataSource.getConnection();
                Statement st = appConn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("rolsuper")).as("crm_app no debe ser superusuario").isFalse();
            assertThat(rs.getBoolean("rolbypassrls")).as("crm_app no debe tener BYPASSRLS").isFalse();
        }
    }

    // ------------------------------------------------------------------------
    // RLS consciente de plataforma (V48): rol y usuario permiten LEER el
    // catalogo de plataforma (tenant_id NULL) y resolver por identificador en la
    // ventana de autenticacion (sin tenant fijado), manteniendo la ESCRITURA
    // estrictamente tenant-scoped. Este es el contrato que arreglo el defecto de
    // arranque (login/seed) sin debilitar el aislamiento de negocio.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("rol/usuario: lectura de plataforma (tenant NULL) y en ventana de autenticacion; escritura de plataforma reservada al migrador")
    void rolUsuarioLecturaPlataforma() throws SQLException {
        // Sembrar filas propias para independencia del orden de ejecucion de los
        // tests (JUnit no garantiza orden). Identificadores unicos para no chocar
        // con el otro test si este corre despues.
        insertarFilasTenant(empresaA, "userA-plat@test", "Rol A plat");

        // (1) Los roles predefinidos de plataforma (tenant_id NULL, sembrados por
        //     Flyway V5/V45/V47) SON visibles bajo un contexto de tenant cualquiera.
        List<UUID> rolesBajoA = tenantIdsDe("rol", empresaA);
        long rolesPlataformaVisibles = rolesBajoA.stream().filter(java.util.Objects::isNull).count();
        assertThat(rolesPlataformaVisibles)
                .as("Los roles predefinidos de plataforma (tenant NULL) deben ser visibles a cada tenant")
                .isPositive();

        // (2) VENTANA DE AUTENTICACION: sin app.current_tenant fijado, la lectura de
        //     `usuario`/`rol` esta permitida (el login resuelve por identificador
        //     unico global antes de conocer el tenant). Esto NO aplica a suscripcion
        //     ni a las tablas de negocio.
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false); // sin fijar tenant = ventana de autenticacion
            assertThat(contarFilas(appConn, "usuario"))
                    .as("En la ventana de autenticacion (sin tenant), usuario es legible para resolver por identificador")
                    .isPositive();
            assertThat(contarFilas(appConn, "rol"))
                    .as("En la ventana de autenticacion (sin tenant), rol es legible para construir los claims")
                    .isPositive();
            appConn.rollback();
        }

        // (3) ESCRITURA de plataforma (tenant_id NULL) PROHIBIDA al rol de aplicacion,
        //     tanto sin tenant como con tenant fijado (WITH CHECK exige tenant actual).
        //     Solo el migrador (BYPASSRLS) siembra filas de plataforma.
        assertThatThrownBy(() -> insertarRolPlataforma())
                .as("El rol de aplicacion NO puede insertar roles de plataforma (tenant NULL); reservado al migrador")
                .isInstanceOf(SQLException.class);
    }

    // ------------------------------------------------------------------------
    // REGRESION V53: cast de tenant seguro ante CADENA VACIA.
    //
    // Defecto de produccion: TenantSessionInitializer fija app.current_tenant con
    // set_config(...,true) (LOCAL a la transaccion). Al terminar la transaccion,
    // PostgreSQL revierte la GUC personalizada a CADENA VACIA '' (no NULL) dentro
    // de la conexion fisica reutilizada por el pool. La siguiente peticion que
    // toca una tabla RLS antes de aplicar un nuevo tenant (p. ej. el LOGIN, que
    // resuelve por identificador_acceso) evaluaba `...::uuid` sobre '' y lanzaba
    // ERROR 22P02 (invalid input syntax for type uuid: ""), visible como HTTP 500.
    // V53 hace el cast seguro con NULLIF(...,''): la cadena vacia colapsa a NULL
    // sin error y sin debilitar el aislamiento. Estas pruebas se ejecutan con el
    // rol NO superusuario `crm_app` (NOBYPASSRLS), por lo que RLS aplica de verdad.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("V53: con app.current_tenant = '' (reset del pool), SELECT sobre usuario por identificador NO lanza 22P02; el login sobrevive a la conexion reutilizada")
    void castTenantVacioNoRompeLecturaDeUsuario() throws SQLException {
        // Sembrar un usuario para tener una fila que resolver por identificador.
        insertarFilasTenant(empresaA, "login-vacio@test", "Rol A vacio");

        // Simular el estado exacto de la conexion reutilizada por el pool: la GUC
        // ha sido tocada y revertida a '' (cadena vacia, no NULL). Antes de V53
        // esto lanzaba 'invalid input syntax for type uuid: ""'.
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false);
            try (Statement st = appConn.createStatement()) {
                st.execute("SET LOCAL app.current_tenant = ''");
            }

            // El LOGIN resuelve al usuario por su identificador global ANTES de
            // fijar un tenant. Debe ejecutarse sin lanzar el error de cast.
            final List<UUID> encontrados = new ArrayList<>();
            org.assertj.core.api.Assertions.assertThatCode(() -> {
                try (PreparedStatement ps = appConn.prepareStatement(
                        "SELECT tenant_id FROM usuario WHERE identificador_acceso = ?")) {
                    ps.setString(1, "login-vacio@test");
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            encontrados.add((UUID) rs.getObject("tenant_id"));
                        }
                    }
                }
            })
                    .as("Con la GUC en cadena vacia, la lectura de usuario NO debe lanzar 22P02 (cast seguro V53)")
                    .doesNotThrowAnyException();

            // La ventana de autenticacion permite ver al usuario (tenant no fijado
            // == cadena vacia colapsada a NULL por NULLIF): se resuelve por
            // identificador global.
            assertThat(encontrados)
                    .as("El usuario debe resolverse por identificador en la ventana de autenticacion con la GUC vacia")
                    .contains(empresaA);
            appConn.rollback();
        }
    }

    @Test
    @DisplayName("V53: el cast seguro NO debilita el aislamiento: con tenant A, no se ve la fila de B y WITH CHECK rechaza insertar tenant B")
    void castSeguroPreservaAislamiento() throws SQLException {
        // Sembrar filas de A y B (identificadores unicos para este test).
        insertarFilasTenant(empresaA, "aisl-A@test", "Rol aisl A");
        insertarFilasTenant(empresaB, "aisl-B@test", "Rol aisl B");

        // Con tenant A fijado (uuid concreto), NULLIF no altera el uuid: solo se
        // ven filas de A y NUNCA de B.
        List<UUID> usuariosBajoA = usuariosConContexto(empresaA);
        assertThat(usuariosBajoA)
                .as("Con tenant A concreto solo se ven filas de A")
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t).isEqualTo(empresaA))
                .doesNotContain(empresaB);

        // WITH CHECK sigue rechazando insertar una fila de B bajo el contexto A.
        assertThatThrownBy(() -> insertarUsuarioConContexto(empresaA, empresaB, "aisl-intruso@test"))
                .as("WITH CHECK (cast seguro V53) sigue rechazando insertar tenant B bajo contexto A")
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("V53: tabla de negocio (suscripcion) con GUC vacia NO lanza 22P02 y sigue oculta (fail-safe estricto)")
    void castTenantVacioEnTablaDeNegocioNoRompeYQuedaOculta() throws SQLException {
        insertarFilasTenant(empresaA, "susc-vacio@test", "Rol susc vacio");

        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false);
            try (Statement st = appConn.createStatement()) {
                st.execute("SET LOCAL app.current_tenant = ''");
            }
            // Antes de V53, la politica tenant_isolation de suscripcion lanzaba
            // 22P02 al castear '' a uuid. Ahora NULLIF lo colapsa a NULL: sin
            // error y, por fail-safe, sin filas visibles.
            final long[] filas = new long[1];
            org.assertj.core.api.Assertions.assertThatCode(() -> filas[0] = contarFilas(appConn, "suscripcion"))
                    .as("Con la GUC vacia, contar suscripcion NO debe lanzar 22P02 (cast seguro V53)")
                    .doesNotThrowAnyException();
            assertThat(filas[0])
                    .as("Con la GUC vacia (tenant NULL), ninguna fila tenant-scoped de suscripcion es visible")
                    .isZero();
            appConn.rollback();
        }
    }

    /** Intenta insertar un rol de plataforma (tenant_id NULL) como el rol de aplicacion; debe fallar por WITH CHECK. */
    private void insertarRolPlataforma() throws SQLException {
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false); // sin tenant fijado
            try (PreparedStatement ps = appConn.prepareStatement(
                    "INSERT INTO rol (id, tenant_id, nombre, predefinido) "
                            + "VALUES (gen_random_uuid(), NULL, ?, TRUE)")) {
                ps.setString(1, "rol_plataforma_intruso");
                ps.executeUpdate();
                appConn.commit();
            } catch (SQLException e) {
                appConn.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /** Inserta un usuario, un rol y una suscripción del tenant dado, con contexto RLS del propio tenant. */
    private void insertarFilasTenant(UUID tenant, String identificador, String nombreRol) throws SQLException {
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false);
            fijarTenant(appConn, tenant);

            try (PreparedStatement ps = appConn.prepareStatement(
                    "INSERT INTO usuario (id, tenant_id, identificador_acceso, hash_password) "
                            + "VALUES (gen_random_uuid(), ?, ?, 'x')")) {
                ps.setObject(1, tenant);
                ps.setString(2, identificador);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = appConn.prepareStatement(
                    "INSERT INTO rol (id, tenant_id, nombre, predefinido) "
                            + "VALUES (gen_random_uuid(), ?, ?, FALSE)")) {
                ps.setObject(1, tenant);
                ps.setString(2, nombreRol);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = appConn.prepareStatement(
                    "INSERT INTO suscripcion (id, tenant_id, plan_id, estado, vigencia_inicio) "
                            + "VALUES (gen_random_uuid(), ?, ?, 'activa', CURRENT_DATE)")) {
                ps.setObject(1, tenant);
                ps.setObject(2, planId);
                ps.executeUpdate();
            }
            appConn.commit();
        }
    }

    /** Intenta insertar un usuario con {@code tenant_id = tenantFila} bajo el contexto {@code tenantCtx}. */
    private void insertarUsuarioConContexto(UUID tenantCtx, UUID tenantFila, String identificador)
            throws SQLException {
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false);
            fijarTenant(appConn, tenantCtx);
            try (PreparedStatement ps = appConn.prepareStatement(
                    "INSERT INTO usuario (id, tenant_id, identificador_acceso, hash_password) "
                            + "VALUES (gen_random_uuid(), ?, ?, 'x')")) {
                ps.setObject(1, tenantFila);
                ps.setString(2, identificador);
                ps.executeUpdate();
                appConn.commit();
            } catch (SQLException e) {
                appConn.rollback();
                throw e;
            }
        }
    }

    /** Devuelve los tenant_id de todos los usuarios visibles bajo el contexto dado. */
    private List<UUID> usuariosConContexto(UUID tenant) throws SQLException {
        return tenantIdsDe("usuario", tenant);
    }

    /** Devuelve los tenant_id de las filas visibles de una tabla bajo el contexto RLS dado. */
    private List<UUID> tenantIdsDe(String tabla, UUID tenant) throws SQLException {
        List<UUID> resultado = new ArrayList<>();
        try (Connection appConn = appDataSource.getConnection()) {
            appConn.setAutoCommit(false);
            fijarTenant(appConn, tenant);
            try (Statement st = appConn.createStatement();
                    ResultSet rs = st.executeQuery("SELECT tenant_id FROM " + tabla)) {
                while (rs.next()) {
                    resultado.add((UUID) rs.getObject("tenant_id"));
                }
            }
            appConn.rollback();
        }
        return resultado;
    }

    private long contarFilas(Connection conn, String tabla) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM " + tabla)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Replica {@link TenantSessionInitializer}: fija app.current_tenant local a la transacción. */
    private void fijarTenant(Connection conn, UUID tenant) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static DataSource appDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(APP_ROLE);
        ds.setPassword(APP_PASSWORD);
        return ds;
    }
}

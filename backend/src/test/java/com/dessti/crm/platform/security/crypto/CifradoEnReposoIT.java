package com.dessti.crm.platform.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración de la Tarea 6.3: verifica el <b>cifrado de datos
 * sensibles en reposo</b> sobre una base de datos PostgreSQL real
 * (Testcontainers), usando {@link CampoCifradoConverter} en una entidad de
 * prueba y {@link ServicioCifradoAesGcm} con llaves de PRUEBA. Cubre los
 * Requisitos 67 (cifrado en reposo, gestión y rotación de la Llave_Cifrado,
 * bloqueo controlado sin exponer la llave) y 11 (material fuera del código).
 *
 * <p>Se verifica:</p>
 * <ol>
 *   <li><b>(a) Round-trip en BD:</b> el valor guardado en la COLUMNA está
 *       cifrado (prefijo {@code alias:} y sin el texto plano), mientras que al
 *       leer la entidad por JPA se descifra correctamente.</li>
 *   <li><b>(b) Fallo controlado:</b> si la versión de llave requerida para
 *       descifrar no está disponible, se lanza
 *       {@link LlaveCifradoNoDisponibleException} sin incluir el material de la
 *       llave en el mensaje.</li>
 *   <li><b>(c) Rotación:</b> un dato cifrado con {@code v1} sigue siendo
 *       descifrable tras activar {@code v2}; los datos nuevos se cifran con
 *       {@code v2}.</li>
 * </ol>
 *
 * <p>El {@link EntityManagerFactory} se arranca de forma programática contra el
 * contenedor, con {@code hbm2ddl=create} para la única entidad de prueba, de
 * modo que la prueba es autocontenida y no depende del contexto completo de
 * Spring.</p>
 */
@Testcontainers
@DisplayName("Tarea 6.3 - Cifrado en reposo y rotación de llave con Testcontainers (Req 67, 11)")
class CifradoEnReposoIT {

    /** Llaves de PRUEBA (AES-256 = 32 bytes) en Base64. NO son llaves reales. */
    private static String llaveV1Base64;
    private static String llaveV2Base64;
    private static SecretKey llaveV1;
    private static SecretKey llaveV2;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("crm")
                    .withUsername("crm_owner")
                    .withPassword("owner_secret_test");

    private static EntityManagerFactory emf;

    @BeforeAll
    static void arrancar() {
        SecureRandom rnd = new SecureRandom();
        byte[] k1 = new byte[32];
        byte[] k2 = new byte[32];
        rnd.nextBytes(k1);
        rnd.nextBytes(k2);
        llaveV1Base64 = Base64.getEncoder().encodeToString(k1);
        llaveV2Base64 = Base64.getEncoder().encodeToString(k2);
        llaveV1 = new SecretKeySpec(k1, "AES");
        llaveV2 = new SecretKeySpec(k2, "AES");

        emf = crearEntityManagerFactory();
    }

    @AfterAll
    static void cerrar() {
        if (emf != null) {
            emf.close();
        }
        // Testcontainers detiene el contenedor automáticamente.
    }

    // ------------------------------------------------------------------------
    // (a) Round-trip en BD: columna cifrada, entidad descifrada por JPA.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("(a) El valor se guarda CIFRADO en la columna y se descifra al leer por JPA")
    void valorCifradoEnColumnaYDescifradoPorJpa() throws SQLException {
        // Servicio de cifrado con v1 activa.
        CifradoHolder.inicializar(new ServicioCifradoAesGcm(
                new ProveedorLlavesEnMemoria("v1", Map.of("v1", llaveV1))));

        UUID id = UUID.randomUUID();
        String textoPlano = "RFC-SECRETO-XAXX010101000";
        persistir(new EntidadSensiblePrueba(id, textoPlano, "publico"));

        // Lectura CRUDA de la columna (sin el converter): debe estar cifrada.
        String columnaCruda = leerColumnaCruda(id, "dato_sensible");
        assertThat(columnaCruda)
                .as("La columna debe contener el prefijo de versión de llave 'v1:'")
                .startsWith("v1:");
        assertThat(columnaCruda)
                .as("El texto plano NO debe aparecer en la columna cifrada")
                .doesNotContain(textoPlano);

        // El campo en claro (control) NO se cifra.
        assertThat(leerColumnaCruda(id, "dato_claro")).isEqualTo("publico");

        // Lectura por JPA (con el converter): descifra correctamente.
        EntidadSensiblePrueba recuperada = buscar(id);
        assertThat(recuperada).isNotNull();
        assertThat(recuperada.getDatoSensible())
                .as("Al leer por JPA el valor debe descifrarse al texto plano original")
                .isEqualTo(textoPlano);
    }

    // ------------------------------------------------------------------------
    // (b) Fallo controlado sin exponer la llave si falta la versión al descifrar.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("(b) Si la versión de llave no está disponible al descifrar, falla sin exponer el material")
    void descifradoFallaControladoSinExponerLlave() throws SQLException {
        // Se cifra y persiste con v1.
        CifradoHolder.inicializar(new ServicioCifradoAesGcm(
                new ProveedorLlavesEnMemoria("v1", Map.of("v1", llaveV1))));
        UUID id = UUID.randomUUID();
        String textoPlano = "DATO-FISCAL-CONFIDENCIAL";
        persistir(new EntidadSensiblePrueba(id, textoPlano, "publico"));

        // Se reconfigura el holder con un proveedor que YA NO tiene v1 (solo v2):
        // simula la indisponibilidad de la versión requerida (Req 67.6).
        CifradoHolder.inicializar(new ServicioCifradoAesGcm(
                new ProveedorLlavesEnMemoria("v2", Map.of("v2", llaveV2))));

        Throwable lanzada = org.assertj.core.api.Assertions.catchThrowable(() -> {
            EntidadSensiblePrueba e = buscar(id);
            // El acceso al campo dispara el converter (descifrado) durante la carga.
            e.getDatoSensible();
        });

        assertThat(lanzada)
                .as("El descifrado sin la versión de llave requerida debe fallar")
                .isNotNull();

        // La causa raíz es el bloqueo controlado del subsistema de cifrado (Req 67.6).
        // Hibernate puede envolver la excepción del converter en una PersistenceException;
        // por eso se busca LlaveCifradoNoDisponibleException a lo largo de la cadena de causas.
        LlaveCifradoNoDisponibleException causaControlada = buscarEnCadena(
                lanzada, LlaveCifradoNoDisponibleException.class);
        assertThat(causaControlada)
                .as("La causa raíz debe ser el bloqueo controlado por llave no disponible")
                .isNotNull();

        // No se expone el material de la llave en NINGÚN mensaje de la cadena (Req 67.2, 67.6).
        for (Throwable t = lanzada; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null) {
                assertThat(msg).doesNotContain(llaveV1Base64);
                assertThat(msg).doesNotContain(llaveV2Base64);
            }
        }
    }

    // ------------------------------------------------------------------------
    // (c) Rotación: dato de v1 sigue descifrable tras activar v2; lo nuevo usa v2.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("(c) Rotación: dato cifrado con v1 sigue descifrable tras activar v2; lo nuevo se cifra con v2")
    void rotacionConservaDescifradoDeDatosPrevios() throws SQLException {
        // Estado inicial: v1 activa.
        CifradoHolder.inicializar(new ServicioCifradoAesGcm(
                new ProveedorLlavesEnMemoria("v1", Map.of("v1", llaveV1))));
        UUID idViejo = UUID.randomUUID();
        String textoViejo = "PERSONAL-PREVIO-A-ROTACION";
        persistir(new EntidadSensiblePrueba(idViejo, textoViejo, "publico"));
        assertThat(leerColumnaCruda(idViejo, "dato_sensible")).startsWith("v1:");

        // ROTACIÓN: v2 pasa a ser la activa, v1 permanece disponible para descifrar.
        CifradoHolder.inicializar(new ServicioCifradoAesGcm(
                new ProveedorLlavesEnMemoria("v2", Map.of("v1", llaveV1, "v2", llaveV2))));

        // El dato previo (cifrado con v1) SIGUE siendo descifrable.
        EntidadSensiblePrueba recuperadoViejo = buscar(idViejo);
        assertThat(recuperadoViejo.getDatoSensible())
                .as("Tras rotar a v2, el dato previo cifrado con v1 debe seguir descifrándose")
                .isEqualTo(textoViejo);

        // Un dato NUEVO se cifra ahora con la versión activa v2.
        UUID idNuevo = UUID.randomUUID();
        String textoNuevo = "PERSONAL-POSTERIOR-A-ROTACION";
        persistir(new EntidadSensiblePrueba(idNuevo, textoNuevo, "publico"));
        assertThat(leerColumnaCruda(idNuevo, "dato_sensible"))
                .as("El dato nuevo debe cifrarse con la versión activa 'v2:'")
                .startsWith("v2:");

        // Y también es descifrable por JPA.
        assertThat(buscar(idNuevo).getDatoSensible()).isEqualTo(textoNuevo);
    }

    // ------------------------------------------------------------------------
    // Helpers de persistencia y lectura cruda.
    // ------------------------------------------------------------------------

    private void persistir(EntidadSensiblePrueba entidad) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(entidad);
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private EntidadSensiblePrueba buscar(UUID id) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(EntidadSensiblePrueba.class, id);
        } finally {
            em.close();
        }
    }

    /** Recorre la cadena de causas buscando una excepción del tipo dado. */
    private <T extends Throwable> T buscarEnCadena(Throwable inicio, Class<T> tipo) {
        for (Throwable t = inicio; t != null; t = t.getCause()) {
            if (tipo.isInstance(t)) {
                return tipo.cast(t);
            }
        }
        return null;
    }

    private String leerColumnaCruda(UUID id, String columna) throws SQLException {
        try (Connection conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT " + columna + " FROM entidad_sensible_prueba WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("La fila debe existir en la BD").isTrue();
                return rs.getString(1);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Bootstrap programático del EntityManagerFactory contra el contenedor.
    // ------------------------------------------------------------------------

    private static EntityManagerFactory crearEntityManagerFactory() {
        Properties props = new Properties();
        props.put(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.postgresql.Driver");
        props.put(AvailableSettings.JAKARTA_JDBC_URL, POSTGRES.getJdbcUrl());
        props.put(AvailableSettings.JAKARTA_JDBC_USER, POSTGRES.getUsername());
        props.put(AvailableSettings.JAKARTA_JDBC_PASSWORD, POSTGRES.getPassword());
        props.put(AvailableSettings.HBM2DDL_AUTO, "create");
        props.put(AvailableSettings.SHOW_SQL, "false");

        PersistenceUnitInfoPrueba puInfo = new PersistenceUnitInfoPrueba(
                "cifrado-it-pu",
                List.of(EntidadSensiblePrueba.class.getName()),
                props);

        return new HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(puInfo, Map.of());
    }
}

package com.dessti.crm.vertical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.dessti.crm.platform.security.jwt.ServicioTokensJwt;
import com.dessti.crm.platform.security.jwt.TokenEmitido;
import com.dessti.crm.platform.vertical.RegistroVerticales;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de integracion de <strong>coexistencia y aislamiento por Giro entre DOS
 * verticales</strong> (Tarea 12.3, Req 12.4).
 *
 * <p>Con el Vertical_Anuncios (Giro {@code anuncios-luminosos}, modulos
 * {@code operacion}/{@code mantenimiento}) y el esqueleto de
 * Vertical_Manufactura (Giro {@code manufactura}, modulo
 * {@code produccion-industrial}) <strong>ambos registrados y coexistiendo</strong>
 * en el mismo contexto de Spring, se verifica que el aislamiento por
 * {@code Gating_Por_Giro} funciona en <strong>ambos sentidos</strong>: una
 * Empresa de un Giro no puede operar los flujos del otro Giro.</p>
 *
 * <h2>Escenarios cubiertos</h2>
 * <ol>
 *   <li><b>(1) Aislamiento manufactura &rarr; anuncios (END-TO-END, Req 12.4).</b>
 *       Una Empresa de Giro {@code manufactura}, <em>con</em> el permiso RBAC
 *       {@code levantamiento_sitio:listar}, invoca un endpoint del
 *       Vertical_Anuncios que <strong>conserva</strong> el gating por Giro
 *       ({@code GET /levantamientos}) y recibe <strong>403</strong>. El RBAC
 *       ({@code tiene}) se satisface, pero
 *       {@code giroCorresponde('operacion' -> 'anuncios-luminosos')} NO coincide
 *       con {@code manufactura}. Se ejercita con seguridad real (cadena de filtros
 *       JWT + {@code @PreAuthorize}) contra PostgreSQL real (Testcontainers),
 *       reutilizando el patron EXACTO de {@code GatingPorGiroVerticalIT}.
 *       <p><em>Nota (tarea 1.3):</em> antes se usaba {@code GET
 *       /ordenes-fabricacion}, pero al mover Orden_Fabricacion al Nucleo
 *       {@code operacion} su {@code @PreAuthorize} ya NO exige
 *       {@code giroCorresponde}; por eso el ejemplo de "bloqueo por Giro" migro a
 *       {@code levantamiento_sitio}, que sigue siendo especifico de anuncios
 *       (Req 16.6). El nuevo comportamiento de Orden_Fabricacion se cubre en el
 *       escenario (4).</p></li>
 *   <li><b>(2) Aislamiento reciproco a nivel de componente (Req 12.4).</b> Con
 *       ambos verticales enchufados, se inyecta el {@link RegistroVerticales}
 *       (indice determinista construido en el arranque) y se comprueba que:
 *       <ul>
 *         <li>{@code girosRegistrados()} contiene AMBOS Giros
 *             ({@code anuncios-luminosos} y {@code manufactura}), evidenciando la
 *             coexistencia;</li>
 *         <li>{@code giroDeModulo('produccion-industrial')} resuelve a
 *             {@code manufactura} y {@code giroDeModulo('operacion')} resuelve a
 *             {@code anuncios-luminosos}: cada modulo pertenece a su Giro y solo a
 *             el.</li>
 *       </ul>
 *       De este indice se deriva el aislamiento reciproco: como el modulo
 *       {@code produccion-industrial} pertenece a {@code manufactura}, el
 *       {@code Autorizador.giroCorresponde('produccion-industrial')} evaluado para
 *       una Empresa de Giro {@code anuncios-luminosos} devuelve {@code false}
 *       (giro del modulo != giro del tenant), denegando el acceso. El esqueleto de
 *       manufactura NO expone endpoints REST propios (es una prueba del modelo,
 *       ver {@code ManufacturaVertical}); verificar el indice de resolucion
 *       modulo&rarr;Giro es la forma mas limpia y <em>determinista</em> de probar
 *       el sentido reciproco sin inventar endpoints de produccion para el test.
 *       El comportamiento exacto de {@code giroCorresponde} con Giros distintos ya
 *       esta cubierto por las pruebas de {@code Autorizador}
 *       ({@code AutorizadorDobleGatingPropertyTest} / Property 4, Req 12.4).</li>
 *   <li><b>(3) Camino feliz de la coexistencia (END-TO-END, Req 12.4).</b> Una
 *       Empresa de Giro {@code anuncios-luminosos} con el permiso
 *       {@code levantamiento_sitio:listar} SI accede al mismo endpoint de anuncios
 *       ({@code 2xx}), confirmando que enchufar un segundo vertical
 *       ({@code manufactura}) no rompe el camino feliz del primero.</li>
 *   <li><b>(4) Orden_Fabricacion generalizada al Nucleo (tarea 1.3, Req 2.1).</b>
 *       La misma Empresa de Giro {@code manufactura} del escenario (1), ahora con
 *       el permiso {@code orden_fabricacion:listar} y el modulo {@code operacion}
 *       contratado, invoca {@code GET /ordenes-fabricacion} y <strong>NO</strong>
 *       recibe 403 por Giro ({@code 2xx}): el recurso del Nucleo solo exige modulo
 *       + permiso, sin amarre a Giro.</li>
 * </ol>
 *
 * <h2>Enfoque de autenticacion y secretos de PRUEBA</h2>
 * <p>Identico a {@code GatingPorGiroVerticalIT}: se emiten {@code Token_Acceso}
 * JWT <strong>reales</strong> con la pieza de produccion {@link ServicioTokensJwt}
 * (firma de PRUEBA publicada en las propiedades de secretos), fijando el
 * {@code tenant_id} = Empresa sembrada, el permiso atomico y el {@code giro}
 * (ultimo parametro de {@code emitirTokenAcceso}, Req 9.1/10.1). El gating por
 * Giro se decide en BD (via {@code GiroEmpresaPort}/{@code Autorizador}), no por el
 * claim {@code giro}. Los secretos de PRUEBA (jamas reales) con valor CONSTANTE se
 * inyectan en un bloque {@code static} (corren antes del validador de secretos y de
 * {@link DynamicPropertySource}), y se liberan en {@link #limpiarSecretos()} para
 * no filtrarlos a otras pruebas del mismo fork.</p>
 *
 * <p>Trazabilidad: Req 12.4 (con dos verticales registrados, una Empresa de un Giro
 * recibe 403 en operaciones del otro).</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("Tarea 12.3 - Coexistencia y aislamiento por Giro entre anuncios y manufactura (Req 12.4)")
class CoexistenciaVerticalesIT {

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

    /** Clave del Giro del Vertical_Anuncios (sembrado por V50). */
    private static final String GIRO_ANUNCIOS = "anuncios-luminosos";
    /** Clave del Giro del esqueleto de manufactura (sembrado por V52). */
    private static final String GIRO_MANUFACTURA = "manufactura";
    /** Modulo canonico del Vertical_Anuncios que ampara {@code /ordenes-fabricacion}. */
    private static final String MODULO_ANUNCIOS = "operacion";
    /** Modulo canonico del esqueleto de manufactura. */
    private static final String MODULO_MANUFACTURA = "produccion-industrial";

    /** Empresa (tenant) de Giro manufactura: debe recibir 403 en anuncios (escenario 1). */
    private static final UUID TENANT_MANUFACTURA = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    /** Empresa (tenant) de Giro anuncios-luminosos: debe recibir 2xx en anuncios (escenario 3). */
    private static final UUID TENANT_ANUNCIOS = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    /**
     * Permiso RBAC del endpoint {@code GET /levantamientos}, que CONSERVA el
     * gating por Giro tras la tarea 1.3 (recurso especifico de anuncios, Req 16.6).
     */
    private static final String PERMISO_LISTAR_LEVANTAMIENTOS = "levantamiento_sitio:listar";
    /**
     * Permiso RBAC del endpoint {@code GET /ordenes-fabricacion}, que tras la
     * tarea 1.3 YA NO se bloquea por Giro (Nucleo: solo modulo + permiso).
     */
    private static final String PERMISO_LISTAR_OF = "orden_fabricacion:listar";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServicioTokensJwt servicioTokensJwt;

    @Autowired
    private RegistroVerticales registroVerticales;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static boolean datosSembrados;

    @BeforeEach
    void sembrar() {
        sembrarUnaVez();
    }

    @Test
    @DisplayName("(1) Empresa de Giro manufactura recibe 403 en GET /levantamientos pese al permiso RBAC (Req 12.4)")
    void giroManufactura_recibe403EnAnuncios() throws Exception {
        // Token real con el permiso 'levantamiento_sitio:listar' y tenant = Empresa de
        // Giro 'manufactura'. El RBAC (tiene) se satisface, pero giroCorresponde
        // ('operacion' -> 'anuncios-luminosos') NO coincide con 'manufactura' => 403.
        // 'levantamiento_sitio' CONSERVA el gating por Giro (Req 16.6); 'orden_fabricacion'
        // dejo de bloquearse por Giro en la tarea 1.3 (ver escenario 4).
        String token = tokenConPermiso(TENANT_MANUFACTURA, PERMISO_LISTAR_LEVANTAMIENTOS, GIRO_MANUFACTURA);

        mockMvc.perform(get("/levantamientos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("(2) El Registro reconoce AMBOS Giros y resuelve cada modulo a su Giro (aislamiento reciproco, Req 12.4)")
    void registro_reconoceAmbosGirosYModulosDisjuntos() {
        // Coexistencia: ambos verticales quedaron indexados en el arranque.
        assertThat(registroVerticales.girosRegistrados())
                .contains(GIRO_ANUNCIOS, GIRO_MANUFACTURA);

        // Cada modulo pertenece a su Giro y solo a el: de aqui se deriva que
        // giroCorresponde('produccion-industrial') sea false para una Empresa de
        // anuncios (aislamiento reciproco) y true solo para 'operacion'.
        assertThat(registroVerticales.giroDeModulo(MODULO_MANUFACTURA))
                .contains(GIRO_MANUFACTURA);
        assertThat(registroVerticales.giroDeModulo(MODULO_ANUNCIOS))
                .contains(GIRO_ANUNCIOS);
    }

    @Test
    @DisplayName("(3) Empresa de Giro anuncios-luminosos con el permiso SI accede a GET /levantamientos (2xx): la coexistencia no rompe el camino feliz (Req 12.4)")
    void giroAnuncios_permiteAccesoConManufacturaEnchufada() throws Exception {
        // Mismo permiso y mismo endpoint, pero tenant = Empresa de Giro
        // 'anuncios-luminosos': giroCorresponde permite y el RBAC autoriza => 2xx,
        // aun con el vertical de manufactura tambien registrado.
        String token = tokenConPermiso(TENANT_ANUNCIOS, PERMISO_LISTAR_LEVANTAMIENTOS, GIRO_ANUNCIOS);

        mockMvc.perform(get("/levantamientos").header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("(4) Orden_Fabricacion YA NO se bloquea por Giro: Empresa de Giro manufactura con modulo operacion + permiso accede (2xx) (tarea 1.3, Req 2.1)")
    void ordenFabricacion_noSeBloqueaPorGiro() throws Exception {
        // Tras la tarea 1.3, GET /ordenes-fabricacion solo exige
        // moduloHabilitado('operacion') and tiene('orden_fabricacion','listar'),
        // SIN giroCorresponde. La Empresa de Giro 'manufactura' contrata el modulo
        // 'operacion' (Plan + Suscripcion) y porta el permiso => 2xx (antes 403 por Giro).
        String token = tokenConPermiso(TENANT_MANUFACTURA, PERMISO_LISTAR_OF, GIRO_MANUFACTURA);

        mockMvc.perform(get("/ordenes-fabricacion").header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
    }

    // ------------------------------------------------------------------
    // Emision de token y siembra
    // ------------------------------------------------------------------

    /**
     * Emite un {@code Token_Acceso} JWT real (mismo servicio de produccion) para
     * un Usuario sintetico del {@code tenant} indicado con un unico permiso
     * atomico. El {@code JwtAuthenticationFilter} lo validara con la misma clave
     * de firma de PRUEBA publicada en las propiedades de secretos.
     *
     * @param tenant  tenant_id que ira en el claim (== Empresa sembrada).
     * @param permiso permiso atomico {@code recurso:operacion}.
     * @param giro    clave del Giro de la Empresa que ira en el claim {@code giro}
     *                (Req 9.1); coincide con el Giro sembrado para el tenant. El
     *                gating por Giro del IT se decide en BD (via
     *                {@code GiroEmpresaPort}/Autorizador), no por este claim; se
     *                incluye para emitir un token realista y coherente.
     * @return el token JWT firmado, listo para la cabecera Authorization.
     */
    private String tokenConPermiso(UUID tenant, String permiso, String giro) {
        TokenEmitido emitido = servicioTokensJwt.emitirTokenAcceso(
                "usuario-it-" + tenant, tenant, List.of("produccion"), List.of(permiso), giro,
                "usuario-it-" + tenant, List.of(MODULO_ANUNCIOS));
        return emitido.valor();
    }

    private synchronized void sembrarUnaVez() {
        if (datosSembrados) {
            return;
        }
        // El Giro 'manufactura' ya lo siembra la migracion V52 y 'anuncios-luminosos'
        // la V50; las Empresas raiz se enlazan por subconsulta a la clave del Giro. La
        // tabla empresa NO tiene RLS (dato de tenant; V2), asi que se insertan con SQL
        // nativo plano. giro_id es obligatorio desde V51.
        crearEmpresa(TENANT_MANUFACTURA, "MAN020202AAA", GIRO_MANUFACTURA);
        crearEmpresa(TENANT_ANUNCIOS, "ANU020202AAA", GIRO_ANUNCIOS);
        // Ambas Empresas contratan el modulo 'operacion' (Plan + Suscripcion
        // activa), gating por Plan (Req 25.4):
        //   - anuncios: escenario 3 (GET /levantamientos, 2xx).
        //   - manufactura: escenario 4 (GET /ordenes-fabricacion, 2xx), donde ya
        //     no hay gating por Giro y solo se exige modulo + permiso. En el
        //     escenario 1 (GET /levantamientos con Giro manufactura) el gating por
        //     Giro sigue denegando (403) aun con el modulo habilitado.
        crearPlanConModuloOperacion();
        crearSuscripcionActiva(TENANT_ANUNCIOS);
        crearSuscripcionActiva(TENANT_MANUFACTURA);
        datosSembrados = true;
    }

    /** Id fijo del Plan de prueba que habilita el modulo {@code operacion}. */
    private static final UUID PLAN_OPERACION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    /**
     * Inserta un {@code plan} que habilita el modulo {@code operacion} en su
     * {@code modulos_habilitados} (JSONB). Idempotente por id.
     */
    private void crearPlanConModuloOperacion() {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO plan (id, nombre, max_usuarios, modulos_habilitados) "
                            + "VALUES (:id, :nombre, 50, '[\"operacion\"]'::jsonb) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", PLAN_OPERACION)
                    .setParameter("nombre", "Plan operacion (IT coexistencia)")
                    .executeUpdate();
            return null;
        });
    }

    /**
     * Inserta una {@code suscripcion} <strong>activa</strong> del {@code tenant}
     * indicado al Plan de operacion, sin override (hereda el catalogo del Plan).
     * Idempotente por id derivado del tenant.
     */
    private void crearSuscripcionActiva(UUID tenant) {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO suscripcion (id, tenant_id, plan_id, estado, vigencia_inicio) "
                            + "VALUES (:id, :tenant, :plan, 'activa', CURRENT_DATE) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", tenant)
                    .setParameter("tenant", tenant)
                    .setParameter("plan", PLAN_OPERACION)
                    .executeUpdate();
            return null;
        });
    }

    /**
     * Inserta la fila padre {@code empresa} (raiz del tenant) con un id FIJO y su
     * {@code giro_id} enlazado por subconsulta a la clave del Giro indicado. La
     * tabla {@code empresa} NO tiene RLS (V2), por lo que el INSERT es plano.
     * Idempotente ({@code ON CONFLICT (id) DO NOTHING}).
     *
     * @param id        id fijo de la Empresa (== tenant_id del token).
     * @param rfc       RFC valido (VARCHAR(13), NOT NULL).
     * @param claveGiro clave del Giro al que se enlaza ({@code manufactura} o
     *                  {@code anuncios-luminosos}).
     */
    private void crearEmpresa(UUID id, String rfc, String claveGiro) {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO empresa (id, nombre, rfc, estado, giro_id) "
                            + "VALUES (:id, :nombre, :rfc, 'activa', "
                            + "(SELECT id FROM giro WHERE clave = :clave)) "
                            + "ON CONFLICT (id) DO NOTHING")
                    .setParameter("id", id)
                    .setParameter("nombre", "Empresa " + rfc)
                    .setParameter("rfc", rfc)
                    .setParameter("clave", claveGiro)
                    .executeUpdate();
            return null;
        });
    }
}

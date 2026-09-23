package com.dessti.crm.vertical.anuncios;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de integracion END-TO-END del <strong>doble gating por Giro</strong>
 * (Tarea 8.7, Req 6.1) y, de forma secundaria, del aislamiento por
 * {@code tenant_id}/RLS de las entidades del vertical (Req 8.3).
 *
 * <p>Arranca el contexto completo de Spring con la <strong>seguridad real</strong>
 * (cadena de filtros JWT + {@code @PreAuthorize}) contra una base de datos
 * PostgreSQL real (Testcontainers), y ejercita un endpoint del vertical de
 * anuncios que <strong>sigue</strong> protegido por Gating_Por_Giro:
 * {@code GET /levantamientos}, con
 * {@code @autorizador.moduloHabilitado('operacion') and
 * @autorizador.giroCorresponde('operacion') and
 * @autorizador.tiene('levantamiento_sitio','listar')}.</p>
 *
 * <p><strong>Nota de generalizacion (tarea 1.3, operacion-produccion-enterprise).</strong>
 * Tras mover Orden_Fabricacion y Proyecto al Nucleo {@code operacion}, sus
 * {@code @PreAuthorize} ya NO exigen {@code giroCorresponde('operacion')} (solo
 * {@code moduloHabilitado('operacion') and tiene(...)}, patron de
 * {@code MaterialController}). Por eso {@code GET /ordenes-fabricacion} dejo de ser
 * un ejemplo valido de "bloqueo por Giro": un Giro no-anuncios CON el modulo
 * {@code operacion} y el permiso {@code orden_fabricacion:listar} ahora SI accede.
 * Este IT prueba el gating por Giro sobre un recurso que lo <strong>conserva</strong>
 * ({@code levantamiento_sitio}, especifico de anuncios; Req 16.6) y, ademas,
 * verifica explicitamente que Orden_Fabricacion ya NO se bloquea por Giro.</p>
 *
 * <h2>Escenarios cubiertos</h2>
 * <ol>
 *   <li><b>(A) Gating por Giro deniega (Req 6.1).</b> Un Usuario de una Empresa
 *       cuyo Giro NO es {@code anuncios-luminosos} (aqui, un Giro
 *       {@code manufactura} sembrado ad-hoc) recibe <strong>403</strong> al
 *       invocar {@code GET /levantamientos}, <em>aun teniendo</em> el permiso RBAC
 *       {@code levantamiento_sitio:listar}. La denegacion la produce
 *       {@code giroCorresponde('operacion')}, que compara el Giro del modulo
 *       (registrado por {@code AnunciosVertical} como {@code anuncios-luminosos})
 *       contra el Giro del tenant resuelto por {@code GiroEmpresaPort} desde la
 *       BD ({@code empresa.giro_id -> giro.clave}).</li>
 *   <li><b>(B) Gating por Giro permite.</b> Un Usuario de una Empresa de Giro
 *       {@code anuncios-luminosos} con el mismo permiso invoca el endpoint y NO
 *       recibe 403 (obtiene 2xx), confirmando que el gating admite cuando el
 *       Giro coincide.</li>
 *   <li><b>(C) Orden_Fabricacion ya NO se bloquea por Giro (tarea 1.3).</b> El
 *       mismo Usuario de Giro {@code manufactura} del escenario A, ahora con el
 *       permiso {@code orden_fabricacion:listar}, invoca {@code GET
 *       /ordenes-fabricacion} y <strong>NO</strong> recibe 403 por Giro (obtiene
 *       2xx): el endpoint del Nucleo solo exige modulo {@code operacion} +
 *       permiso, sin amarre a Giro.</li>
 * </ol>
 *
 * <p><b>(C) RLS del vertical (Req 8.3).</b> El aislamiento por {@code tenant_id}
 * y RLS de las entidades de negocio del vertical (p. ej. {@code orden_fabricacion})
 * ya esta verificado END-TO-END por los ITs de RLS existentes del Nucleo
 * ({@code com.dessti.crm.platform.tenant.RlsMultiTenantIT}) y por
 * {@code IndicadoresTableroDatosVivosIT}, que siembra {@code orden_fabricacion}
 * por tenant y comprueba que un tenant no ve los datos del otro. Las tablas del
 * vertical replican EXACTAMENTE el patron RLS por {@code tenant_id} del Nucleo
 * (misma politica {@code tenant_isolation} en sus migraciones), por lo que aqui
 * no se reimplementa ese montaje y esta prueba se centra en el corazon del Req
 * 6.1 (gating por Giro), que es lo especifico de la plataforma multigiro.</p>
 *
 * <h2>Enfoque de autenticacion</h2>
 * <p>En lugar de un login real (que exigiria sembrar un Usuario con contrasena
 * BCrypt y sus roles), se emite un {@code Token_Acceso} JWT <strong>real</strong>
 * con la pieza de produccion {@link ServicioTokensJwt} (bean del contexto),
 * fijando el {@code tenant_id} = Empresa sembrada y el permiso atomico
 * {@code orden_fabricacion:listar}. El token viaja en
 * {@code Authorization: Bearer <token>}; el {@code JwtAuthenticationFilter} lo
 * valida, construye el principal {@code UsuarioAutenticado} (que expone el
 * {@code tenant_id}) y el {@code TenantResolutionFilter} fija el
 * {@code TenantContext}. Asi el {@code Autorizador} evalua el gating con datos
 * reales de BD, exactamente como en produccion. El {@code tenant_id} DEBE
 * corresponder a una Empresa real con su {@code giro_id} porque
 * {@code GiroEmpresaPort.giroDeTenant} lo consulta en la BD.</p>
 *
 * <h2>Secretos de PRUEBA y arranque temprano</h2>
 * <p>Identico patron que {@code ArranqueContextoSmokeIT} e
 * {@code IndicadoresTableroDatosVivosIT}: el validador de secretos
 * ({@code SecretosEnvironmentPostProcessor}) corre muy temprano, antes de
 * {@link DynamicPropertySource}, por lo que los secretos de PRUEBA (jamas
 * reales) con valor CONSTANTE se inyectan como propiedades de sistema en un
 * bloque {@code static}. La clave de firma JWT de PRUEBA es la MISMA que se
 * publica en {@code crm.secretos.jwt-signing-key}/{@code JWT_SIGNING_KEY}, de
 * modo que el {@link ServicioTokensJwt} del contexto firma y valida con la
 * misma clave. Solo la URL con puerto aleatorio queda en
 * {@link DynamicPropertySource}. Las propiedades de sistema se liberan en
 * {@link #limpiarSecretos()} para no filtrarlas a otras pruebas del mismo fork.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("Tarea 8.7 - Doble gating por Giro END-TO-END en el vertical de anuncios (Req 6.1, 8.3)")
class GatingPorGiroVerticalIT {

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

    /** Empresa (tenant) de Giro AJENO (manufactura): debe recibir 403 (escenario A). */
    private static final UUID TENANT_MANUFACTURA = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    /** Empresa (tenant) de Giro anuncios-luminosos: debe recibir 2xx (escenario B). */
    private static final UUID TENANT_ANUNCIOS = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    /**
     * Permiso RBAC del endpoint {@code GET /levantamientos}, que CONSERVA el
     * gating por Giro ({@code giroCorresponde('operacion')}) tras la tarea 1.3.
     */
    private static final String PERMISO_LISTAR_LEVANTAMIENTOS = "levantamiento_sitio:listar";
    /**
     * Permiso RBAC del endpoint {@code GET /ordenes-fabricacion}, que tras la
     * tarea 1.3 YA NO se bloquea por Giro (solo modulo {@code operacion} +
     * permiso). Se usa en el escenario C para verificar la generalizacion.
     */
    private static final String PERMISO_LISTAR_OF = "orden_fabricacion:listar";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServicioTokensJwt servicioTokensJwt;

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
    @DisplayName("(A) Empresa de otro Giro (manufactura) recibe 403 en GET /levantamientos pese al permiso RBAC (Req 6.1)")
    void giroAjeno_recibe403() throws Exception {
        // Token real con el permiso 'levantamiento_sitio:listar' y tenant = Empresa de
        // Giro 'manufactura'. El RBAC (tiene) se satisface, pero giroCorresponde
        // ('operacion' -> 'anuncios-luminosos') NO coincide con 'manufactura' => 403.
        // Se usa 'levantamiento_sitio' porque CONSERVA el gating por Giro (Req 16.6);
        // 'orden_fabricacion' dejo de bloquearse por Giro en la tarea 1.3.
        String token = tokenConPermiso(TENANT_MANUFACTURA, PERMISO_LISTAR_LEVANTAMIENTOS, "manufactura");

        mockMvc.perform(get("/levantamientos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("(B) Empresa de Giro anuncios-luminosos con el permiso SI accede a GET /levantamientos (2xx) (Req 6.1)")
    void giroCorrecto_permiteAcceso() throws Exception {
        // Mismo permiso y mismo endpoint, pero tenant = Empresa de Giro
        // 'anuncios-luminosos': giroCorresponde permite y el RBAC autoriza => 2xx.
        String token = tokenConPermiso(TENANT_ANUNCIOS, PERMISO_LISTAR_LEVANTAMIENTOS, "anuncios-luminosos");

        mockMvc.perform(get("/levantamientos").header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("(C) Orden_Fabricacion YA NO se bloquea por Giro: una Empresa de Giro manufactura con modulo operacion + permiso accede (2xx) (tarea 1.3, Req 2.1)")
    void ordenFabricacion_noSeBloqueaPorGiro() throws Exception {
        // Tras la tarea 1.3, GET /ordenes-fabricacion solo exige
        // moduloHabilitado('operacion') and tiene('orden_fabricacion','listar'),
        // SIN giroCorresponde. La Empresa de Giro 'manufactura' contrata el modulo
        // 'operacion' (Plan + Suscripcion, sembrados abajo) y porta el permiso, por
        // lo que ahora accede (2xx) donde antes el Giro la denegaba (403).
        String token = tokenConPermiso(TENANT_MANUFACTURA, PERMISO_LISTAR_OF, "manufactura");

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
     * @param giro    clave del Giro de la Empresa que ira en el claim
     *                {@code giro} (Req 9.1); coincide con el Giro sembrado para
     *                el tenant. El gating por Giro del IT se decide en BD (via
     *                {@code GiroEmpresaPort}/Autorizador), no por este claim; se
     *                incluye para emitir un token realista y coherente.
     * @return el token JWT firmado, listo para la cabecera Authorization.
     */
    private String tokenConPermiso(UUID tenant, String permiso, String giro) {
        TokenEmitido emitido = servicioTokensJwt.emitirTokenAcceso(
                "usuario-it-" + tenant, tenant, List.of("produccion"), List.of(permiso), giro,
                "usuario-it-" + tenant, List.of("operacion"));
        return emitido.valor();
    }

    private synchronized void sembrarUnaVez() {
        if (datosSembrados) {
            return;
        }
        // 1) Giro 'manufactura' activo (ademas de 'anuncios-luminosos', ya sembrado por
        //    V50). Se inserta con SQL nativo por la clave natural; idempotente.
        crearGiroManufactura();
        // 2) Empresas raiz del arbol multi-tenant. La tabla empresa NO tiene RLS
        //    (dato de tenant; V2), asi que se insertan con SQL nativo plano. giro_id es
        //    obligatorio desde V51: se enlaza por subconsulta a la clave del Giro.
        crearEmpresa(TENANT_MANUFACTURA, "MAN010101AAA", "manufactura");
        crearEmpresa(TENANT_ANUNCIOS, "ANU010101AAA", "anuncios-luminosos");
        // 3) Ambas Empresas contratan el modulo 'operacion' via un Plan y una
        //    Suscripcion activa (gating por Plan, Req 25.4):
        //    - anuncios: para el escenario B (GET /levantamientos, 2xx).
        //    - manufactura: para el escenario C (GET /ordenes-fabricacion, 2xx),
        //      donde ya no hay gating por Giro y solo se exige el modulo + permiso.
        //    En el escenario A (GET /levantamientos con Giro manufactura) el gating
        //    por Giro deniega (403) aunque el modulo este habilitado.
        crearPlanConModuloOperacion();
        crearSuscripcionActiva(TENANT_ANUNCIOS);
        crearSuscripcionActiva(TENANT_MANUFACTURA);
        datosSembrados = true;
    }

    /** Id fijo del Plan de prueba que habilita el modulo {@code operacion}. */
    private static final UUID PLAN_OPERACION = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

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
                    .setParameter("nombre", "Plan operacion (IT gating)")
                    .executeUpdate();
            return null;
        });
    }

    /**
     * Inserta una {@code suscripcion} <strong>activa</strong> del {@code tenant}
     * indicado al Plan de operacion, sin override de modulos (hereda el catalogo
     * del Plan). Idempotente por id derivado del tenant.
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
     * Inserta el Giro {@code manufactura} activo con SQL nativo (dato de
     * plataforma, sin RLS), en su propia transaccion. Idempotente por la clave
     * natural {@code clave}. Permite validar el escenario A: una Empresa cuyo Giro
     * es distinto de {@code anuncios-luminosos}.
     */
    private void crearGiroManufactura() {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO giro (clave, nombre_visible, descripcion, activo) "
                            + "VALUES ('manufactura', 'Manufactura', "
                            + "'Giro de prueba para el gating por Giro (Tarea 8.7).', TRUE) "
                            + "ON CONFLICT (clave) DO NOTHING")
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

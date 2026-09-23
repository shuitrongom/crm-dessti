package com.dessti.crm.platform.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.ItemNavegacionVertical;
import com.dessti.crm.platform.vertical.RegistroVerticales;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;

/**
 * Pruebas basadas en propiedades (jqwik) del <strong>doble gating</strong> (Plan
 * Y Giro) del {@link Autorizador} (Req 6.1-6.4, 12.4).
 *
 * <p>Reutiliza la pieza de produccion {@link Autorizador} tal cual, sin contexto
 * de Spring ni base de datos: el {@link PlanModulosPort} y el
 * {@link GiroEmpresaPort} se inyectan como lambdas controladas por los valores
 * generados, el {@link RegistroVerticales} se construye con {@link ContratoVertical}
 * falsos (fakes) que declaran los modulos/giros generados, y el
 * {@link AuditoriaPort} es un mock neutro (la auditoria de denegacion no altera
 * la decision).</p>
 *
 * <p>Cubre dos propiedades de diseno:</p>
 * <ul>
 *   <li><strong>Property 4</strong>: la decision efectiva del doble gating
 *       ({@code moduloHabilitado(M) && giroCorresponde(M)}, asumiendo permiso RBAC
 *       concedido) es verdadera si y solo si {@code M} esta habilitado en el Plan
 *       Y ({@code M} es de Nucleo O el Giro de {@code M} coincide con el de la
 *       Empresa).</li>
 *   <li><strong>Property 5</strong>: para todo modulo de Nucleo (giro resuelto por
 *       el registro es vacio) y todo Giro de Empresa, {@code giroCorresponde}
 *       devuelve verdadero, con o sin autenticacion/tenant (el Nucleo no depende
 *       de ellos, tal como lo implementa la pieza de produccion).</li>
 * </ul>
 *
 * <p><strong>Aislamiento entre intentos:</strong> el {@link SecurityContextHolder}
 * y el {@link TenantContext} son thread-local y jqwik reutiliza el hilo entre
 * intentos; {@link #limpiarContexto()} (anotado {@link AfterTry}) limpia ambos
 * tras cada intento para evitar fugas de estado entre iteraciones.</p>
 */
class AutorizadorDobleGatingPropertyTest {

    /** Modulo del vertical usado en la composicion del doble gating. */
    private static final String MODULO_VERTICAL = "anuncios";
    /** Recurso RBAC declarado por el vertical (irrelevante para el gating). */
    private static final String RECURSO_VERTICAL = "prueba_diseno";

    // ----------------------------------------------------------------------
    // Aislamiento entre intentos: SecurityContextHolder + TenantContext son
    // thread-local y jqwik reutiliza el hilo. Limpiar siempre tras cada try.
    // ----------------------------------------------------------------------
    @AfterTry
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ----------------------------------------------------------------------
    // Utilidades de contexto y fabricacion
    // ----------------------------------------------------------------------

    /** Autentica un principal de prueba con una authority arbitraria no vacia. */
    private static void autenticar() {
        var auth = new UsernamePasswordAuthenticationToken(
                "usuario@empresa", "n/a",
                List.of(new SimpleGrantedAuthority("cualquiera:leer")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * {@link ContratoVertical} falso que declara un unico Giro y un unico modulo,
     * sin recursos ni navegacion relevantes para el gating.
     *
     * @param giro   clave de Giro que aporta el vertical.
     * @param modulo clave del modulo que aporta el vertical.
     * @return un contrato de vertical de prueba.
     */
    private static ContratoVertical verticalFalso(String giro, String modulo) {
        return new ContratoVertical() {
            @Override
            public String giro() {
                return giro;
            }

            @Override
            public Set<String> modulos() {
                return Set.of(modulo);
            }

            @Override
            public Set<String> recursos() {
                return Set.of(RECURSO_VERTICAL);
            }

            @Override
            public List<ItemNavegacionVertical> navegacion() {
                return List.of();
            }
        };
    }

    /**
     * Construye el {@link Autorizador} bajo prueba con los colaboradores del doble
     * gating controlados por parametros.
     *
     * @param planHabilita     valor que devuelve el {@link PlanModulosPort}.
     * @param giroEmpresa      clave de Giro que devuelve el {@link GiroEmpresaPort}
     *                         (puede ser nula para simular Empresa sin Giro).
     * @param registro         registro de verticales ya construido.
     * @return el autorizador de produccion listo para evaluar.
     */
    private static Autorizador autorizador(
            boolean planHabilita, String giroEmpresa, RegistroVerticales registro) {
        PlanModulosPort plan = (tenantId, modulo) -> planHabilita;
        GiroEmpresaPort giro = tenantId -> Optional.ofNullable(giroEmpresa);
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        return new Autorizador(plan, registro, giro, auditoria);
    }

    /** Normaliza una clave de Giro igual que el {@link Autorizador} de produccion. */
    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String n = valor.strip().toLowerCase(Locale.ROOT);
        return n.isEmpty() ? null : n;
    }

    // ----------------------------------------------------------------------
    // Property 4 — Composicion del doble gating (Plan Y Giro)
    // ----------------------------------------------------------------------

    // Feature: plataforma-multigiro, Property 4: Composición del doble gating (Plan Y Giro)
    // Validates: Requirements 6.1, 6.2, 6.3, 6.4, 12.4
    @Property(tries = 500)
    void composicionDelDobleGatingEsEquivalenteAPlanYGiro(
            @ForAll boolean esModuloDeNucleo,
            @ForAll boolean planHabilita,
            @ForAll("clavesGiro") String giroEmpresa,
            @ForAll("clavesGiro") String giroDelModulo) {

        // Contexto plenamente autenticado con tenant: aislamos la composicion
        // Plan Y Giro (el permiso RBAC se asume concedido, fuera de esta property).
        autenticar();
        TenantContext.set(UUID.randomUUID());

        // Segun el caso generado, el modulo evaluado es de Nucleo (ningun vertical
        // lo declara) o de un vertical con un Giro concreto.
        final String moduloEvaluado;
        final RegistroVerticales registro;
        if (esModuloDeNucleo) {
            // El vertical declara OTRO modulo distinto al evaluado => el modulo
            // evaluado queda sin Giro asociado (es de Nucleo).
            registro = new RegistroVerticales(List.of(
                    verticalFalso(giroDelModulo, "modulo-de-otro-vertical")));
            moduloEvaluado = MODULO_VERTICAL;
        } else {
            // El vertical declara el modulo evaluado con el Giro generado.
            registro = new RegistroVerticales(List.of(
                    verticalFalso(giroDelModulo, MODULO_VERTICAL)));
            moduloEvaluado = MODULO_VERTICAL;
        }

        Autorizador autorizador = autorizador(planHabilita, giroEmpresa, registro);

        // Decision efectiva = gating por Plan AND gating por Giro (permiso RBAC
        // concedido por hipotesis). Es la expresion que compone @PreAuthorize.
        boolean decisionEfectiva =
                autorizador.moduloHabilitado(moduloEvaluado)
                        && autorizador.giroCorresponde(moduloEvaluado);

        // Modelo esperado del si-y-solo-si:
        //   habilitado en Plan Y (es de Nucleo O giro(M) == giro(Empresa)).
        boolean giroCoincide =
                normalizar(giroDelModulo) != null
                        && normalizar(giroDelModulo).equals(normalizar(giroEmpresa));
        boolean esperado = planHabilita && (esModuloDeNucleo || giroCoincide);

        assertThat(decisionEfectiva)
                .as("decision efectiva == Plan Y (Nucleo O giro(M)==giro(Empresa)); "
                        + "nucleo=%s, plan=%s, giroModulo=%s, giroEmpresa=%s",
                        esModuloDeNucleo, planHabilita, giroDelModulo, giroEmpresa)
                .isEqualTo(esperado);
    }

    // ----------------------------------------------------------------------
    // Property 5 — El Nucleo nunca se bloquea por Giro
    // ----------------------------------------------------------------------

    // Feature: plataforma-multigiro, Property 5: El Núcleo nunca se bloquea por Giro
    // Validates: Requirements 6.4
    @Property(tries = 500)
    void nucleoNuncaSeBloqueaPorGiro(
            @ForAll("modulosDeNucleo") String moduloNucleo,
            @ForAll("clavesGiro") String giroEmpresa,
            @ForAll("clavesGiro") String giroVertical,
            @ForAll boolean conAutenticacion,
            @ForAll boolean conTenant) {

        // El registro contiene un vertical que NO declara el modulo evaluado, de
        // modo que 'moduloNucleo' es de Nucleo (giroDeModulo -> vacio). El generador
        // de modulosDeNucleo evita colisionar con la clave del vertical.
        RegistroVerticales registro = new RegistroVerticales(List.of(
                verticalFalso(giroVertical, MODULO_VERTICAL)));

        // Se varia el contexto (con/sin auth, con/sin tenant) porque, tal como lo
        // implementa la pieza de produccion, el Nucleo devuelve true ANTES de mirar
        // autenticacion o tenant: no depende de ellos.
        if (conAutenticacion) {
            autenticar();
        }
        if (conTenant) {
            TenantContext.set(UUID.randomUUID());
        }

        Autorizador autorizador = autorizador(false, giroEmpresa, registro);

        assertThat(autorizador.giroCorresponde(moduloNucleo))
                .as("un modulo de Nucleo (%s) nunca se bloquea por Giro, "
                        + "auth=%s, tenant=%s, giroEmpresa=%s",
                        moduloNucleo, conAutenticacion, conTenant, giroEmpresa)
                .isTrue();
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Claves de Giro plausibles (minusculas kebab, 1..15 chars) mas variantes con
     * mayusculas/espacios envolventes para ejercitar la normalizacion del gating.
     */
    @Provide
    Arbitrary<String> clavesGiro() {
        Arbitrary<String> base = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(15)
                .withCharRange('a', 'z')
                .withChars('-');
        // Mezcla de claves normalizadas y claves "sucias" (con espacios/mayusculas)
        // para verificar que la comparacion de Giro es insensible a ellos.
        Arbitrary<String> sucias = base.map(s -> "  " + s.toUpperCase(Locale.ROOT) + "  ");
        return Arbitraries.oneOf(base, sucias);
    }

    /**
     * Claves de modulo de Nucleo: cualquier token que NO sea la clave del modulo
     * de vertical usado en la property (garantiza que el registro lo trate como
     * Nucleo). Incluye tokens con mayusculas/espacios para ejercitar la
     * normalizacion del registro.
     */
    @Provide
    Arbitrary<String> modulosDeNucleo() {
        return Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(15)
                .withCharRange('a', 'z')
                .withChars('_')
                .filter(s -> !MODULO_VERTICAL.equals(normalizar(s)));
    }
}

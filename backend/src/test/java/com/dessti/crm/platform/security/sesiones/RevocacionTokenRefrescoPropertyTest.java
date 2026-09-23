package com.dessti.crm.platform.security.sesiones;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 41: Revocacion
 * de Token_Refresco</strong> (Req 68.1, 68.2, 68.3, 68.4).
 *
 * <p>La property valida la <em>invariante de revocacion</em> del almacen de
 * Sesiones: <em>para cualquier Token_Refresco que haya sido revocado (por
 * logout, por accion de administrador, por desactivacion de la cuenta o por
 * cambio de contrasena), toda solicitud de refresco presentada con ese token se
 * rechaza y el sistema no emite un nuevo Token_Acceso</em>.</p>
 *
 * <p><strong>Enfoque de modelado (dominio puro en memoria):</strong> se ejerce
 * la <em>semantica de dominio</em> del puerto {@link RegistroSesionesPort} a
 * traves de una implementacion en memoria ({@link RegistroSesionesEnMemoria})
 * respaldada por un {@code Map} por {@code jti}. Esta implementacion reutiliza
 * la <b>entidad de produccion</b> {@link SesionRefresco} (cuyos metodos
 * {@code revocar}/{@code isRevocado}/{@code estaActiva} son puros, sin
 * dependencias de JPA) y replica linea por linea el contrato del adaptador real
 * {@link RegistroSesionesJpaAdapter}: {@code registrar} idempotente por
 * {@code jti}, {@code estaRevocado} conservador (un {@code jti} desconocido o en
 * blanco se trata como revocado), {@code revocar} idempotente que devuelve el
 * numero de sesiones revocadas (0 o 1) y {@code revocarTodasDeUsuario} acotado a
 * las sesiones activas de esa cuenta. No hay contexto de Spring, ni base de
 * datos, ni Testcontainers; el {@link Clock} se controla de forma
 * deterministica.</p>
 *
 * <p>Se prefiere este enfoque de dominio puro frente a ejercer el
 * {@code ServicioAutenticacion.refresh} real porque este ultimo exigiria
 * cablear {@code ServicioTokensJwt} y dobles adicionales, mientras que la
 * invariante que interesa (revocado &rArr; refresco rechazado, sin nuevo
 * Token_Acceso) vive integramente en el contrato del puerto de sesiones. Aqui
 * un "refresco" se modela como {@code !estaRevocado(jti)}: si el {@code jti}
 * esta revocado, no se emite un nuevo Token_Acceso.</p>
 */
class RevocacionTokenRefrescoPropertyTest {

    /** Reloj fijo (UTC): el sellado del instante de revocacion es deterministico. */
    private static final Instant AHORA = Instant.parse("2025-01-15T10:00:00Z");
    private static final Clock RELOJ_FIJO = Clock.fixed(AHORA, ZoneOffset.UTC);

    /** Ventana amplia de expiracion: las sesiones generadas estan vigentes en {@code AHORA}. */
    private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(7);

    // ----------------------------------------------------------------------
    // Property 41 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 41: Para cualquier Token_Refresco que haya sido revocado (por logout, por acción de administrador, por desactivación de la cuenta o por cambio de contraseña), toda solicitud de refresco presentada con ese token se rechaza y el sistema no emite un nuevo Token_Acceso.
    @Property(tries = 1000)
    void sesionRecienRegistradaNoEstaRevocadaYPermiteRefresco(
            @ForAll("jtis") String jti,
            @ForAll("usuarios") UUID usuarioId,
            @ForAll("tenants") UUID tenantId) {

        RegistroSesionesEnMemoria puerto = new RegistroSesionesEnMemoria(RELOJ_FIJO);
        puerto.registrar(registro(jti, usuarioId, tenantId));

        // Invariante 1: una sesion recien registrada (jti unico) NO esta revocada.
        assertThat(puerto.estaRevocado(jti))
                .as("una sesion recien registrada no debe estar revocada")
                .isFalse();
        // Y por tanto un refresco con ese jti emite un nuevo Token_Acceso.
        assertThat(refrescoEmiteNuevoAcceso(puerto, jti))
                .as("un refresco con jti activo debe emitir un nuevo Token_Acceso")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 41: Para cualquier Token_Refresco que haya sido revocado (por logout, por acción de administrador, por desactivación de la cuenta o por cambio de contraseña), toda solicitud de refresco presentada con ese token se rechaza y el sistema no emite un nuevo Token_Acceso.
    @Property(tries = 1000)
    void revocarRechazaElRefrescoYEsIdempotenteUnaVezRevocadoSiempreRevocado(
            @ForAll("jtis") String jti,
            @ForAll("usuarios") UUID usuarioId,
            @ForAll("tenants") UUID tenantId,
            @ForAll("motivos") MotivoRevocacion motivo) {

        RegistroSesionesEnMemoria puerto = new RegistroSesionesEnMemoria(RELOJ_FIJO);
        puerto.registrar(registro(jti, usuarioId, tenantId));

        // La primera revocacion cambia el estado (devuelve 1).
        assertThat(puerto.revocar(jti, motivo))
                .as("la primera revocacion de una sesion activa revoca exactamente 1")
                .isEqualTo(1);

        // Invariante 2: tras revocar, el jti esta revocado y el refresco se rechaza
        // (no se emite un nuevo Token_Acceso), sin importar el motivo (Req 68.3).
        assertThat(puerto.estaRevocado(jti)).isTrue();
        assertThat(refrescoEmiteNuevoAcceso(puerto, jti))
                .as("un refresco con jti revocado NO debe emitir un nuevo Token_Acceso")
                .isFalse();

        // Idempotencia: revocar de nuevo (incluso con otro motivo) no revierte el
        // estado; "una vez revocado, siempre revocado" (no vuelve a activo).
        for (MotivoRevocacion otro : MotivoRevocacion.values()) {
            assertThat(puerto.revocar(jti, otro))
                    .as("revocar una sesion ya revocada no cambia el estado (0)")
                    .isEqualTo(0);
            assertThat(puerto.estaRevocado(jti))
                    .as("una sesion revocada nunca vuelve a estar activa")
                    .isTrue();
            assertThat(refrescoEmiteNuevoAcceso(puerto, jti)).isFalse();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 41: Para cualquier Token_Refresco que haya sido revocado (por logout, por acción de administrador, por desactivación de la cuenta o por cambio de contraseña), toda solicitud de refresco presentada con ese token se rechaza y el sistema no emite un nuevo Token_Acceso.
    @Property(tries = 1000)
    void revocarTodasDeUsuarioRevocaSoloLasDeEsaCuenta(
            @ForAll("listaJtis") @Size(min = 1, max = 6) List<String> jtisObjetivo,
            @ForAll("listaJtis") @Size(max = 6) List<String> jtisAjenos,
            @ForAll("motivos") MotivoRevocacion motivo) {

        // jti unicos y disjuntos entre las dos cuentas para un modelo bien definido.
        List<String> objetivo = distintos(jtisObjetivo, List.of());
        List<String> ajenos = distintos(jtisAjenos, objetivo);

        UUID usuarioObjetivo = UUID.randomUUID();
        UUID usuarioAjeno = UUID.randomUUID();

        RegistroSesionesEnMemoria puerto = new RegistroSesionesEnMemoria(RELOJ_FIJO);
        for (String jti : objetivo) {
            puerto.registrar(registro(jti, usuarioObjetivo, UUID.randomUUID()));
        }
        for (String jti : ajenos) {
            puerto.registrar(registro(jti, usuarioAjeno, UUID.randomUUID()));
        }

        int revocadas = puerto.revocarTodasDeUsuario(usuarioObjetivo, motivo);

        // Invariante 3: se revocan TODAS las sesiones activas de la cuenta objetivo...
        assertThat(revocadas)
                .as("revocarTodasDeUsuario revoca todas las sesiones activas de la cuenta")
                .isEqualTo(objetivo.size());
        for (String jti : objetivo) {
            assertThat(puerto.estaRevocado(jti)).isTrue();
            assertThat(refrescoEmiteNuevoAcceso(puerto, jti)).isFalse();
        }
        // ...y NINGUNA de otra cuenta (aislamiento por usuario/tenant, Req 68.2, 68.4).
        for (String jti : ajenos) {
            assertThat(puerto.estaRevocado(jti))
                    .as("las sesiones de otra cuenta permanecen activas")
                    .isFalse();
            assertThat(refrescoEmiteNuevoAcceso(puerto, jti)).isTrue();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 41: Para cualquier Token_Refresco que haya sido revocado (por logout, por acción de administrador, por desactivación de la cuenta o por cambio de contraseña), toda solicitud de refresco presentada con ese token se rechaza y el sistema no emite un nuevo Token_Acceso.
    @Property(tries = 1000)
    void jtiDesconocidoOEnBlancoSeTrataComoRevocado(
            @ForAll("jtis") String jtiConocido,
            @ForAll("jtis") String jtiDesconocido,
            @ForAll("usuarios") UUID usuarioId,
            @ForAll("tenants") UUID tenantId) {

        RegistroSesionesEnMemoria puerto = new RegistroSesionesEnMemoria(RELOJ_FIJO);
        puerto.registrar(registro(jtiConocido, usuarioId, tenantId));

        // Invariante 4: un jti nunca registrado se rechaza de forma conservadora.
        if (!jtiDesconocido.equals(jtiConocido)) {
            assertThat(puerto.estaRevocado(jtiDesconocido))
                    .as("un jti desconocido se trata como revocado (rechazo conservador)")
                    .isTrue();
            assertThat(refrescoEmiteNuevoAcceso(puerto, jtiDesconocido)).isFalse();
        }
        // Un jti nulo o en blanco tambien se rechaza (nunca emite Token_Acceso).
        assertThat(puerto.estaRevocado(null)).isTrue();
        assertThat(puerto.estaRevocado("")).isTrue();
        assertThat(puerto.estaRevocado("   ")).isTrue();
        assertThat(refrescoEmiteNuevoAcceso(puerto, null)).isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 41: Para cualquier Token_Refresco que haya sido revocado (por logout, por acción de administrador, por desactivación de la cuenta o por cambio de contraseña), toda solicitud de refresco presentada con ese token se rechaza y el sistema no emite un nuevo Token_Acceso.
    @Property(tries = 1000)
    void rotacionRevocaElJtiPrevioYActivaElNuevoYElReplayDelViejoSigueRechazado(
            @ForAll("jtis") String jtiViejo,
            @ForAll("jtis") String jtiNuevo,
            @ForAll("usuarios") UUID usuarioId,
            @ForAll("tenants") UUID tenantId) {

        // Modela una rotacion de refresco exitosa: se exige jti distintos.
        String nuevo = jtiNuevo.equals(jtiViejo) ? jtiViejo + "-rot" : jtiNuevo;

        RegistroSesionesEnMemoria puerto = new RegistroSesionesEnMemoria(RELOJ_FIJO);
        puerto.registrar(registro(jtiViejo, usuarioId, tenantId));

        // Refresco-rotacion = revocar(jtiViejo) + registrar(jtiNuevo) (Req 68.1).
        assertThat(refrescoEmiteNuevoAcceso(puerto, jtiViejo)).isTrue();
        puerto.revocar(jtiViejo, MotivoRevocacion.LOGOUT);
        puerto.registrar(registro(nuevo, usuarioId, tenantId));

        // Invariante 5: el jti viejo queda revocado; el nuevo queda activo.
        assertThat(puerto.estaRevocado(jtiViejo)).isTrue();
        assertThat(puerto.estaRevocado(nuevo)).isFalse();
        assertThat(refrescoEmiteNuevoAcceso(puerto, nuevo)).isTrue();

        // Replay del jti viejo: sigue rechazado (no emite un nuevo Token_Acceso).
        assertThat(refrescoEmiteNuevoAcceso(puerto, jtiViejo))
                .as("reproducir el jti rotado no debe emitir un nuevo Token_Acceso")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 41: Para cualquier Token_Refresco que haya sido revocado (por logout, por acción de administrador, por desactivación de la cuenta o por cambio de contraseña), toda solicitud de refresco presentada con ese token se rechaza y el sistema no emite un nuevo Token_Acceso.
    @Property(tries = 1000)
    void losCuatroMotivosDeRevocacionProducenSesionNoRefrescable(
            @ForAll("jtis") String jti,
            @ForAll("usuarios") UUID usuarioId,
            @ForAll("tenants") UUID tenantId,
            @ForAll("motivos") MotivoRevocacion motivo) {

        RegistroSesionesEnMemoria puerto = new RegistroSesionesEnMemoria(RELOJ_FIJO);
        puerto.registrar(registro(jti, usuarioId, tenantId));

        // Invariante 6: cualquiera de los cuatro motivos (LOGOUT, ADMINISTRADOR,
        // DESACTIVACION, CAMBIO_PASSWORD) produce una sesion revocada e
        // irrefrescable; la causa no altera la invariante.
        puerto.revocar(jti, motivo);

        assertThat(puerto.estaRevocado(jti))
                .as("cualquier motivo de revocacion deja la sesion revocada")
                .isTrue();
        assertThat(refrescoEmiteNuevoAcceso(puerto, jti))
                .as("una sesion revocada por cualquier motivo no emite un nuevo Token_Acceso")
                .isFalse();
    }

    // ----------------------------------------------------------------------
    // Modelo de refresco y helpers
    // ----------------------------------------------------------------------

    /**
     * Modela una solicitud de refresco: el sistema emite un nuevo Token_Acceso
     * si (y solo si) el {@code jti} presentado NO esta revocado. Refleja la
     * semantica de {@code ServicioAutenticacion.refresh}, que rechaza (401) todo
     * refresco cuyo {@code jti} este revocado o sea desconocido.
     */
    private static boolean refrescoEmiteNuevoAcceso(RegistroSesionesPort puerto, String jti) {
        return !puerto.estaRevocado(jti);
    }

    private static RegistroSesion registro(String jti, UUID usuarioId, UUID tenantId) {
        return new RegistroSesion(jti, usuarioId, tenantId, AHORA, AHORA.plus(VIGENCIA_REFRESCO));
    }

    /** Devuelve los elementos de {@code fuente} sin duplicados ni colision con {@code excluidos}. */
    private static List<String> distintos(List<String> fuente, List<String> excluidos) {
        List<String> resultado = new ArrayList<>();
        for (String s : fuente) {
            if (!resultado.contains(s) && !excluidos.contains(s)) {
                resultado.add(s);
            }
        }
        return resultado;
    }

    // ----------------------------------------------------------------------
    // Implementacion en memoria del puerto (espeja RegistroSesionesJpaAdapter)
    // ----------------------------------------------------------------------

    /**
     * Implementacion en memoria de {@link RegistroSesionesPort} respaldada por un
     * {@code Map<String, SesionRefresco>} por {@code jti}. Reutiliza la entidad de
     * produccion {@link SesionRefresco} (metodos de dominio puros) y replica el
     * contrato de {@link RegistroSesionesJpaAdapter}.
     */
    private static final class RegistroSesionesEnMemoria implements RegistroSesionesPort {

        private final Map<String, SesionRefresco> porJti = new HashMap<>();
        private final Clock clock;

        RegistroSesionesEnMemoria(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void registrar(RegistroSesion sesion) {
            // Idempotencia por jti: no duplicar (igual que el adaptador real).
            porJti.putIfAbsent(sesion.jti(), new SesionRefresco(sesion));
        }

        @Override
        public boolean estaRevocado(String jti) {
            if (jti == null || jti.isBlank()) {
                return true;
            }
            SesionRefresco sesion = porJti.get(jti);
            // jti desconocido => revocado (rechazo conservador).
            return (sesion == null) || sesion.isRevocado();
        }

        @Override
        public int revocar(String jti, MotivoRevocacion motivo) {
            if (jti == null || jti.isBlank()) {
                return 0;
            }
            SesionRefresco sesion = porJti.get(jti);
            if (sesion == null) {
                return 0;
            }
            return sesion.revocar(motivo, clock) ? 1 : 0;
        }

        @Override
        public int revocarTodasDeUsuario(UUID usuarioId, MotivoRevocacion motivo) {
            if (usuarioId == null) {
                return 0;
            }
            int revocadas = 0;
            for (SesionRefresco sesion : porJti.values()) {
                if (usuarioId.equals(sesion.getUsuarioId()) && sesion.estaActiva(clock)
                        && sesion.revocar(motivo, clock)) {
                    revocadas++;
                }
            }
            return revocadas;
        }

        @Override
        public Page<SesionActivaView> listarActivasDeUsuario(UUID usuarioId, Clock relojConsulta,
                                                             Pageable pageable) {
            Clock efectivo = (relojConsulta != null) ? relojConsulta : this.clock;
            List<SesionActivaView> activas = new ArrayList<>();
            for (SesionRefresco sesion : porJti.values()) {
                if (usuarioId != null && usuarioId.equals(sesion.getUsuarioId())
                        && sesion.estaActiva(efectivo)) {
                    activas.add(sesion.aVista());
                }
            }
            return new PageImpl<>(activas, pageable, activas.size());
        }
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    @Provide
    Arbitrary<String> jtis() {
        // jti como UUID en texto (claim jti unico por token).
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }

    @Provide
    Arbitrary<List<String>> listaJtis() {
        // Secuencias pequenas de jti (UUID en texto); la @Size acota el tamano.
        return jtis().list().ofMaxSize(6);
    }

    @Provide
    Arbitrary<UUID> usuarios() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> tenants() {
        // tenant nulo (super_admin) o un UUID cualquiera.
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.create(UUID::randomUUID));
    }

    @Provide
    Arbitrary<MotivoRevocacion> motivos() {
        return Arbitraries.of(MotivoRevocacion.class);
    }
}

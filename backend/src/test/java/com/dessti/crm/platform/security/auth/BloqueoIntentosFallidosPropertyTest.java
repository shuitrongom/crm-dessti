package com.dessti.crm.platform.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 27: Bloqueo por
 * intentos fallidos</strong> (Req 2.1, 2.2).
 *
 * <p>Esta property valida la <em>logica de dominio</em> del bloqueo por fuerza
 * bruta directamente sobre la entidad de produccion {@link UsuarioAuth}, sin
 * base de datos ni contexto de Spring. El tiempo se modela con un {@link Clock}
 * <strong>mutable</strong> que la prueba avanza de forma determinista, tal como
 * el servicio inyecta el reloj en produccion.</p>
 *
 * <p><strong>Semantica LITERAL de la ventana deslizante (Req 2.1) que aqui se
 * verifica:</strong> un fallo solo suma al bloqueo si ocurre dentro de la
 * ventana de {@link UsuarioAuth#MINUTOS_VENTANA} minutos anclada en el
 * <em>primer</em> fallo de la racha en curso. Cinco fallos DENTRO de esa ventana
 * bloquean la cuenta {@link UsuarioAuth#MINUTOS_BLOQUEO} minutos; un fallo que
 * llegue estrictamente despues del limite de la ventana reinicia la racha (nuevo
 * conteo desde 1) y por tanto fallos suficientemente espaciados nunca acumulan
 * hasta el bloqueo. El borde exacto (justo 15 min desde el primer fallo) se trata
 * como <b>dentro</b> de la ventana (borde superior inclusivo).</p>
 *
 * <p>Metodos de dominio ejercitados (nombres y firmas reales verificados sobre
 * la fuente de {@link UsuarioAuth}):</p>
 * <ul>
 *   <li>{@link UsuarioAuth#registrarFallo(Clock)} — cuenta un fallo dentro de la
 *       ventana y bloquea al llegar a {@link UsuarioAuth#MAX_INTENTOS} fallos.</li>
 *   <li>{@link UsuarioAuth#registrarExito()} — reinicia el contador a 0, limpia
 *       el bloqueo y el ancla de la ventana.</li>
 *   <li>{@link UsuarioAuth#estaBloqueado(Clock)} — indica si el bloqueo sigue
 *       vigente en el instante del reloj.</li>
 *   <li>{@link UsuarioAuth#minutosRestantesBloqueo(Clock)} — minutos restantes
 *       (redondeo hacia arriba) hasta el desbloqueo.</li>
 * </ul>
 *
 * <p><strong>Invariantes comprobadas (espejo de la semantica real):</strong></p>
 * <ol>
 *   <li>Con menos de {@code MAX_INTENTOS} fallos la cuenta <em>nunca</em> queda
 *       bloqueada, independientemente del espaciado.</li>
 *   <li>Cinco fallos DENTRO de la ventana bloquean durante {@code MINUTOS_BLOQUEO}
 *       minutos; en el limite exacto del bloqueo ya no lo esta.</li>
 *   <li>Fallos suficientemente espaciados (el primero de la racha a mas de la
 *       ventana de un fallo posterior) reinician la racha: nunca acumulan al
 *       bloqueo.</li>
 *   <li>Un {@code registrarExito()} intercalado reinicia el contador, el bloqueo
 *       y el ancla de la ventana.</li>
 *   <li>Al expirar el bloqueo, la siguiente interaccion reinicia el contador a 0
 *       (Req 2.1) y el fallo posterior es el primero de una nueva racha.</li>
 * </ol>
 */
class BloqueoIntentosFallidosPropertyTest {

    private static final int MAX = UsuarioAuth.MAX_INTENTOS;            // 5
    private static final int BLOQUEO_MIN = UsuarioAuth.MINUTOS_BLOQUEO; // 15
    private static final int VENTANA_MIN = UsuarioAuth.MINUTOS_VENTANA; // 15

    /** Instante base arbitrario y fijo (UTC) para anclar el reloj mutable. */
    private static final Instant BASE = Instant.parse("2025-01-01T00:00:00Z");

    // ----------------------------------------------------------------------
    // Reloj mutable determinista: la prueba lo avanza manualmente.
    // ----------------------------------------------------------------------

    private static final class RelojMutable extends Clock {
        private Instant ahora;

        RelojMutable(Instant inicial) {
            this.ahora = inicial;
        }

        void avanzar(Duration d) {
            this.ahora = this.ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    /**
     * Crea una instancia de {@link UsuarioAuth} recien registrada
     * (contador 0, sin bloqueo). Como el constructor es {@code protected} y no
     * hay setters, se usa reflexion solo para instanciarla; el estado del
     * bloqueo se manipula exclusivamente a traves de los metodos de dominio
     * publicos bajo prueba.
     */
    private static UsuarioAuth nuevoUsuario() {
        try {
            java.lang.reflect.Constructor<UsuarioAuth> ctor =
                    UsuarioAuth.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No se pudo instanciar UsuarioAuth", ex);
        }
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Huecos DENTRO de la ventana: desde 1 s hasta {@code VENTANA_MIN} minutos.
     * Se usan para racha rapida en la que 5 fallos caen dentro de la ventana
     * anclada en el primero. Cada hueco individual es <= ventana, pero el
     * generador tambien controla que el acumulado del primero al ultimo no la
     * exceda (ver uso en cada @Property).
     */
    @Provide
    Arbitrary<List<Duration>> huecosPequenos() {
        Arbitrary<Duration> hueco = Arbitraries.integers()
                .between(1, 60) // 1..60 s: 4 huecos suman como mucho 4 min < 15
                .map(Duration::ofSeconds);
        return hueco.list().ofMinSize(0).ofMaxSize(MAX);
    }

    /**
     * Huecos amplios (0 s a 30 min) para explorar libremente dentro/fuera de la
     * ventana en las invariantes estructurales.
     */
    @Provide
    Arbitrary<List<Duration>> huecosEntreIntentos() {
        Arbitrary<Duration> hueco = Arbitraries.integers()
                .between(0, 30 * 60)
                .map(Duration::ofSeconds);
        return hueco.list().ofMinSize(0).ofMaxSize(10);
    }

    // ----------------------------------------------------------------------
    // Property 27 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void menosDeCincoFallosNuncaBloquea(
            @ForAll @IntRange(min = 0, max = MAX - 1) int numFallos,
            @ForAll("huecosEntreIntentos") List<Duration> huecos) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        for (int i = 0; i < numFallos; i++) {
            if (i < huecos.size()) {
                reloj.avanzar(huecos.get(i));
            }
            usuario.registrarFallo(reloj);

            // En ningun momento con < MAX fallos la cuenta debe estar bloqueada,
            // sea cual sea el espaciado (nunca se alcanza MAX en la ventana).
            assertThat(usuario.estaBloqueado(reloj))
                    .as("con a lo sumo %d fallos (< %d) la cuenta no debe estar bloqueada", i + 1, MAX)
                    .isFalse();
        }

        assertThat(usuario.estaBloqueado(reloj)).isFalse();
        assertThat(usuario.minutosRestantesBloqueo(reloj)).isZero();
        // El contador nunca supera MAX-1 con < MAX fallos.
        assertThat(usuario.getIntentosFallidos()).isLessThanOrEqualTo(MAX - 1);
    }

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void cincoFallosDentroDeLaVentanaBloqueaQuinceMinutos(
            @ForAll("huecosPequenos") List<Duration> huecos) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        Instant primerFallo = null;

        // MAX fallos con huecos pequenos: el acumulado del primero al quinto es,
        // como mucho, 4 * 60 s = 4 min < 15 min, luego los 5 caen en la ventana.
        for (int i = 0; i < MAX; i++) {
            if (i > 0 && (i - 1) < huecos.size()) {
                reloj.avanzar(huecos.get(i - 1));
            }
            if (i == 0) {
                primerFallo = reloj.instant();
            }
            // Todos los fallos deben caer dentro de la ventana anclada en el 1ro.
            assertThat(reloj.instant())
                    .as("el fallo %d debe caer dentro de la ventana de %d min", i + 1, VENTANA_MIN)
                    .isBeforeOrEqualTo(primerFallo.plus(Duration.ofMinutes(VENTANA_MIN)));

            usuario.registrarFallo(reloj);

            if (i < MAX - 1) {
                assertThat(usuario.estaBloqueado(reloj))
                        .as("con %d fallos aun no debe bloquear", i + 1)
                        .isFalse();
            }
        }

        Instant instanteBloqueo = reloj.instant();
        assertThat(usuario.estaBloqueado(reloj))
                .as("al %d-esimo fallo dentro de la ventana la cuenta debe quedar bloqueada", MAX)
                .isTrue();
        assertThat(usuario.getBloqueadoHasta())
                .as("bloqueado_hasta debe ser el instante del 5to fallo + %d min", BLOQUEO_MIN)
                .isEqualTo(instanteBloqueo.plus(Duration.ofMinutes(BLOQUEO_MIN)));
        assertThat(usuario.minutosRestantesBloqueo(reloj))
                .as("minutos restantes al momento del bloqueo")
                .isEqualTo((long) BLOQUEO_MIN);

        // Sigue bloqueado justo antes de cumplirse los 15 min.
        RelojMutable casiFin = new RelojMutable(
                instanteBloqueo.plus(Duration.ofMinutes(BLOQUEO_MIN).minusSeconds(1)));
        assertThat(usuario.estaBloqueado(casiFin))
                .as("un segundo antes del limite sigue bloqueado")
                .isTrue();

        // En el limite exacto (15 min) ya NO esta bloqueado (bloqueado_hasta no es posterior).
        RelojMutable justoFin = new RelojMutable(instanteBloqueo.plus(Duration.ofMinutes(BLOQUEO_MIN)));
        assertThat(usuario.estaBloqueado(justoFin))
                .as("en el instante de expiracion ya no esta bloqueado")
                .isFalse();
        assertThat(usuario.minutosRestantesBloqueo(justoFin)).isZero();
    }

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void quintoFalloJustoEnElBordeDeLaVentanaBloquea(
            @ForAll @IntRange(min = 0, max = 3) int fallosIntermedios) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        // Primer fallo ancla la ventana en BASE.
        usuario.registrarFallo(reloj);
        Instant ancla = BASE;

        // Fallos intermedios repartidos dentro de la ventana.
        for (int i = 0; i < fallosIntermedios; i++) {
            reloj.avanzar(Duration.ofMinutes(1));
            usuario.registrarFallo(reloj);
        }

        // Colocar los fallos restantes de modo que el ultimo caiga EXACTAMENTE en
        // el borde superior de la ventana (ancla + 15 min), que es "dentro".
        int registrados = 1 + fallosIntermedios;
        int restantes = MAX - registrados;
        for (int k = 0; k < restantes; k++) {
            if (k < restantes - 1) {
                // Fallos previos al ultimo: los dejamos justo antes del borde.
                RelojMutable r = new RelojMutable(ancla.plus(Duration.ofMinutes(VENTANA_MIN)).minusSeconds(1));
                reloj = r;
            } else {
                // Ultimo fallo: exactamente en el borde de la ventana.
                reloj = new RelojMutable(ancla.plus(Duration.ofMinutes(VENTANA_MIN)));
            }
            usuario.registrarFallo(reloj);
        }

        assertThat(usuario.getIntentosFallidos())
                .as("el 5to fallo en el borde de la ventana aun cuenta (borde inclusivo)")
                .isEqualTo(MAX);
        assertThat(usuario.estaBloqueado(reloj))
                .as("5 fallos con el ultimo en el borde exacto de la ventana bloquean")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void fallosEspaciadosMasAllaDeLaVentanaNuncaBloquean(
            @ForAll @IntRange(min = 5, max = 20) int numFallos,
            @ForAll @IntRange(min = 1, max = 30) int minutosExtraTrasVentana) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        // Cada fallo llega ESTRICTAMENTE despues del borde de la ventana anclada
        // en el fallo anterior: cada fallo reinicia la racha (contador = 1).
        Duration paso = Duration.ofMinutes(VENTANA_MIN).plusMinutes(minutosExtraTrasVentana);

        for (int i = 0; i < numFallos; i++) {
            if (i > 0) {
                reloj.avanzar(paso);
            }
            usuario.registrarFallo(reloj);

            assertThat(usuario.getIntentosFallidos())
                    .as("cada fallo espaciado > ventana reinicia la racha a 1")
                    .isEqualTo(1);
            assertThat(usuario.estaBloqueado(reloj))
                    .as("fallos espaciados fuera de la ventana nunca bloquean")
                    .isFalse();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void exitoIntercaladoReiniciaContadorBloqueoYVentana(
            @ForAll @IntRange(min = 0, max = MAX - 1) int fallosPrevios) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        // Racha rapida (todos dentro de la ventana) de menos de MAX fallos.
        for (int i = 0; i < fallosPrevios; i++) {
            reloj.avanzar(Duration.ofSeconds(30));
            usuario.registrarFallo(reloj);
        }

        usuario.registrarExito();
        assertThat(usuario.getIntentosFallidos())
                .as("registrarExito debe reiniciar el contador a 0")
                .isZero();
        assertThat(usuario.getBloqueadoHasta())
                .as("registrarExito debe limpiar bloqueado_hasta")
                .isNull();
        assertThat(usuario.getPrimerIntentoFallido())
                .as("registrarExito debe limpiar el ancla de la ventana")
                .isNull();
        assertThat(usuario.estaBloqueado(reloj)).isFalse();

        // Tras el exito, se requiere de nuevo una racha completa de MAX fallos
        // dentro de la ventana para volver a bloquear.
        for (int i = 0; i < MAX - 1; i++) {
            reloj.avanzar(Duration.ofSeconds(30));
            usuario.registrarFallo(reloj);
            assertThat(usuario.estaBloqueado(reloj))
                    .as("tras un exito, el fallo %d no debe bloquear", i + 1)
                    .isFalse();
        }
        reloj.avanzar(Duration.ofSeconds(30));
        usuario.registrarFallo(reloj);
        assertThat(usuario.estaBloqueado(reloj))
                .as("tras un exito, se necesitan %d fallos nuevos en la ventana para bloquear", MAX)
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void alExpirarElBloqueoElContadorSeReiniciaEnLaSiguienteInteraccion(
            @ForAll @IntRange(min = 0, max = 60) int minutosExtraTrasExpirar) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        // Provocar el bloqueo con MAX fallos dentro de la ventana.
        for (int i = 0; i < MAX; i++) {
            usuario.registrarFallo(reloj);
        }
        assertThat(usuario.estaBloqueado(reloj)).isTrue();
        Instant hasta = usuario.getBloqueadoHasta();

        // Avanzar el reloj mas alla del fin del bloqueo.
        reloj.avanzar(Duration.ofMinutes(BLOQUEO_MIN).plusMinutes(minutosExtraTrasExpirar));
        assertThat(usuario.estaBloqueado(reloj))
                .as("una vez pasado bloqueado_hasta ya no esta bloqueado")
                .isFalse();
        assertThat(reloj.instant())
                .as("el reloj debe estar en o despues del fin del bloqueo")
                .isAfterOrEqualTo(hasta);

        // La siguiente interaccion (un fallo) reinicia el contador (Req 2.1):
        // cuenta como el primer fallo de una nueva racha y NO vuelve a bloquear.
        usuario.registrarFallo(reloj);
        assertThat(usuario.getIntentosFallidos())
                .as("al expirar el bloqueo, el contador se reinicia y este fallo es el primero")
                .isEqualTo(1);
        assertThat(usuario.getBloqueadoHasta())
                .as("el bloqueo previo debe haberse limpiado")
                .isNull();
        assertThat(usuario.getPrimerIntentoFallido())
                .as("la ventana se reancla en este primer fallo de la nueva racha")
                .isEqualTo(reloj.instant());
        assertThat(usuario.estaBloqueado(reloj))
                .as("un unico fallo tras expirar el bloqueo no vuelve a bloquear")
                .isFalse();

        // Y se requieren MAX-1 fallos adicionales dentro de la ventana para
        // volver a bloquear.
        for (int i = 0; i < MAX - 2; i++) {
            usuario.registrarFallo(reloj);
            assertThat(usuario.estaBloqueado(reloj)).isFalse();
        }
        usuario.registrarFallo(reloj);
        assertThat(usuario.estaBloqueado(reloj))
                .as("nueva racha completa de %d fallos vuelve a bloquear", MAX)
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 27: Para cualquier secuencia de intentos de inicio de sesión sobre una misma cuenta, tras 5 fallos consecutivos dentro de una ventana de 15 minutos la cuenta queda bloqueada durante 15 minutos y el contador de fallos se reinicia a 0 al expirar el bloqueo.
    @Property(tries = 1000)
    void invarianteGlobalBloqueadoSiiHayInstanteFuturoDeDesbloqueo(
            @ForAll @IntRange(min = 0, max = 10) int numFallos,
            @ForAll("huecosEntreIntentos") List<Duration> huecos,
            @ForAll boolean exitoAlFinal) {

        RelojMutable reloj = new RelojMutable(BASE);
        UsuarioAuth usuario = nuevoUsuario();

        for (int i = 0; i < numFallos; i++) {
            if (i < huecos.size()) {
                reloj.avanzar(huecos.get(i));
            }
            usuario.registrarFallo(reloj);
        }
        if (exitoAlFinal) {
            usuario.registrarExito();
        }

        // Invariante estructural: estaBloqueado <=> bloqueado_hasta es posterior a ahora.
        Instant ahora = reloj.instant();
        boolean esperado = usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isAfter(ahora);
        assertThat(usuario.estaBloqueado(reloj))
                .as("estaBloqueado debe coincidir con bloqueado_hasta posterior a ahora")
                .isEqualTo(esperado);

        // Los minutos restantes son > 0 si y solo si esta bloqueado.
        assertThat(usuario.minutosRestantesBloqueo(reloj) > 0)
                .as("minutos restantes > 0 <=> esta bloqueado")
                .isEqualTo(usuario.estaBloqueado(reloj));

        // El contador nunca es negativo. Nota: puede superar MAX si, ya
        // bloqueada la cuenta, siguen llegando fallos DENTRO del periodo de
        // bloqueo (el contador no se reinicia hasta que el bloqueo expira,
        // Req 2.1); esos fallos no cambian el estado de bloqueo.
        assertThat(usuario.getIntentosFallidos()).isGreaterThanOrEqualTo(0);
        // Si esta bloqueada, se alcanzo al menos el umbral de bloqueo.
        if (usuario.estaBloqueado(reloj)) {
            assertThat(usuario.getIntentosFallidos()).isGreaterThanOrEqualTo(MAX);
        }

        // Tras un exito, el estado siempre queda limpio (incluida la ventana).
        if (exitoAlFinal) {
            assertThat(usuario.getIntentosFallidos()).isZero();
            assertThat(usuario.getBloqueadoHasta()).isNull();
            assertThat(usuario.getPrimerIntentoFallido()).isNull();
        }
    }

    /**
     * Verificacion estatica de que la entidad expone los campos de estado del
     * bloqueo esperados (deteccion temprana de renombrados en produccion),
     * incluida el ancla de la ventana deslizante (V7).
     */
    @Property(tries = 1)
    void laEntidadExponeLosCamposDeEstadoDelBloqueo() {
        List<String> nombres = new ArrayList<>();
        for (Field f : UsuarioAuth.class.getDeclaredFields()) {
            nombres.add(f.getName());
        }
        assertThat(nombres)
                .as("UsuarioAuth debe conservar los campos de estado del bloqueo")
                .contains("intentosFallidos", "bloqueadoHasta", "primerIntentoFallido", "version");
    }
}

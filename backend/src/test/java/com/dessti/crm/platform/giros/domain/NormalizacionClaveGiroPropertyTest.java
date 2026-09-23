package com.dessti.crm.platform.giros.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Locale;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Feature:
 * plataforma-multigiro, Property 1: Normalización idempotente de la clave de
 * Giro</strong> (Validates: Requirements 1.2).
 *
 * <p>Ejercita la pieza de produccion pura
 * {@link Giro#normalizarClave(String)} sin base de datos ni contexto de Spring.
 * La propiedad afirma dos cosas universales sobre toda cadena de entrada cuya
 * normalizacion produce una clave valida (no vacia tras normalizar y de longitud
 * &lt;= 60):</p>
 * <ol>
 *   <li><strong>Invariante de forma canonica:</strong> la clave normalizada esta
 *       en minusculas, sin espacios envolventes, en formato kebab (solo
 *       {@code [a-z0-9]} separados por un unico guion, sin guiones en los
 *       extremos ni secuencias de separadores).</li>
 *   <li><strong>Idempotencia:</strong> volver a normalizar el resultado produce
 *       exactamente la misma clave, es decir
 *       {@code normalizarClave(normalizarClave(x)) == normalizarClave(x)}.</li>
 * </ol>
 *
 * <p>Para las entradas que {@code normalizarClave} rechaza (vacias tras
 * normalizar, o de mas de {@value #LONGITUD_MAXIMA_CLAVE} caracteres tras
 * normalizar) se verifica que la {@link ReglaNegocioException} ocurre
 * <em>exactamente</em> cuando corresponde segun un modelo de referencia
 * independiente. La propiedad de idempotencia se comprueba unicamente sobre las
 * entradas que producen clave valida.</p>
 */
class NormalizacionClaveGiroPropertyTest {

    /** Longitud maxima de clave (espejo de {@code Giro.LONGITUD_MAXIMA_CLAVE}). */
    private static final int LONGITUD_MAXIMA_CLAVE = 60;

    /** Alfabeto kebab valido de la clave canonica: minusculas, digitos y guion. */
    private static final Pattern CLAVE_CANONICA = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** Modelo de referencia de separadores (secuencia de caracteres no {@code [a-z0-9]}). */
    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

    /** Modelo de referencia de guiones sobrantes en los extremos. */
    private static final Pattern GUIONES_EXTREMOS = Pattern.compile("^-+|-+$");

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Piezas de cadena variadas para componer entradas realistas: espacios,
     * mayusculas, guiones repetidos, guiones bajos, signos y letras acentuadas /
     * unicode. Al combinarlas se ejercitan colapso de separadores, recorte de
     * extremos y paso a minusculas.
     */
    @Provide
    Arbitrary<String> fragmentos() {
        Arbitrary<String> alfanumerico = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(0)
                .ofMaxLength(8);
        Arbitrary<String> separadores = Arbitraries.of(
                " ", "  ", "\t", "-", "--", "___", "_", ".", "/", "@", "#", "  -  ", "···");
        Arbitrary<String> acentosYUnicode = Arbitraries.of(
                "áé", "ñ", "ü", "Ω", "日本", "café", "SEÑAL", "Ünïcode", "");
        return Arbitraries.oneOf(alfanumerico, separadores, acentosYUnicode);
    }

    /**
     * Cadenas arbitrarias construidas concatenando de 0 a 8 fragmentos variados.
     * Cubre tanto entradas que normalizan a una clave valida como entradas que se
     * rechazan (vacias tras normalizar o demasiado largas).
     */
    @Provide
    Arbitrary<String> cadenasArbitrarias() {
        return fragmentos().list().ofMinSize(0).ofMaxSize(8)
                .map(partes -> String.join("", partes));
    }

    // ----------------------------------------------------------------------
    // Modelo de referencia (independiente de la implementacion de produccion)
    // ----------------------------------------------------------------------

    /**
     * Reproduce el resultado esperado de la normalizacion, o {@code null} si la
     * entrada debe rechazarse (vacia/blanca de origen, vacia tras normalizar, o
     * de longitud superior al maximo).
     */
    private static String claveEsperadaONull(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String minusculas = valor.strip().toLowerCase(Locale.ROOT);
        String kebab = SEPARADORES.matcher(minusculas).replaceAll("-");
        String normalizado = GUIONES_EXTREMOS.matcher(kebab).replaceAll("");
        if (normalizado.isEmpty() || normalizado.length() > LONGITUD_MAXIMA_CLAVE) {
            return null;
        }
        return normalizado;
    }

    // ----------------------------------------------------------------------
    // Feature: plataforma-multigiro, Property 1: Normalización idempotente de la clave de Giro
    // ----------------------------------------------------------------------

    /**
     * Propiedad principal (Validates: Requirements 1.2). Para toda cadena de
     * entrada: si produce una clave valida, la salida cumple el invariante
     * canonico y la normalizacion es idempotente; si no, se rechaza con
     * {@link ReglaNegocioException} exactamente cuando el modelo de referencia lo
     * indica.
     */
    // Feature: plataforma-multigiro, Property 1: Normalización idempotente de la clave de Giro
    @Property(tries = 1000)
    void normalizarClaveEsIdempotenteYRespetaElInvarianteCanonico(
            @ForAll("cadenasArbitrarias") String entrada) {
        String esperada = claveEsperadaONull(entrada);

        if (esperada == null) {
            // El modelo de referencia predice rechazo: debe lanzar ReglaNegocioException.
            assertThatThrownBy(() -> Giro.normalizarClave(entrada))
                    .as("una entrada que no produce clave valida debe rechazarse: [%s]", entrada)
                    .isInstanceOf(ReglaNegocioException.class);
            return;
        }

        String clave = Giro.normalizarClave(entrada);

        // Coincide con el modelo de referencia.
        assertThat(clave)
                .as("la clave normalizada debe coincidir con el modelo de referencia")
                .isEqualTo(esperada);

        // Invariante de forma canonica.
        afirmarInvarianteCanonico(clave);

        // Idempotencia: normalizar el resultado produce exactamente la misma clave.
        assertThat(Giro.normalizarClave(clave))
                .as("normalizarClave(normalizarClave(x)) debe ser igual a normalizarClave(x)")
                .isEqualTo(clave);
    }

    /**
     * Propiedad de punto fijo enfocada: partiendo de una clave ya canonica
     * generada directamente, {@code normalizarClave} la devuelve intacta. Esto
     * asegura que ninguna clave valida se ve alterada por una segunda pasada.
     */
    // Feature: plataforma-multigiro, Property 1: Normalización idempotente de la clave de Giro
    @Property(tries = 1000)
    void unaClaveYaCanonicaEsUnPuntoFijo(@ForAll("clavesCanonicas") String canonica) {
        assertThat(Giro.normalizarClave(canonica))
                .as("una clave ya canonica debe permanecer inalterada")
                .isEqualTo(canonica);
    }

    /**
     * Genera claves ya en forma canonica (minusculas/kebab, 1..60 caracteres):
     * palabras alfanumericas unidas por un unico guion, sin guiones en extremos.
     */
    @Provide
    Arbitrary<String> clavesCanonicas() {
        Arbitrary<String> palabra = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(10);
        return palabra.list().ofMinSize(1).ofMaxSize(6)
                .map(palabras -> String.join("-", palabras))
                .filter(clave -> clave.length() <= LONGITUD_MAXIMA_CLAVE);
    }

    /**
     * Casos frontera dirigidos (data-driven) alrededor de la longitud maxima y de
     * las entradas que se rechazan, para complementar la exploracion aleatoria.
     */
    // Feature: plataforma-multigiro, Property 1: Normalización idempotente de la clave de Giro
    @Property(tries = 200)
    void fronterasDeLongitudYRechazoSeComportanSegunElModelo(
            @ForAll("valoresFrontera") String entrada) {
        String esperada = claveEsperadaONull(entrada);
        Throwable fallo = catchThrowable(() -> Giro.normalizarClave(entrada));

        if (esperada == null) {
            assertThat(fallo)
                    .as("frontera rechazada: [%s]", entrada)
                    .isInstanceOf(ReglaNegocioException.class);
        } else {
            assertThat(fallo).as("frontera aceptada no debe fallar: [%s]", entrada).isNull();
            String clave = Giro.normalizarClave(entrada);
            assertThat(clave).isEqualTo(esperada);
            afirmarInvarianteCanonico(clave);
            assertThat(Giro.normalizarClave(clave))
                    .as("idempotencia en frontera")
                    .isEqualTo(clave);
        }
    }

    /**
     * Conjunto dirigido: cadena de exactamente 60 caracteres (aceptada), de 61
     * (rechazada por longitud), entradas nulas/blancas y solo-separadores
     * (rechazadas por quedar vacias), y claves con guiones/extremos a colapsar.
     */
    @Provide
    Arbitrary<String> valoresFrontera() {
        String sesenta = "a".repeat(LONGITUD_MAXIMA_CLAVE);            // -> valida (60)
        String sesentaYUno = "a".repeat(LONGITUD_MAXIMA_CLAVE + 1);    // -> rechazada (61)
        String kebabDeSesenta = "ab-".repeat(20).substring(0, 59);      // <= 60 tras normalizar
        return Arbitraries.of(
                null,
                "",
                "   ",
                "---",
                "__ __",
                "···",
                sesenta,
                sesentaYUno,
                "  Anuncios   Luminosos  ",   // -> anuncios-luminosos
                "ANUNCIOS_LUMINOSOS",           // -> anuncios-luminosos
                "--anuncios--luminosos--",     // -> anuncios-luminosos
                "Señal 24/7",                   // -> se-al-24-7
                kebabDeSesenta);
    }

    // ----------------------------------------------------------------------
    // Auxiliares
    // ----------------------------------------------------------------------

    /**
     * Afirma que la clave cumple la forma canonica: no vacia, longitud &lt;= 60,
     * igual a su version en minusculas, sin espacios envolventes, solo
     * {@code [a-z0-9-]}, sin guiones en los extremos y sin secuencias de guiones.
     */
    private static void afirmarInvarianteCanonico(String clave) {
        assertThat(clave).as("la clave no debe ser vacia").isNotEmpty();
        assertThat(clave.length())
                .as("la clave no debe exceder %d caracteres", LONGITUD_MAXIMA_CLAVE)
                .isLessThanOrEqualTo(LONGITUD_MAXIMA_CLAVE);
        assertThat(clave)
                .as("la clave debe estar en minusculas")
                .isEqualTo(clave.toLowerCase(Locale.ROOT));
        assertThat(clave)
                .as("la clave no debe tener espacios envolventes")
                .isEqualTo(clave.strip());
        assertThat(clave)
                .as("la clave no debe empezar ni terminar con guion")
                .doesNotStartWith("-")
                .doesNotEndWith("-");
        assertThat(clave)
                .as("la clave no debe contener secuencias de separadores")
                .doesNotContain("--");
        assertThat(CLAVE_CANONICA.matcher(clave).matches())
                .as("la clave [%s] debe respetar el alfabeto kebab [a-z0-9-]", clave)
                .isTrue();
    }
}

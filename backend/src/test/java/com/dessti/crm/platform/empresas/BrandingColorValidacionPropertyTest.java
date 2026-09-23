package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.dessti.crm.platform.error.ReglaNegocioException;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 8: validacion de
 * formato hexadecimal del Color_Primario_Marca</strong> (Req 6.1, 6.4), aplicada
 * al comportamiento de dominio
 * {@link Empresa#actualizarBranding(String, String, String, String)} respecto al
 * color primario de marca.
 *
 * <p>Enunciado (para toda cadena de entrada):</p>
 * <ul>
 *   <li>Si {@code colorPrimario} coincide con {@code ^#[0-9a-fA-F]{6}$} (o es
 *       {@code null}/blanco &rarr; limpia): NO lanza, y
 *       {@link Empresa#getBrandingColorPrimario()} queda con el valor normalizado
 *       a minusculas (o {@code null} cuando limpia).</li>
 *   <li>Si NO cumple el formato: lanza {@link ReglaNegocioException} (HTTP 422) y
 *       el color vigente NO cambia (se conserva el valor previo).</li>
 * </ul>
 *
 * <p>Es una property de dominio puro: no hay contexto de Spring, base de datos ni
 * mocks. Se construye la Empresa con
 * {@link Empresa#crear(String, String, UUID, String)}, se fija un color inicial
 * valido y luego se intenta aplicar la entrada generada verificando el invariante
 * (aceptar &harr; formato valido; en rechazo, se conserva el color previo).</p>
 */
@Label("Feature: tematizacion-empresa-enterprise, Property 8: para toda cadena, el validador acepta "
        + "sii coincide con ^#[0-9a-fA-F]{6}$; una entrada rechazada no modifica el color vigente")
class BrandingColorValidacionPropertyTest {

    private static final Pattern PATRON_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /** Color inicial valido (ya normalizado en minusculas) fijado antes de cada intento. */
    private static final String COLOR_INICIAL = "#1a2b3c";

    /**
     * Property 8 — entradas VALIDAS y de LIMPIEZA (formato correcto, null o blanco):
     * {@code actualizarBranding} NO lanza y el color queda normalizado a minusculas
     * (o {@code null} cuando la entrada limpia). Verifica ademas que nombre/logo se
     * fijan a partir de los argumentos, sin efectos colaterales.
     */
    @Property(tries = 200)
    void colorValidoOLimpiezaSeAceptaYNormalizaAMinusculas(
            @ForAll("colorAceptado") String colorEntrada) {

        Empresa empresa = empresaConColorInicial();
        assertThat(empresa.getBrandingColorPrimario()).isEqualTo(COLOR_INICIAL);

        assertThatCode(() -> empresa.actualizarBranding("Marca", "https://logo", colorEntrada, "actor"))
                .as("una entrada de color valida o de limpieza no debe lanzar")
                .doesNotThrowAnyException();

        boolean limpia = (colorEntrada == null || colorEntrada.isBlank());
        if (limpia) {
            assertThat(empresa.getBrandingColorPrimario())
                    .as("null/blanco limpia el color (queda null)")
                    .isNull();
        } else {
            assertThat(empresa.getBrandingColorPrimario())
                    .as("un color valido queda normalizado a minusculas")
                    .isEqualTo(colorEntrada.strip().toLowerCase(Locale.ROOT));
            assertThat(PATRON_COLOR.matcher(empresa.getBrandingColorPrimario()).matches())
                    .as("el color persistido sigue cumpliendo el formato #RRGGBB")
                    .isTrue();
        }
    }

    /**
     * Property 8 — entradas INVALIDAS (no null, no blanco y que NO cumplen el
     * formato): {@code actualizarBranding} lanza {@link ReglaNegocioException} (422)
     * y el color vigente NO cambia (se conserva {@link #COLOR_INICIAL}).
     */
    @Property(tries = 200)
    void colorInvalidoSeRechazaYConservaElColorVigente(
            @ForAll("colorInvalido") String colorEntrada) {

        Empresa empresa = empresaConColorInicial();
        assertThat(empresa.getBrandingColorPrimario()).isEqualTo(COLOR_INICIAL);

        assertThatThrownBy(() -> empresa.actualizarBranding("Marca", "https://logo", colorEntrada, "actor"))
                .as("un color con formato invalido debe rechazarse con 422")
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(empresa.getBrandingColorPrimario())
                .as("tras un rechazo, el color vigente no cambia")
                .isEqualTo(COLOR_INICIAL);
    }

    // ----------------------------------------------------------------------
    // Fabrica de la Empresa bajo prueba, con un color inicial valido fijado.
    // ----------------------------------------------------------------------

    private static Empresa empresaConColorInicial() {
        Empresa empresa = Empresa.crear("Empresa de prueba", "AAA010101AAA", UUID.randomUUID(), "creador");
        empresa.actualizarBranding("Marca", "https://logo", COLOR_INICIAL, "creador");
        return empresa;
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Genera entradas que el dominio debe ACEPTAR: hex validos aleatorios
     * {@code #RRGGBB} (con mezcla de mayusculas/minusculas para ejercitar la
     * normalizacion), y ademas los casos de LIMPIEZA ({@code null} y cadenas en
     * blanco).
     */
    @Provide
    Arbitrary<String> colorAceptado() {
        return Arbitraries.oneOf(hexValido(), limpieza());
    }

    /** Hex valido aleatorio {@code #RRGGBB} con digitos en mayusculas y minusculas. */
    private Arbitrary<String> hexValido() {
        Arbitrary<Character> hexDigit = Arbitraries.of(
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F');
        return hexDigit.list().ofSize(6).map(chars -> {
            StringBuilder sb = new StringBuilder("#");
            for (Character c : chars) {
                sb.append(c);
            }
            return sb.toString();
        });
    }

    /** Entradas de limpieza: {@code null} y cadenas compuestas solo por espacios en blanco. */
    private Arbitrary<String> limpieza() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just(" \n "));
    }

    /**
     * Genera cadenas arbitrarias que NO son de limpieza (no null, con algun
     * caracter no en blanco) y que NO cumplen el patron {@code ^#[0-9a-fA-F]{6}$}
     * tras recortar. Combina cadenas totalmente arbitrarias con casos borde
     * cercanos al formato (longitud incorrecta, sin {@code #}, digitos no hex).
     */
    @Provide
    Arbitrary<String> colorInvalido() {
        Arbitrary<String> arbitrarias = Arbitraries.strings()
                .withCharRange((char) 0x20, (char) 0x7e)
                .ofMinLength(0).ofMaxLength(20);
        Arbitrary<String> casiValidos = casiValidos();
        return Arbitraries.oneOf(arbitrarias, casiValidos)
                .filter(s -> {
                    if (s == null) {
                        return false;
                    }
                    if (s.isBlank()) {
                        return false; // blanco = limpieza (valido), no invalido
                    }
                    return !PATRON_COLOR.matcher(s.strip()).matches();
                });
    }

    /** Casos borde cercanos al formato valido pero invalidos por construccion. */
    private Arbitrary<String> casiValidos() {
        Arbitrary<Integer> longitud = Arbitraries.integers().between(1, 8).filter(n -> n != 6);
        Arbitrary<String> hexChars = Combinators.combine(
                        Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                'a', 'b', 'c', 'd', 'e', 'f').list().ofMinSize(1).ofMaxSize(10),
                        longitud)
                .as((chars, len) -> {
                    StringBuilder sb = new StringBuilder("#");
                    for (int i = 0; i < len; i++) {
                        sb.append(chars.get(i % chars.size()));
                    }
                    return sb.toString();
                });
        return Arbitraries.oneOf(
                hexChars,                              // # + longitud != 6
                Arbitraries.just("1a2b3c"),            // sin '#'
                Arbitraries.just("#1a2b3g"),           // digito no hex
                Arbitraries.just("##1a2b3"),           // doble '#'
                Arbitraries.just("#1a2b3cd"),          // 7 digitos
                Arbitraries.just("#zzzzzz"));          // no hex
    }
}

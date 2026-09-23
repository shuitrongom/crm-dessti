package com.dessti.crm.platform.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <b>Property 23: Validacion de
 * datos de entrada</b> del diseno de {@code crm-anuncios-luminosos} (tarea 5.3).
 *
 * <p>Como aun no existen los DTO de negocio (clientes, proveedores, empleados,
 * activos: tareas 15+), se valida la INVARIANTE transversal de forma
 * property-based sobre un DTO de PRUEBA anotado con Bean Validation
 * (jakarta.validation) representativo de los patrones del diseno: campos
 * obligatorios, longitudes 1..200, formato de identificador fiscal (tipo RFC)
 * y rangos numericos (0.01..999,999,999.99).</p>
 *
 * <p>La property verifica el MECANISMO transversal: toda entrada que incumple
 * formato/tipo/obligatoriedad produce violaciones (equivalente a rechazo 400 y
 * no persistencia) y toda entrada valida no produce ninguna violacion. La
 * <b>traduccion a HTTP 400</b> la realiza el {@code @RestControllerAdvice} de la
 * tarea 5.1; los DTO reales de cada modulo reutilizaran estas mismas
 * anotaciones de Bean Validation en sus propias tareas.</p>
 *
 * <p>Todo se ejecuta EN MEMORIA con un {@link Validator} de
 * {@code jakarta.validation}, sin Spring, sin BD y sin Docker.</p>
 *
 * <p>Validates: Requirements 5.1, 5.2, 8.1, 8.2, 29.1, 29.2, 40.1, 40.2, 44.1, 44.2.</p>
 */
class ValidacionEntradaPropertyTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private static final BigDecimal MIN_IMPORTE = new BigDecimal("0.01");
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("999999999.99");

    /**
     * DTO de prueba representativo de los patrones de validacion del diseno.
     * Reune las anotaciones que los DTO reales de negocio aplicaran:
     * obligatoriedad, longitud 1..200, formato de identificador fiscal y rango
     * monetario decimal.
     */
    record EntradaPrueba(
            @NotNull @Size(min = 1, max = 200) String nombre,
            @NotNull @Pattern(regexp = "^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$") String rfc,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "999999999.99") BigDecimal importe) {
    }

    // ---------------------------------------------------------------------
    // Entradas validas -> sin violaciones (se procesan)
    // ---------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 23: Validacion de datos de entrada
    // Para cualquier peticion de registro/actualizacion, una entrada valida
    // (cumple formato, tipo y obligatoriedad) no produce violaciones y se procesa.
    @Property(tries = 1000)
    void entradaValidaNoProduceViolaciones(@ForAll("entradasValidas") EntradaPrueba entrada) {
        Set<ConstraintViolation<EntradaPrueba>> violaciones = VALIDATOR.validate(entrada);

        assertThat(violaciones)
                .as("una entrada valida no debe producir violaciones: %s", entrada)
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // Entradas invalidas -> al menos una violacion (rechazo 400, no persistencia)
    // ---------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 23: Validacion de datos de entrada
    // Para cualquier peticion de registro/actualizacion, una entrada que incumple
    // reglas de formato, tipo u obligatoriedad se rechaza (=> 400) y no se persiste;
    // esto se manifiesta como al menos una violacion de Bean Validation.
    @Property(tries = 1000)
    void entradaInvalidaProduceViolaciones(@ForAll("entradasInvalidas") EntradaPrueba entrada) {
        Set<ConstraintViolation<EntradaPrueba>> violaciones = VALIDATOR.validate(entrada);

        assertThat(violaciones)
                .as("una entrada invalida debe producir al menos una violacion: %s", entrada)
                .isNotEmpty();
    }

    // Feature: crm-anuncios-luminosos, Property 23: Validacion de datos de entrada
    // Un campo obligatorio nulo (nombre) siempre se rechaza.
    @Property(tries = 500)
    void obligatorioNuloSeRechaza(@ForAll("rfcsValidos") String rfc,
                                  @ForAll("importesValidos") BigDecimal importe) {
        EntradaPrueba entrada = new EntradaPrueba(null, rfc, importe);

        assertThat(VALIDATOR.validate(entrada))
                .as("un nombre nulo (obligatorio) debe rechazarse")
                .isNotEmpty();
    }

    // Feature: crm-anuncios-luminosos, Property 23: Validacion de datos de entrada
    // Un importe fuera del rango 0.01..999,999,999.99 siempre se rechaza.
    @Property(tries = 1000)
    void importeFueraDeRangoSeRechaza(@ForAll("nombresValidos") String nombre,
                                      @ForAll("rfcsValidos") String rfc,
                                      @ForAll("importesFueraDeRango") BigDecimal importe) {
        EntradaPrueba entrada = new EntradaPrueba(nombre, rfc, importe);

        assertThat(VALIDATOR.validate(entrada))
                .as("un importe fuera de rango debe rechazarse: %s", importe)
                .isNotEmpty();
    }

    // Feature: crm-anuncios-luminosos, Property 23: Validacion de datos de entrada
    // Un identificador fiscal con formato invalido siempre se rechaza.
    @Property(tries = 1000)
    void rfcConFormatoInvalidoSeRechaza(@ForAll("nombresValidos") String nombre,
                                        @ForAll("rfcsInvalidos") String rfc,
                                        @ForAll("importesValidos") BigDecimal importe) {
        EntradaPrueba entrada = new EntradaPrueba(nombre, rfc, importe);

        assertThat(VALIDATOR.validate(entrada))
                .as("un RFC con formato invalido debe rechazarse: %s", rfc)
                .isNotEmpty();
    }

    // ---------------------------------------------------------------------
    // Generadores de campos validos
    // ---------------------------------------------------------------------

    @Provide
    Arbitrary<String> nombresValidos() {
        // Longitud 1..200 (incluye los limites 1 y 200).
        return Arbitraries.oneOf(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(1),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(200).ofMaxLength(200),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(200));
    }

    @Provide
    Arbitrary<String> rfcsValidos() {
        Arbitrary<String> letrasIniciales = Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZÑ&")
                .ofMinLength(3).ofMaxLength(4);
        Arbitrary<String> fecha = Arbitraries.strings().withChars("0123456789").ofLength(6);
        Arbitrary<String> homoclave = Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").ofLength(3);
        return Combinators.combine(letrasIniciales, fecha, homoclave)
                .as((l, f, h) -> l + f + h);
    }

    @Provide
    Arbitrary<BigDecimal> importesValidos() {
        // Rango 0.01..999,999,999.99 con 2 decimales, incluyendo los limites.
        Arbitrary<BigDecimal> limites = Arbitraries.of(MIN_IMPORTE, MAX_IMPORTE, new BigDecimal("1.00"));
        Arbitrary<BigDecimal> intervalo = Arbitraries.bigDecimals()
                .between(MIN_IMPORTE, MAX_IMPORTE)
                .ofScale(2);
        return Arbitraries.oneOf(limites, intervalo);
    }

    // ---------------------------------------------------------------------
    // Generadores de entradas completas
    // ---------------------------------------------------------------------

    @Provide
    Arbitrary<EntradaPrueba> entradasValidas() {
        return Combinators.combine(nombresValidos(), rfcsValidos(), importesValidos())
                .as(EntradaPrueba::new);
    }

    /**
     * Entradas invalidas: al menos uno de los campos incumple una regla. Se
     * construye mezclando valores validos e invalidos garantizando que la
     * combinacion resultante sea invalida.
     */
    @Provide
    Arbitrary<EntradaPrueba> entradasInvalidas() {
        Arbitrary<String> nombre = Arbitraries.oneOf(nombresValidos(), nombresInvalidos());
        Arbitrary<String> rfc = Arbitraries.oneOf(rfcsValidos(), rfcsInvalidos());
        Arbitrary<BigDecimal> importe = Arbitraries.oneOf(importesValidos(), importesFueraDeRango());

        return Combinators.combine(nombre, rfc, importe)
                .as(EntradaPrueba::new)
                .filter(e -> !VALIDATOR.validate(e).isEmpty());
    }

    // ---------------------------------------------------------------------
    // Generadores de campos invalidos
    // ---------------------------------------------------------------------

    @Provide
    Arbitrary<String> nombresInvalidos() {
        // Vacio (longitud 0) o excede el maximo de 200. El caso nulo se cubre aparte.
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(201).ofMaxLength(400));
    }

    @Provide
    Arbitrary<String> rfcsInvalidos() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("123456789012"),           // solo digitos
                Arbitraries.just("AB123456XYZ"),            // pocas letras iniciales
                Arbitraries.just("ABCDE123456XYZ"),         // demasiadas letras iniciales
                Arbitraries.just("ABC12345XY"),             // longitud incorrecta
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20)); // minusculas
    }

    @Provide
    Arbitrary<BigDecimal> importesFueraDeRango() {
        return Arbitraries.oneOf(
                Arbitraries.just(BigDecimal.ZERO),                       // < 0.01
                Arbitraries.just(new BigDecimal("0.001")),               // por debajo del minimo
                Arbitraries.just(new BigDecimal("-5.00")),               // negativo
                Arbitraries.just(new BigDecimal("1000000000.00")),       // > maximo
                Arbitraries.bigDecimals().between(
                        new BigDecimal("1000000000.00"), new BigDecimal("9999999999.99")).ofScale(2));
    }
}

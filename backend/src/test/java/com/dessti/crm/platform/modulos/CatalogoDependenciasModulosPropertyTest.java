package com.dessti.crm.platform.modulos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la normalizacion de dependencias de
 * modulos: {@link CatalogoDependenciasModulos#normalizar(java.util.Collection)}.
 *
 * <p>Ejercita directamente el componente PURO y estatico, sin base de datos ni
 * contexto de Spring, comprobando universalmente los invariantes de la unica
 * dependencia declarada hoy: {@code inventario-avanzado} <strong>requiere</strong>
 * {@code operacion}.</p>
 *
 * <h2>Propiedades verificadas (tarea 3.2)</h2>
 * <ol>
 *   <li><strong>Property 1:</strong> la normalizacion agrega {@code operacion}
 *       cuando hay {@code inventario-avanzado} (Valida 6.1, 7.1, 7.2, 8.1).</li>
 *   <li><strong>Property 2:</strong> la normalizacion es idempotente, sin
 *       duplicar {@code operacion} (Valida 7.3, 8.3, 10.4).</li>
 *   <li><strong>Property 3:</strong> la dependencia es unidireccional y preserva
 *       el resto de los modulos (Valida 7.4, 10.5).</li>
 * </ol>
 */
class CatalogoDependenciasModulosPropertyTest {

    private static final String INVENTARIO_AVANZADO = "inventario-avanzado";
    private static final String OPERACION = "operacion";

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Claves de modulo "conocidas" de la plataforma, ya normalizadas. Se combinan
     * con cadenas aleatorias para ejercitar tanto las claves con dependencia como
     * el resto de modulos que deben preservarse intactos.
     */
    private static final List<String> CLAVES_CONOCIDAS = List.of(
            OPERACION,
            INVENTARIO_AVANZADO,
            "comercial",
            "estrategia",
            "redes-sociales",
            "contabilidad",
            "rh-nomina",
            "tesoreria");

    /**
     * Genera un modulo arbitrario: mayoritariamente una clave conocida (para
     * ejercitar la regla de dependencia con frecuencia) y ocasionalmente una
     * cadena aleatoria corta (para verificar que se preservan modulos
     * desconocidos). Las claves conocidas se emiten ya normalizadas.
     */
    private static Arbitrary<String> unModulo() {
        Arbitrary<String> conocidas = Arbitraries.of(CLAVES_CONOCIDAS);
        Arbitrary<String> aleatorias = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(12)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT));
        // Peso 4:1 a favor de las claves conocidas para densidad de la dependencia.
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(4, conocidas),
                net.jqwik.api.Tuple.of(1, aleatorias));
    }

    /** Lista arbitraria de modulos (posiblemente con repetidos y desconocidos). */
    @Provide
    Arbitrary<List<String>> modulosArbitrarios() {
        return unModulo().list().ofMinSize(0).ofMaxSize(12);
    }

    /**
     * Lista arbitraria de modulos que <strong>siempre</strong> contiene
     * {@code inventario-avanzado} (insertado en una posicion aleatoria), para
     * ejercitar de forma dirigida la activacion de {@code operacion}.
     */
    @Provide
    Arbitrary<List<String>> modulosConInventarioAvanzado() {
        return modulosArbitrarios().map(base -> {
            List<String> conDependiente = new ArrayList<>(base);
            // Insertar en una posicion determinista (final) es suficiente; el orden
            // exacto no importa para esta propiedad (se compara por contenido).
            conDependiente.add(INVENTARIO_AVANZADO);
            return conDependiente;
        });
    }

    /**
     * Lista arbitraria de modulos que contiene {@code operacion} pero
     * <strong>nunca</strong> {@code inventario-avanzado}, para verificar que la
     * dependencia es unidireccional.
     */
    @Provide
    Arbitrary<List<String>> modulosConOperacionSinInventarioAvanzado() {
        return modulosArbitrarios().map(base -> {
            List<String> filtrada = new ArrayList<>();
            for (String m : base) {
                // Descartar cualquier variante (mayus/espacios) de inventario-avanzado.
                if (m == null || !m.strip().toLowerCase(java.util.Locale.ROOT).equals(INVENTARIO_AVANZADO)) {
                    filtrada.add(m);
                }
            }
            filtrada.add(OPERACION);
            return filtrada;
        });
    }

    // ----------------------------------------------------------------------
    // Property 1 — agrega operacion cuando hay inventario-avanzado
    // ----------------------------------------------------------------------

    // Feature: operacion-inventario-modulo-dependiente, Property 1: La normalizacion agrega operacion cuando hay inventario-avanzado
    @Property(tries = 100)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 1: La normalizacion agrega operacion cuando hay inventario-avanzado")
    void normalizarAgregaOperacionCuandoHayInventarioAvanzado(
            @ForAll("modulosConInventarioAvanzado") List<String> modulos) {
        // Para todo conjunto que contenga inventario-avanzado, el resultado debe
        // incluir tanto inventario-avanzado como su requerido operacion.
        Set<String> resultado = CatalogoDependenciasModulos.normalizar(modulos);

        assertThat(resultado).contains(INVENTARIO_AVANZADO);
        assertThat(resultado).contains(OPERACION);
    }

    // ----------------------------------------------------------------------
    // Property 2 — idempotencia sin duplicar operacion
    // ----------------------------------------------------------------------

    // Feature: operacion-inventario-modulo-dependiente, Property 2: La normalizacion es idempotente
    @Property(tries = 100)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 2: La normalizacion es idempotente")
    void normalizarEsIdempotente(@ForAll("modulosArbitrarios") List<String> modulos) {
        Set<String> unaVez = CatalogoDependenciasModulos.normalizar(modulos);
        Set<String> dosVeces = CatalogoDependenciasModulos.normalizar(new ArrayList<>(unaVez));

        // normalizar(x) == normalizar(normalizar(x)): mismo contenido exacto.
        assertThat(dosVeces).isEqualTo(unaVez);

        // No se duplica operacion: a lo sumo aparece una vez (por ser un Set, se
        // verifica ademas que el conteo por igualdad de claves sea 0 o 1).
        long apariciones = unaVez.stream().filter(OPERACION::equals).count();
        assertThat(apariciones).isLessThanOrEqualTo(1L);
    }

    // ----------------------------------------------------------------------
    // Property 3 — unidireccional y preserva el resto
    // ----------------------------------------------------------------------

    // Feature: operacion-inventario-modulo-dependiente, Property 3: La dependencia es unidireccional y preserva el resto
    @Property(tries = 100)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 3: La dependencia es unidireccional y preserva el resto")
    void dependenciaEsUnidireccionalYPreservaElResto(
            @ForAll("modulosConOperacionSinInventarioAvanzado") List<String> conOperacion,
            @ForAll("modulosArbitrarios") List<String> cualquiera) {
        // (a) Unidireccional: operacion sin inventario-avanzado NO arrastra
        // inventario-avanzado.
        Set<String> resultadoUnidireccional =
                CatalogoDependenciasModulos.normalizar(conOperacion);
        assertThat(resultadoUnidireccional).contains(OPERACION);
        assertThat(resultadoUnidireccional).doesNotContain(INVENTARIO_AVANZADO);

        // (b) Preservacion: para todo conjunto, cada modulo original valido
        // (normalizado, no nulo, no vacio) se conserva en el resultado.
        Set<String> resultado = CatalogoDependenciasModulos.normalizar(cualquiera);
        Set<String> originalesNormalizados = new LinkedHashSet<>();
        for (String m : cualquiera) {
            if (m != null) {
                String clave = m.strip().toLowerCase(java.util.Locale.ROOT);
                if (!clave.isEmpty()) {
                    originalesNormalizados.add(clave);
                }
            }
        }
        assertThat(resultado).containsAll(originalesNormalizados);
    }
}

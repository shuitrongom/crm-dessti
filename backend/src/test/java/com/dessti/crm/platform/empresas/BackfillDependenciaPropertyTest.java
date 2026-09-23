package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.dessti.crm.platform.modulos.CatalogoDependenciasModulos;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>idempotencia del backfill</strong>
 * de la migracion Flyway {@code V65__dependencia_operacion_inventario_avanzado.sql}.
 *
 * <h2>Que se modela (Parte A)</h2>
 * <p>La migracion V65 aplica, sobre el array JSONB {@code modulos_habilitados}, la
 * transformacion condicional:</p>
 * <pre>
 *   UPDATE ...
 *   SET modulos_habilitados = modulos_habilitados || '["operacion"]'::jsonb
 *   WHERE modulos_habilitados @&gt; '["inventario-avanzado"]'::jsonb
 *     AND NOT (modulos_habilitados @&gt; '["operacion"]'::jsonb);
 * </pre>
 * <p>Es decir: <em>si el array contiene {@code inventario-avanzado} y NO contiene
 * {@code operacion}, agrega {@code operacion} al final; en cualquier otro caso lo deja
 * intacto</em>. El predicado del {@code WHERE} es exactamente lo que hace idempotente al
 * backfill: reaplicarlo no vuelve a encontrar la fila (no duplica {@code operacion}) y el
 * operador {@code ||} preserva los demas modulos del array sin tocarlos.</p>
 *
 * <p>Aqui {@link #backfill(List)} reproduce esa semantica pura sobre {@code List<String>}
 * (el array de modulos), sin base de datos. Ademas se verifica que esta semantica coincide
 * con el cierre de dependencia de la fuente unica de verdad
 * {@link CatalogoDependenciasModulos#normalizar(java.util.Collection)}, de modo que la
 * propiedad se demuestra sobre la MISMA regla de negocio que persiste al guardar Planes,
 * Paquetes y overrides de Suscripcion.</p>
 *
 * <h2>Propiedad verificada (tarea 3.14)</h2>
 * <ul>
 *   <li><strong>Property 6:</strong> el backfill es idempotente y preserva los demas
 *       modulos (Valida 10.1, 10.2, 10.3, 10.4, 10.5, 11.2).</li>
 * </ul>
 *
 * <h2>Nota sobre la Parte B (prueba de integracion del SQL real)</h2>
 * <p>El repositorio ejercita el SQL real unicamente mediante pruebas de integracion
 * marcadas {@code *IT} con {@code @Testcontainers} + PostgreSQL, que <strong>no corren bajo
 * {@code mvn -o test}</strong> (requieren Docker). No existe un mecanismo de prueba de
 * migracion sin Docker (p. ej. H2 no soporta el operador JSONB {@code @>} ni {@code ||}
 * sobre {@code jsonb}). Para no introducir dependencias nuevas ni una prueba fragil, la
 * idempotencia del backfill se verifica aqui a nivel de propiedad sobre su semantica pura
 * (Parte A), complementada por la revision del predicado idempotente del propio V65.</p>
 */
class BackfillDependenciaPropertyTest {

    private static final String INVENTARIO_AVANZADO = "inventario-avanzado";
    private static final String OPERACION = "operacion";

    // ----------------------------------------------------------------------
    // Semantica pura del backfill V65 (equivalente al SQL)
    // ----------------------------------------------------------------------

    /**
     * Reproduce, de forma pura, la sentencia {@code UPDATE ... SET modulos_habilitados =
     * modulos_habilitados || '["operacion"]'::jsonb WHERE ... @> inventario-avanzado AND
     * NOT ... @> operacion} del V65 sobre el array de modulos.
     *
     * <p>Semantica exacta del SQL:</p>
     * <ul>
     *   <li>Si el array contiene {@code inventario-avanzado} y NO contiene {@code operacion},
     *       agrega {@code operacion} <strong>al final</strong> (operador {@code ||}).</li>
     *   <li>En cualquier otro caso, devuelve el array sin cambios.</li>
     * </ul>
     *
     * <p>Nota: el {@code ||} de PostgreSQL agrega sin deduplicar el resto del array; por eso
     * esta funcion tampoco altera los elementos preexistentes (los preserva tal cual).</p>
     *
     * @param modulos array de modulos actual (no nulo).
     * @return el array resultante tras una aplicacion del backfill.
     */
    private static List<String> backfill(List<String> modulos) {
        List<String> resultado = new ArrayList<>(modulos);
        boolean tieneInventarioAvanzado = resultado.contains(INVENTARIO_AVANZADO);
        boolean tieneOperacion = resultado.contains(OPERACION);
        if (tieneInventarioAvanzado && !tieneOperacion) {
            resultado.add(OPERACION); // Equivalente a `|| '["operacion"]'::jsonb`.
        }
        return resultado;
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Claves de modulo "conocidas" de la plataforma, ya normalizadas. Incluyen las dos
     * claves de la dependencia y el resto que debe preservarse intacto.
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
     * Genera un array arbitrario de modulos <strong>sin duplicados</strong> (como el estado
     * real de un {@code modulos_habilitados} JSONB, que es un conjunto). Se favorecen las
     * claves conocidas para densidad de la dependencia y se anaden ocasionalmente cadenas
     * aleatorias para verificar la preservacion de modulos desconocidos.
     */
    @Provide
    Arbitrary<List<String>> arraysDeModulos() {
        Arbitrary<String> conocidas = Arbitraries.of(CLAVES_CONOCIDAS);
        Arbitrary<String> aleatorias = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(12)
                .map(s -> s.toLowerCase(Locale.ROOT));
        Arbitrary<String> unModulo = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(4, conocidas),
                net.jqwik.api.Tuple.of(1, aleatorias));
        // Deduplicar preservando el orden de insercion, para emular un array JSONB (conjunto).
        return unModulo.list().ofMinSize(0).ofMaxSize(12)
                .map(BackfillDependenciaPropertyTest::sinDuplicados);
    }

    /** Elimina duplicados preservando el orden de aparicion. */
    private static List<String> sinDuplicados(List<String> lista) {
        return new ArrayList<>(new LinkedHashSet<>(lista));
    }

    // ----------------------------------------------------------------------
    // Property 6 — el backfill es idempotente y preserva los demas modulos
    // ----------------------------------------------------------------------

    // Feature: operacion-inventario-modulo-dependiente, Property 6: El backfill es idempotente y preserva los demas modulos
    @Property(tries = 200)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 6: El backfill es idempotente y preserva los demas modulos")
    void backfillEsIdempotenteYPreservaLosDemasModulos(
            @ForAll("arraysDeModulos") List<String> inicial) {

        // Estado y modulos originales (para la asercion de preservacion).
        boolean teniaInventarioAvanzado = inicial.contains(INVENTARIO_AVANZADO);
        boolean teniaOperacion = inicial.contains(OPERACION);
        Set<String> originales = new LinkedHashSet<>(inicial);

        // (1) Aplicar el backfill una vez.
        List<String> unaVez = backfill(inicial);

        // (2) Aplicar el backfill varias veces mas: debe ser IDEMPOTENTE.
        List<String> dosVeces = backfill(unaVez);
        List<String> tresVeces = backfill(dosVeces);
        assertThat(dosVeces).isEqualTo(unaVez);
        assertThat(tresVeces).isEqualTo(unaVez);

        // (3) operacion aparece exactamente una vez si (y solo si) correspondia.
        long aparicionesOperacion = unaVez.stream().filter(OPERACION::equals).count();
        if (teniaInventarioAvanzado) {
            // Con inventario-avanzado, el resultado SIEMPRE contiene operacion (una sola vez).
            assertThat(unaVez).contains(OPERACION);
            assertThat(aparicionesOperacion).isEqualTo(1L);
        } else {
            // Sin inventario-avanzado, la dependencia es unidireccional: NO se agrega operacion.
            assertThat(unaVez.contains(OPERACION)).isEqualTo(teniaOperacion);
            assertThat(aparicionesOperacion).isLessThanOrEqualTo(1L);
        }

        // (4) Preservacion: todos los modulos originales se conservan sin alteracion.
        assertThat(unaVez).containsAll(originales);

        // (5) El backfill NO agrega nada mas que, a lo sumo, operacion.
        Set<String> agregados = new LinkedHashSet<>(unaVez);
        agregados.removeAll(originales);
        if (teniaInventarioAvanzado && !teniaOperacion) {
            assertThat(agregados).containsExactly(OPERACION);
        } else {
            assertThat(agregados).isEmpty();
        }

        // (6) Coherencia con la fuente unica de verdad: la semantica del backfill sobre el
        //     eje inventario-avanzado -> operacion coincide con el cierre de dependencia de
        //     CatalogoDependenciasModulos.normalizar(...). Ambos deben producir el MISMO
        //     conjunto de modulos (comparado por contenido, ignorando el orden).
        Set<String> viaNormalizar = CatalogoDependenciasModulos.normalizar(inicial);
        assertThat(new LinkedHashSet<>(unaVez))
                .containsExactlyInAnyOrderElementsOf(viaNormalizar);
    }

    // ----------------------------------------------------------------------
    // Property 6 (caso tester dirigido) — override con inventario-avanzado
    // ----------------------------------------------------------------------

    /**
     * Genera arrays que <strong>siempre</strong> contienen {@code inventario-avanzado}
     * (como el override de la empresa "tester"
     * {@code ["comercial","inventario-avanzado","estrategia","redes-sociales"]}), para
     * ejercitar de forma dirigida la rama que agrega {@code operacion}.
     */
    @Provide
    Arbitrary<List<String>> arraysConInventarioAvanzado() {
        return arraysDeModulos().map(base -> {
            List<String> conDependiente = new ArrayList<>(base);
            if (!conDependiente.contains(INVENTARIO_AVANZADO)) {
                conDependiente.add(INVENTARIO_AVANZADO);
            }
            return conDependiente;
        });
    }

    // Feature: operacion-inventario-modulo-dependiente, Property 6: El backfill es idempotente y preserva los demas modulos
    @Property(tries = 100)
    @Label("Feature: operacion-inventario-modulo-dependiente, Property 6: El backfill es idempotente y preserva los demas modulos (rama con inventario-avanzado)")
    void backfillConInventarioAvanzadoAgregaOperacionUnaVezYEsEstable(
            @ForAll("arraysConInventarioAvanzado") List<String> inicial) {

        Set<String> originales = new LinkedHashSet<>(inicial);

        List<String> unaVez = backfill(inicial);
        List<String> dosVeces = backfill(unaVez);

        // Idempotente y con operacion presente exactamente una vez.
        assertThat(dosVeces).isEqualTo(unaVez);
        assertThat(unaVez).contains(OPERACION);
        assertThat(unaVez.stream().filter(OPERACION::equals).count()).isEqualTo(1L);

        // Se conservan todos los modulos originales (p. ej. comercial, estrategia, redes-sociales).
        assertThat(unaVez).containsAll(originales);
    }
}

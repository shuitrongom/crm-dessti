package com.dessti.crm.platform.security.roles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.dessti.crm.platform.vertical.ContratoVertical;
import com.dessti.crm.platform.vertical.ItemNavegacionVertical;
import com.dessti.crm.platform.vertical.RegistroVerticales;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Prueba basada en propiedades (jqwik) del {@link ClasificadorRecursosVertical}
 * que cubre la <strong>Property 6</strong> del diseño de la plataforma
 * multigiro: los permisos aplicables se clasifican por Giro (Validates
 * Requirements 7.2, 7.3, 7.4).
 *
 * <p>Ejercita directamente el {@link ClasificadorRecursosVertical} sobre un
 * {@link RegistroVerticales} construido con verticales generados: sin base de
 * datos ni contexto de Spring, ya que la clasificacion recurso&rarr;Giro es
 * logica pura y determinista.</p>
 *
 * <h2>Invariante verificado</h2>
 * <p>Para todo Giro {@code g} y todo catalogo compuesto por recursos de Nucleo y
 * recursos de varios verticales (con conjuntos de recursos <strong>disjuntos</strong>
 * entre verticales, para respetar la unicidad recurso&rarr;Giro del
 * {@link RegistroVerticales}), el conjunto de recursos aplicables a {@code g}
 * segun {@link ClasificadorRecursosVertical#esRecursoAplicableAGiro(String, String)}:</p>
 * <ul>
 *   <li>contiene <strong>todos</strong> los recursos de Nucleo (Req 7.2);</li>
 *   <li>contiene <strong>todos</strong> los recursos del vertical de {@code g} (Req 7.2, 7.3);</li>
 *   <li><strong>no</strong> contiene ningun recurso de un vertical de Giro
 *       distinto de {@code g} (Req 7.3, 7.4).</li>
 * </ul>
 * <p>Adicionalmente comprueba que {@link ClasificadorRecursosVertical#giroDeRecurso(String)}
 * resuelve cada recurso de vertical a su Giro y deja vacio los de Nucleo.</p>
 */
class ClasificadorRecursosVerticalPropertyTest {

    // ----------------------------------------------------------------------
    // Implementacion de prueba (fake) del Contrato_Vertical
    // ----------------------------------------------------------------------

    /**
     * Fake inmutable de {@link ContratoVertical} que declara una clave de Giro,
     * unas claves de modulo y un conjunto de recursos. La navegacion se deja
     * vacia por no ser relevante para la clasificacion de recursos.
     */
    private static final class VerticalFake implements ContratoVertical {

        private final String giro;
        private final Set<String> modulos;
        private final Set<String> recursos;

        VerticalFake(String giro, Set<String> modulos, Set<String> recursos) {
            this.giro = giro;
            this.modulos = modulos;
            this.recursos = recursos;
        }

        @Override
        public String giro() {
            return giro;
        }

        @Override
        public Set<String> modulos() {
            return modulos;
        }

        @Override
        public Set<String> recursos() {
            return recursos;
        }

        @Override
        public List<ItemNavegacionVertical> navegacion() {
            return List.of();
        }
    }

    /**
     * Escenario generado: la lista de verticales (con Giros y recursos disjuntos),
     * el conjunto de recursos de Nucleo (que ningun vertical declara) y el indice
     * del Giro elegido {@code g} dentro de la lista.
     */
    private record Escenario(List<ContratoVertical> verticales,
                             Set<String> recursosNucleo,
                             int indiceGiroElegido) {
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Genera un escenario con entre 1 y 8 verticales de claves de Giro distintas
     * y conjuntos de recursos <strong>disjuntos</strong> entre verticales
     * (derivados del indice: {@code giro-i}, recursos {@code rec-i-a},
     * {@code rec-i-b}, ...), mas un conjunto de recursos de Nucleo que ningun
     * vertical declara ({@code nucleo-*}, disjunto del espacio de nombres de los
     * verticales por construccion). Se elige un indice de Giro {@code g} valido.
     *
     * @return escenarios reproducibles y por construccion coherentes con la
     *         unicidad recurso&rarr;Giro que valida el {@link RegistroVerticales}.
     */
    @Provide
    Arbitrary<Escenario> escenarios() {
        Arbitrary<Integer> numeroVerticales = Arbitraries.integers().between(1, 8);
        Arbitrary<Integer> recursosPorVertical = Arbitraries.integers().between(1, 4);
        Arbitrary<Integer> numeroRecursosNucleo = Arbitraries.integers().between(0, 6);

        return Combinators.combine(numeroVerticales, recursosPorVertical, numeroRecursosNucleo)
                .as((n, recursosCadaVertical, recursosNucleo) -> new int[] {
                        n, recursosCadaVertical, recursosNucleo })
                .flatMap(params -> {
                    int n = params[0];
                    int recursosCadaVertical = params[1];
                    int recursosNucleo = params[2];

                    List<ContratoVertical> verticales = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        Set<String> recursos = new LinkedHashSet<>();
                        for (int r = 0; r < recursosCadaVertical; r++) {
                            // Espacio de nombres derivado del indice del vertical:
                            // disjunto entre verticales por construccion.
                            recursos.add("rec-" + i + "-" + (char) ('a' + r));
                        }
                        Set<String> modulos = Set.of("modulo-" + i);
                        verticales.add(new VerticalFake("giro-" + i, modulos, recursos));
                    }

                    Set<String> nucleo = new LinkedHashSet<>();
                    for (int k = 0; k < recursosNucleo; k++) {
                        // Prefijo "nucleo-" disjunto del espacio "rec-*" de los verticales.
                        nucleo.add("nucleo-" + k);
                    }

                    return Arbitraries.integers().between(0, n - 1)
                            .map(indiceGiro -> new Escenario(verticales, nucleo, indiceGiro));
                });
    }

    // ----------------------------------------------------------------------
    // Property 6 — Los permisos aplicables se clasifican por Giro
    // ----------------------------------------------------------------------

    // Feature: plataforma-multigiro, Property 6: Los permisos aplicables se clasifican por Giro
    @Property(tries = 1000)
    void recursosAplicablesAUnGiroSonNucleoMasElVerticalPropioYNuncaUnVerticalAjeno(
            @ForAll("escenarios") Escenario escenario) {

        RegistroVerticales registro = new RegistroVerticales(escenario.verticales());
        ClasificadorRecursosVertical clasificador = new ClasificadorRecursosVertical(registro);

        ContratoVertical verticalElegido = escenario.verticales().get(escenario.indiceGiroElegido());
        String giro = verticalElegido.giro();

        // (1) Todo recurso de Nucleo es aplicable a cualquier Giro (Req 7.2).
        for (String recursoNucleo : escenario.recursosNucleo()) {
            assertThat(clasificador.esRecursoAplicableAGiro(recursoNucleo, giro))
                    .as("el recurso de Nucleo '%s' debe ser aplicable al Giro '%s'", recursoNucleo, giro)
                    .isTrue();
            // Un recurso de Nucleo no pertenece a ningun vertical.
            assertThat(clasificador.giroDeRecurso(recursoNucleo))
                    .as("el recurso de Nucleo '%s' no debe pertenecer a ningun vertical", recursoNucleo)
                    .isEmpty();
        }

        // (2) Todo recurso del vertical de g es aplicable a g (Req 7.2, 7.3).
        for (String recursoPropio : verticalElegido.recursos()) {
            assertThat(clasificador.esRecursoAplicableAGiro(recursoPropio, giro))
                    .as("el recurso propio '%s' debe ser aplicable a su Giro '%s'", recursoPropio, giro)
                    .isTrue();
            assertThat(clasificador.giroDeRecurso(recursoPropio))
                    .as("giroDeRecurso('%s') debe resolver a '%s'", recursoPropio, giro)
                    .contains(giro);
        }

        // (3) Ningun recurso de un vertical ajeno (Giro != g) es aplicable a g
        //     (Req 7.3, 7.4), y giroDeRecurso lo resuelve a su propio Giro ajeno.
        for (ContratoVertical otro : escenario.verticales()) {
            if (otro.giro().equals(giro)) {
                continue;
            }
            for (String recursoAjeno : otro.recursos()) {
                assertThat(clasificador.esRecursoAplicableAGiro(recursoAjeno, giro))
                        .as("el recurso ajeno '%s' (Giro '%s') NO debe ser aplicable al Giro '%s'",
                                recursoAjeno, otro.giro(), giro)
                        .isFalse();
                assertThat(clasificador.giroDeRecurso(recursoAjeno))
                        .as("giroDeRecurso('%s') debe resolver a su Giro ajeno '%s'",
                                recursoAjeno, otro.giro())
                        .contains(otro.giro());
            }
        }
    }
}

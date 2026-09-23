package com.dessti.crm.platform.vertical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) del {@link RegistroVerticales}, que
 * cubren la <strong>Property 2</strong> (indexacion por Giro, Validates Req 4.3)
 * y la <strong>Property 3</strong> (rechazo de Giros duplicados, Validates Req
 * 4.4) del diseño de la plataforma multigiro.
 *
 * <p>Ambas propiedades ejercitan directamente el constructor y los metodos de
 * consulta del {@link RegistroVerticales}, sin base de datos ni contexto de
 * Spring: la construccion es determinista y sin estado, por lo que las
 * invariantes de indexacion y de fail-fast se comprueban universalmente sobre
 * conjuntos de verticales generados.</p>
 *
 * <h2>Invariantes verificados</h2>
 * <ol>
 *   <li><strong>Property 2:</strong> para todo conjunto de verticales con claves
 *       de Giro distintas, {@code porGiro(g)} recupera exactamente el contrato de
 *       clave {@code g}, y {@code girosRegistrados()} es igual al conjunto de
 *       claves de entrada.</li>
 *   <li><strong>Property 3:</strong> para toda lista con al menos dos elementos
 *       que comparten la misma clave de Giro, construir el registro lanza
 *       {@link IllegalStateException} (falla el arranque, Req 4.4).</li>
 * </ol>
 */
class RegistroVerticalesPropertyTest {

    // ----------------------------------------------------------------------
    // Implementacion de prueba (fake) del Contrato_Vertical
    // ----------------------------------------------------------------------

    /**
     * Fake inmutable de {@link ContratoVertical} usado por las propiedades. Cada
     * instancia declara una clave de Giro, un conjunto de claves de modulo y un
     * conjunto de recursos; la navegacion se deja vacia por no ser relevante para
     * la indexacion del registro.
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

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Genera un conjunto de verticales con claves de Giro <strong>distintas</strong>
     * y claves de modulo <strong>distintas entre verticales</strong>, de forma que
     * no choquen con la validacion de modulo duplicado del registro. Las claves se
     * derivan del indice (ya normalizadas: minusculas, formato kebab), lo que las
     * hace unicas por construccion y reproducibles.
     *
     * @return listas de 0 a 25 verticales con Giros y modulos globalmente unicos.
     */
    @Provide
    Arbitrary<List<ContratoVertical>> verticalesConGirosDistintos() {
        return Arbitraries.integers().between(0, 25).map(n -> {
            List<ContratoVertical> verticales = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String giro = "giro-" + i;
                // Cada vertical aporta modulos globalmente unicos (prefijados por su
                // indice) para no colisionar con la unicidad de modulo del registro.
                Set<String> modulos = new LinkedHashSet<>();
                modulos.add("modulo-" + i + "-a");
                modulos.add("modulo-" + i + "-b");
                Set<String> recursos = Set.of("recurso-" + i);
                verticales.add(new VerticalFake(giro, modulos, recursos));
            }
            return verticales;
        });
    }

    /**
     * Genera una lista de verticales que contiene <strong>al menos una colision</strong>
     * de clave de Giro: se produce una base de Giros unicos y luego se inserta un
     * vertical adicional que repite la clave de uno de ellos. Los modulos se
     * mantienen globalmente unicos para garantizar que el fallo provenga del Giro
     * duplicado y no de un modulo duplicado.
     *
     * @return listas de al menos dos verticales con al menos una clave de Giro
     *         repetida.
     */
    @Provide
    Arbitrary<List<ContratoVertical>> verticalesConGiroDuplicado() {
        return Arbitraries.integers().between(1, 25).flatMap(base ->
                Arbitraries.integers().between(0, base - 1).map(indiceRepetido -> {
                    List<ContratoVertical> verticales = new ArrayList<>(base + 1);
                    for (int i = 0; i < base; i++) {
                        verticales.add(new VerticalFake(
                                "giro-" + i,
                                Set.of("modulo-" + i),
                                Set.of("recurso-" + i)));
                    }
                    // Vertical adicional que REPITE la clave de Giro de 'indiceRepetido'
                    // pero con modulo unico, forzando exclusivamente el conflicto de Giro.
                    verticales.add(new VerticalFake(
                            "giro-" + indiceRepetido,
                            Set.of("modulo-extra"),
                            Set.of("recurso-extra")));
                    return verticales;
                }));
    }

    // ----------------------------------------------------------------------
    // Property 2 — El Registro de Verticales indexa cada Giro (Req 4.3)
    // ----------------------------------------------------------------------

    // Feature: plataforma-multigiro, Property 2: El Registro de Verticales indexa cada Giro
    @Property(tries = 1000)
    void porGiroRecuperaElContratoDeCadaClaveYGirosRegistradosCoincide(
            @ForAll("verticalesConGirosDistintos") List<ContratoVertical> verticales) {

        RegistroVerticales registro = new RegistroVerticales(verticales);

        // porGiro(g) recupera EXACTAMENTE el contrato cuya clave es g, para cada Giro.
        for (ContratoVertical vertical : verticales) {
            assertThat(registro.porGiro(vertical.giro()))
                    .as("porGiro('%s') debe recuperar el contrato de esa clave", vertical.giro())
                    .containsSame(vertical);
        }

        // girosRegistrados() == conjunto de claves de Giro de entrada.
        Set<String> girosEntrada = new HashSet<>();
        for (ContratoVertical vertical : verticales) {
            girosEntrada.add(vertical.giro());
        }
        assertThat(registro.girosRegistrados())
                .as("girosRegistrados() debe ser igual al conjunto de claves de entrada")
                .isEqualTo(girosEntrada);

        // giroDeRecurso(r) resuelve al Giro que declara r; un recurso no declarado
        // (de Nucleo) resuelve a vacio (Req 7.2).
        for (ContratoVertical vertical : verticales) {
            for (String recurso : vertical.recursos()) {
                assertThat(registro.giroDeRecurso(recurso))
                        .as("giroDeRecurso('%s') debe resolver al Giro '%s'", recurso, vertical.giro())
                        .contains(vertical.giro());
            }
        }
        assertThat(registro.giroDeRecurso("recurso-de-nucleo-inexistente"))
                .as("un recurso no declarado por ningun vertical es de Nucleo (vacio)")
                .isEmpty();
    }

    // ----------------------------------------------------------------------
    // Property 3 — El Registro de Verticales rechaza Giros duplicados (Req 4.4)
    // ----------------------------------------------------------------------

    // Feature: plataforma-multigiro, Property 3: El Registro de Verticales rechaza Giros duplicados
    @Property(tries = 1000)
    void construirConGiroDuplicadoSiempreLanzaIllegalStateException(
            @ForAll("verticalesConGiroDuplicado") List<ContratoVertical> verticales) {

        assertThatThrownBy(() -> new RegistroVerticales(verticales))
                .as("una lista con dos verticales de la misma clave de Giro debe romper el arranque")
                .isInstanceOf(IllegalStateException.class);
    }
}

package com.dessti.crm.rhnomina.organizacion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 35: Organigrama
 * sin ciclos</strong> (Validates Req 61.7; tarea 36.2).
 *
 * <p>Ejercita directamente el componente PURO
 * {@link GrafoOrganigrama#introduciriaCiclo(java.util.Collection, UUID, UUID)} y
 * {@link GrafoOrganigrama#derivar(java.util.Collection)}, sin base de datos ni
 * contexto de Spring: ambos son metodos estaticos, deterministas y sin estado, por
 * lo que la invariante aciclica de la jerarquia de Puestos se comprueba
 * universalmente sobre jerarquias arbitrarias.</p>
 *
 * <h2>Invariantes verificados (Property 35, Req 61.7)</h2>
 * <ol>
 *   <li><strong>Arbol/bosque valido:</strong> una jerarquia construida colgando
 *       cada Puesto de uno ya existente (o dejandolo como raiz) es aciclica: al
 *       insertarlo, {@code introduciriaCiclo} devuelve {@code false} para cada
 *       arista, y {@code derivar} produce un bosque cuyo numero total de nodos
 *       coincide con el de Puestos (no se pierden ni duplican).</li>
 *   <li><strong>Arista descendiente -&gt; ancestro es ciclo:</strong> sobre un
 *       arbol con al menos una arista, colgar un ancestro de uno de sus
 *       descendientes se detecta como ciclo.</li>
 *   <li><strong>Auto-superior es ciclo:</strong> una arista de un Puesto hacia si
 *       mismo se detecta como ciclo.</li>
 *   <li><strong>Cierre aciclico:</strong> tras rechazar toda arista ciclica y
 *       aceptar solo las que {@code introduciriaCiclo} declara seguras, la
 *       jerarquia resultante sigue sin ciclos (verificado con una comprobacion
 *       independiente de aciclidad).</li>
 * </ol>
 */
class OrganigramaSinCiclosPropertyTest {

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Genera una jerarquia ACICLICA por construccion: N Puestos donde el Puesto en
     * la posicion {@code i} tiene como superior alguno de los Puestos previos
     * ({@code 0..i-1}) o {@code null} (raiz). Por construccion no puede haber ciclos.
     */
    @Provide
    Arbitrary<List<PuestoJerarquia>> jerarquiasAciclicas() {
        return Arbitraries.integers().between(1, 40).flatMap(n ->
                // Para cada nodo i, elegir un "superior candidato" en [-1, n-1]
                // (-1 = raiz). El superior efectivo se acota a un nodo ESTRICTAMENTE
                // anterior (< i) al construir, lo que garantiza aciclidad. Los
                // identificadores son deterministas por indice para reproducibilidad.
                Arbitraries.integers().between(-1, n - 1).list().ofSize(n)
                        .map(seleccion -> {
                            List<UUID> ids = new ArrayList<>(n);
                            for (int i = 0; i < n; i++) {
                                ids.add(new UUID(0L, i + 1L));
                            }
                            List<PuestoJerarquia> jerarquia = new ArrayList<>(n);
                            for (int i = 0; i < n; i++) {
                                int elegido = seleccion.get(i);
                                UUID superiorId =
                                        (elegido >= 0 && elegido < i) ? ids.get(elegido) : null;
                                jerarquia.add(new PuestoJerarquia(
                                        ids.get(i), "Puesto-" + i, superiorId));
                            }
                            return jerarquia;
                        }));
    }

    // ----------------------------------------------------------------------
    // Property 35 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 35: Organigrama sin ciclos
    @Property(tries = 1000)
    void construirArbolNuncaReportaCicloYDerivaBosqueValido(
            @ForAll("jerarquiasAciclicas") List<PuestoJerarquia> jerarquia) {

        // Al insertar cada Puesto sobre los ya presentes, ninguna arista introduce ciclo.
        List<PuestoJerarquia> acumulado = new ArrayList<>();
        for (PuestoJerarquia puesto : jerarquia) {
            List<PuestoJerarquia> base = new ArrayList<>(acumulado);
            base.add(puesto);
            assertThat(GrafoOrganigrama.introduciriaCiclo(base, puesto.id(), puesto.superiorId()))
                    .as("colgar %s de %s no debe reportar ciclo en un arbol",
                            puesto.id(), puesto.superiorId())
                    .isFalse();
            acumulado.add(puesto);
        }

        // El bosque derivado conserva exactamente todos los Puestos (sin perdidas ni duplicados).
        List<OrganigramaNodo> bosque = GrafoOrganigrama.derivar(jerarquia);
        assertThat(contarNodos(bosque))
                .as("el organigrama derivado debe contener todos los Puestos")
                .isEqualTo(jerarquia.size());
        assertThat(esAciclico(jerarquia))
                .as("la jerarquia construida por arbol debe ser aciclica")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 35: Organigrama sin ciclos
    @Property(tries = 1000)
    void colgarUnAncestroDeSuDescendienteSeDetectaComoCiclo(
            @ForAll("jerarquiasAciclicas") List<PuestoJerarquia> jerarquia) {

        // Buscar un par (descendiente, ancestro) con ancestro != descendiente.
        Map<UUID, UUID> superiorDe = new HashMap<>();
        for (PuestoJerarquia p : jerarquia) {
            superiorDe.put(p.id(), p.superiorId());
        }

        for (PuestoJerarquia p : jerarquia) {
            UUID ancestro = superiorDe.get(p.id());
            if (ancestro != null) {
                // La arista (ancestro -> p) invierte la relacion existente p (desc) -> ancestro:
                // ancestro es descendiente-candidato y p es su ancestro -> debe ser ciclo.
                assertThat(GrafoOrganigrama.introduciriaCiclo(jerarquia, ancestro, p.id()))
                        .as("colgar el ancestro %s de su descendiente %s debe ser ciclo",
                                ancestro, p.id())
                        .isTrue();
                return; // basta un par por caso generado.
            }
        }
        // Si no hay ninguna arista (todos raiz), no aplica: el caso es trivialmente valido.
    }

    // Feature: crm-anuncios-luminosos, Property 35: Organigrama sin ciclos
    @Property(tries = 1000)
    void autoSuperiorSiempreEsCiclo(
            @ForAll("jerarquiasAciclicas") List<PuestoJerarquia> jerarquia) {

        for (PuestoJerarquia p : jerarquia) {
            assertThat(GrafoOrganigrama.introduciriaCiclo(jerarquia, p.id(), p.id()))
                    .as("un Puesto no puede ser su propio superior (%s)", p.id())
                    .isTrue();
        }
    }

    // Feature: crm-anuncios-luminosos, Property 35: Organigrama sin ciclos
    @Property(tries = 1000)
    void trasRechazarAristasCiclicasLaJerarquiaSigueSinCiclos(
            @ForAll("jerarquiasAciclicas") List<PuestoJerarquia> jerarquia,
            @ForAll("jerarquiasAciclicas") List<PuestoJerarquia> otros) {

        // Estado base aciclico como mapa mutable hijo -> superior.
        Map<UUID, UUID> superiorDe = new HashMap<>();
        List<UUID> ids = new ArrayList<>();
        for (PuestoJerarquia p : jerarquia) {
            superiorDe.put(p.id(), p.superiorId());
            ids.add(p.id());
        }

        // Proponer aristas arbitrarias entre pares de ids existentes; aceptar solo
        // las que introduciriaCiclo declara seguras. El resultado debe seguir aciclico.
        if (ids.size() >= 2) {
            for (int k = 0; k < otros.size(); k++) {
                UUID hijo = ids.get(k % ids.size());
                UUID nuevoSuperior = ids.get((k * 7 + 3) % ids.size());

                List<PuestoJerarquia> estado = estadoActual(superiorDe);
                boolean ciclo = GrafoOrganigrama.introduciriaCiclo(estado, hijo, nuevoSuperior);
                if (!ciclo) {
                    superiorDe.put(hijo, nuevoSuperior);
                    assertThat(esAciclico(estadoActual(superiorDe)))
                            .as("tras aceptar la arista segura %s -> %s la jerarquia sigue aciclica",
                                    hijo, nuevoSuperior)
                            .isTrue();
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Utilidades de verificacion independientes (no usan la clase bajo prueba)
    // ----------------------------------------------------------------------

    private static List<PuestoJerarquia> estadoActual(Map<UUID, UUID> superiorDe) {
        List<PuestoJerarquia> estado = new ArrayList<>(superiorDe.size());
        for (Map.Entry<UUID, UUID> e : superiorDe.entrySet()) {
            estado.add(new PuestoJerarquia(e.getKey(), "n", e.getValue()));
        }
        return estado;
    }

    /**
     * Comprobacion INDEPENDIENTE de aciclidad: asciende desde cada nodo por sus
     * superiores; si vuelve a visitar un nodo del mismo recorrido, hay ciclo.
     */
    private static boolean esAciclico(List<PuestoJerarquia> jerarquia) {
        Map<UUID, UUID> superiorDe = new HashMap<>();
        for (PuestoJerarquia p : jerarquia) {
            superiorDe.put(p.id(), p.superiorId());
        }
        for (UUID inicio : superiorDe.keySet()) {
            Set<UUID> visitados = new HashSet<>();
            UUID actual = inicio;
            while (actual != null) {
                if (!visitados.add(actual)) {
                    return false; // ciclo detectado
                }
                actual = superiorDe.get(actual);
            }
        }
        return true;
    }

    /** Cuenta el numero total de nodos del bosque (recorrido en anchura). */
    private static int contarNodos(List<OrganigramaNodo> bosque) {
        int total = 0;
        Deque<OrganigramaNodo> pendientes = new ArrayDeque<>(bosque);
        while (!pendientes.isEmpty()) {
            OrganigramaNodo nodo = pendientes.pop();
            total++;
            pendientes.addAll(nodo.subordinados());
        }
        return total;
    }
}

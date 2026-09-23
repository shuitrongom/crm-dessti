package com.dessti.crm.operacion.proyecto.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la funcion pura de dominio
 * {@link DerivacionEstadoProyecto}, que cubren la <strong>Property 1</strong>
 * (derivacion del estado consolidado por fases aplicables, Validates Req 3.2,
 * 3.3, 3.4, 3.6) y la <strong>Property 2</strong> (equivalencia del perfil
 * ANUNCIOS con la derivacion clasica y dependencia del perfil GENERICO solo de la
 * fase Produccion, Validates Req 3.5) del diseño de operacion-produccion-enterprise.
 *
 * <p>La derivacion es una funcion pura sin infraestructura (ni Spring ni JPA), por
 * lo que las propiedades se ejercen directamente sobre
 * {@link DerivacionEstadoProyecto#derivar(PerfilFasesGiro, java.util.List)} y su
 * firma de conveniencia {@link DerivacionEstadoProyecto#derivar(java.util.List)}.
 * Los generadores construyen listas de {@link AvanceFasesSitio} con las cuatro
 * banderas aleatorias y perfiles {@link PerfilFasesGiro#ANUNCIOS} /
 * {@link PerfilFasesGiro#GENERICO}, de modo que las invariantes se comprueban sobre
 * todo el espacio de entradas relevante.</p>
 */
class DerivacionEstadoProyectoPropertyTest {

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /**
     * Genera un {@link AvanceFasesSitio} con las cuatro banderas de fase
     * (levantamiento, permiso, orden de fabricacion e instalacion) elegidas de
     * forma independiente y aleatoria, cubriendo las 16 combinaciones posibles.
     *
     * @return avances de Sitio con banderas booleanas arbitrarias.
     */
    @Provide
    Arbitrary<AvanceFasesSitio> avancesSitio() {
        Arbitrary<Boolean> bandera = Arbitraries.of(true, false);
        return Combinators.combine(bandera, bandera, bandera, bandera)
                .as(AvanceFasesSitio::new);
    }

    /**
     * Genera listas de {@link AvanceFasesSitio} de 0 a 8 elementos (incluye la lista
     * vacia, que representa un Proyecto sin Sitios), con banderas aleatorias por
     * Sitio.
     *
     * @return listas de avances de Sitio de tamaño variable.
     */
    @Provide
    Arbitrary<List<AvanceFasesSitio>> listasDeAvances() {
        return avancesSitio().list().ofMinSize(0).ofMaxSize(8);
    }

    /**
     * Genera uno de los dos perfiles predefinidos: {@link PerfilFasesGiro#ANUNCIOS}
     * (las cuatro fases) o {@link PerfilFasesGiro#GENERICO} (solo Produccion).
     *
     * @return un perfil de fases de giro arbitrario.
     */
    @Provide
    Arbitrary<PerfilFasesGiro> perfiles() {
        return Arbitraries.of(PerfilFasesGiro.ANUNCIOS, PerfilFasesGiro.GENERICO);
    }

    // ----------------------------------------------------------------------
    // Property 1 — Derivacion por fases aplicables (Req 3.2, 3.3, 3.4, 3.6)
    // ----------------------------------------------------------------------

    // Feature: operacion-produccion-enterprise, Property 1: derivacion por fases aplicables (vacio->SIN_SITIOS; COMPLETADO sii todos los Sitios cubren todas las fases aplicables; si no, la primera fase aplicable no cubierta en orden Levantamiento->Permiso->Produccion->Instalacion; determinista)
    @Property(tries = 1000)
    void derivacionPorFasesAplicables(
            @ForAll("perfiles") PerfilFasesGiro perfil,
            @ForAll("listasDeAvances") List<AvanceFasesSitio> avances) {

        EstadoConsolidadoProyecto resultado = DerivacionEstadoProyecto.derivar(perfil, avances);

        // (a) Lista vacia -> SIN_SITIOS para cualquier perfil (Req 3.4).
        if (avances.isEmpty()) {
            assertThat(resultado)
                    .as("un Proyecto sin Sitios es SIN_SITIOS con independencia del perfil")
                    .isEqualTo(EstadoConsolidadoProyecto.SIN_SITIOS);
            return;
        }

        // (b) Determinismo: misma entrada -> misma salida al reevaluar (Req 3.6).
        assertThat(DerivacionEstadoProyecto.derivar(perfil, avances))
                .as("la derivacion es determinista: misma entrada, misma salida")
                .isEqualTo(resultado);

        // Cobertura por fase: ¿todos los Sitios cubren cada fase?
        boolean todosLevantamiento = avances.stream()
                .allMatch(AvanceFasesSitio::tieneLevantamientoCompletado);
        boolean todosPermiso = avances.stream()
                .allMatch(AvanceFasesSitio::tienePermisoAprobado);
        boolean todosFabricacion = avances.stream()
                .allMatch(AvanceFasesSitio::tieneOrdenFabricacionTerminada);
        boolean todosInstalacion = avances.stream()
                .allMatch(AvanceFasesSitio::tieneInstalacionCompletada);

        // Se recalcula de forma independiente cual deberia ser el estado esperado:
        // primera fase APLICABLE no cubierta por todos, en el orden operativo; si
        // todas las aplicables estan cubiertas -> COMPLETADO.
        EstadoConsolidadoProyecto esperado;
        boolean todasAplicablesCubiertas;
        if (perfil.aplica(FaseProyecto.LEVANTAMIENTO) && !todosLevantamiento) {
            esperado = EstadoConsolidadoProyecto.EN_LEVANTAMIENTO;
            todasAplicablesCubiertas = false;
        } else if (perfil.aplica(FaseProyecto.PERMISO) && !todosPermiso) {
            esperado = EstadoConsolidadoProyecto.EN_TRAMITE_PERMISOS;
            todasAplicablesCubiertas = false;
        } else if (perfil.aplica(FaseProyecto.PRODUCCION) && !todosFabricacion) {
            esperado = EstadoConsolidadoProyecto.EN_PRODUCCION;
            todasAplicablesCubiertas = false;
        } else if (perfil.aplica(FaseProyecto.INSTALACION) && !todosInstalacion) {
            esperado = EstadoConsolidadoProyecto.EN_INSTALACION;
            todasAplicablesCubiertas = false;
        } else {
            esperado = EstadoConsolidadoProyecto.COMPLETADO;
            todasAplicablesCubiertas = true;
        }

        // (c) El resultado corresponde a la primera fase aplicable no cubierta (o a
        // COMPLETADO si no hay ninguna). (Req 3.2, 3.3)
        assertThat(resultado)
                .as("el estado refleja la primera fase aplicable no cubierta en orden operativo")
                .isEqualTo(esperado);

        // (d) COMPLETADO si y solo si todas las fases aplicables estan cubiertas
        // por todos los Sitios (Req 3.3).
        assertThat(resultado == EstadoConsolidadoProyecto.COMPLETADO)
                .as("COMPLETADO sii todas las fases aplicables estan cubiertas por todos")
                .isEqualTo(todasAplicablesCubiertas);
    }

    // ----------------------------------------------------------------------
    // Property 2 — Equivalencia por perfil (Req 3.5)
    // ----------------------------------------------------------------------

    // Feature: operacion-produccion-enterprise, Property 2: derivar(ANUNCIOS, avances) equivale a la derivacion clasica y derivar(GENERICO, avances) depende solo de PRODUCCION
    @Property(tries = 1000)
    void equivalenciaPorPerfil(@ForAll("listasDeAvances") List<AvanceFasesSitio> avances) {

        // (a) El perfil ANUNCIOS equivale a la firma de conveniencia clasica
        // derivar(avances) (Req 3.5).
        EstadoConsolidadoProyecto anuncios =
                DerivacionEstadoProyecto.derivar(PerfilFasesGiro.ANUNCIOS, avances);
        assertThat(anuncios)
                .as("derivar(ANUNCIOS, avances) debe igualar a la derivacion clasica derivar(avances)")
                .isEqualTo(DerivacionEstadoProyecto.derivar(avances));

        // (b) El perfil GENERICO depende unicamente de la fase Produccion.
        EstadoConsolidadoProyecto generico =
                DerivacionEstadoProyecto.derivar(PerfilFasesGiro.GENERICO, avances);

        if (avances.isEmpty()) {
            // Sin Sitios -> SIN_SITIOS, con independencia de la fase Produccion.
            assertThat(generico).isEqualTo(EstadoConsolidadoProyecto.SIN_SITIOS);
        } else {
            boolean todosFabricacion = avances.stream()
                    .allMatch(AvanceFasesSitio::tieneOrdenFabricacionTerminada);

            // Todos con OF terminada -> COMPLETADO; alguno sin ella -> EN_PRODUCCION.
            assertThat(generico)
                    .as("GENERICO: todos con OF terminada -> COMPLETADO; alguno sin ella -> EN_PRODUCCION")
                    .isEqualTo(todosFabricacion
                            ? EstadoConsolidadoProyecto.COMPLETADO
                            : EstadoConsolidadoProyecto.EN_PRODUCCION);

            // El perfil GENERICO nunca deriva a las fases no aplicables.
            assertThat(generico)
                    .as("GENERICO nunca deriva a fases no aplicables (levantamiento/permiso/instalacion)")
                    .isNotIn(
                            EstadoConsolidadoProyecto.EN_LEVANTAMIENTO,
                            EstadoConsolidadoProyecto.EN_TRAMITE_PERMISOS,
                            EstadoConsolidadoProyecto.EN_INSTALACION);
        }
    }
}

package com.dessti.crm.social.analitica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.DireccionMensaje;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 40: Metricas
 * sociales como agregacion de solo lectura y por tenant</strong> (Req 66.1, 66.6).
 *
 * <p>La property valida que, para cualquier conjunto de filas fuente de una o varias
 * Empresas, el calculo de {@link CalculoMetricasSociales} es:
 * <ul>
 *   <li><strong>Solo lectura (Req 66.1):</strong> deterministico (misma entrada,
 *       mismo resultado) y sin mutar la lista de entrada ni sus elementos.</li>
 *   <li><strong>Por tenant (Req 66.6):</strong> las metricas del tenant A son
 *       identicas a las calculadas unicamente con las filas de A; ninguna fila de B
 *       contribuye a las metricas de A (aislamiento a nivel de funcion pura).</li>
 * </ul>
 * ademas de invariantes de sanidad: {@code mensajesRecibidos + mensajesEnviados ==
 * interacciones} y todos los conteos no negativos.</p>
 *
 * <p><strong>Enfoque (dominio puro en memoria):</strong> se ejerce directamente la
 * pieza de produccion {@link CalculoMetricasSociales} sobre listas de
 * {@link FilaMetricaSocial} generadas por jqwik, sin Spring, JPA ni base de datos. El
 * aislamiento por tenant se comprueba de forma determinista comparando el calculo
 * filtrado por tenant contra el calculo sobre la sublista pre-filtrada de ese
 * tenant.</p>
 */
class MetricasSocialesPropertyTest {

    // ----------------------------------------------------------------------
    // Property 40 — Invariantes
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 40: Métricas sociales como agregación de solo lectura y por tenant
    @Property(tries = 1000)
    void agregacionEsDeterministaYNoMutaLaEntrada(
            @ForAll("filasMultiTenant") List<FilaMetricaSocial> filas) {

        // Copia de referencia para verificar que la entrada no se muta.
        List<FilaMetricaSocial> copiaOriginal = new ArrayList<>(filas);

        List<MetricasSociales> primera = CalculoMetricasSociales.porCanal(filas);
        List<MetricasSociales> segunda = CalculoMetricasSociales.porCanal(filas);

        // Solo lectura (Req 66.1): dos invocaciones sobre la misma entrada coinciden.
        assertThat(segunda)
                .as("la agregacion es determinista sobre la misma entrada")
                .isEqualTo(primera);

        // Solo lectura (Req 66.1): la lista de entrada no cambia de tamano ni contenido.
        assertThat(filas)
                .as("la agregacion no muta la lista de entrada")
                .containsExactlyElementsOf(copiaOriginal);
    }

    // Feature: crm-anuncios-luminosos, Property 40: Métricas sociales como agregación de solo lectura y por tenant
    @Property(tries = 1000)
    void metricasPorTenantExcluyenLasFilasDeOtrosTenants(
            @ForAll("mundoMultiTenant") MundoMetricas mundo) {

        UUID objetivo = mundo.tenantObjetivo();
        List<FilaMetricaSocial> mezcladas = mundo.filas();

        // Filtro por tenant sobre la lista mezclada.
        List<MetricasSociales> conFiltro =
                CalculoMetricasSociales.porCanalParaTenant(mezcladas, objetivo);

        // Referencia: agregacion sobre SOLO las filas del tenant objetivo.
        List<FilaMetricaSocial> soloObjetivo = mezcladas.stream()
                .filter(f -> objetivo.equals(f.tenantId()))
                .toList();
        List<MetricasSociales> referencia = CalculoMetricasSociales.porCanal(soloObjetivo);

        // Por tenant (Req 66.6): las filas de otras Empresas nunca contribuyen.
        assertThat(conFiltro)
                .as("las metricas del tenant objetivo ignoran las filas de otros tenants")
                .isEqualTo(referencia);
    }

    // Feature: crm-anuncios-luminosos, Property 40: Métricas sociales como agregación de solo lectura y por tenant
    @Property(tries = 1000)
    void interaccionesSonLaSumaDeRecibidosYEnviadosYNoNegativas(
            @ForAll("filasMultiTenant") List<FilaMetricaSocial> filas) {

        List<MetricasSociales> metricas = CalculoMetricasSociales.porCanal(filas);

        for (MetricasSociales m : metricas) {
            assertThat(m.interacciones())
                    .as("interacciones == mensajesRecibidos + mensajesEnviados para el canal %s",
                            m.canal())
                    .isEqualTo(m.mensajesRecibidos() + m.mensajesEnviados());

            assertThat(m.alcance()).as("alcance no negativo").isGreaterThanOrEqualTo(0L);
            assertThat(m.interacciones()).as("interacciones no negativas").isGreaterThanOrEqualTo(0L);
            assertThat(m.mensajesRecibidos()).as("recibidos no negativos").isGreaterThanOrEqualTo(0L);
            assertThat(m.mensajesEnviados()).as("enviados no negativos").isGreaterThanOrEqualTo(0L);
            assertThat(m.tiempoRespuestaPromedioSegundos())
                    .as("tiempo de respuesta no negativo").isGreaterThanOrEqualTo(0L);
            assertThat(m.conversiones()).as("conversiones no negativas").isGreaterThanOrEqualTo(0L);

            // El alcance (Conversaciones distintas) nunca supera las interacciones (mensajes).
            assertThat(m.alcance())
                    .as("el alcance del canal %s no supera sus interacciones", m.canal())
                    .isLessThanOrEqualTo(m.interacciones());
        }
    }

    // ----------------------------------------------------------------------
    // Modelo del escenario
    // ----------------------------------------------------------------------

    /** Escenario multi-tenant: las filas mezcladas y el tenant objetivo del filtro. */
    private record MundoMetricas(List<FilaMetricaSocial> filas, UUID tenantObjetivo) {
    }

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Base temporal fija para que las marcas UTC sean deterministas. */
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    /** Conjunto acotado de tenants candidatos (para forzar solapamientos y aislamiento). */
    private static final List<UUID> TENANTS = List.of(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));

    /** Conjunto acotado de Conversaciones candidatas (para agrupar filas). */
    private static final List<UUID> CONVERSACIONES = List.of(
            UUID.fromString("11111111-0000-0000-0000-000000000001"),
            UUID.fromString("22222222-0000-0000-0000-000000000002"),
            UUID.fromString("33333333-0000-0000-0000-000000000003"),
            UUID.fromString("44444444-0000-0000-0000-000000000004"));

    /**
     * Genera una fila fuente con tenant, canal, Conversacion, direccion, instante
     * (derivado de un desplazamiento en segundos sobre la base) y marca de lead.
     */
    @Provide
    Arbitrary<FilaMetricaSocial> fila() {
        Arbitrary<UUID> tenant = Arbitraries.of(TENANTS);
        Arbitrary<CanalSocial> canal = Arbitraries.of(CanalSocial.values());
        Arbitrary<UUID> conversacion = Arbitraries.of(CONVERSACIONES);
        Arbitrary<DireccionMensaje> direccion = Arbitraries.of(DireccionMensaje.values());
        Arbitrary<Integer> offset = Arbitraries.integers().between(0, 100_000);
        Arbitrary<Boolean> esLead = Arbitraries.of(true, false);

        return Combinators.combine(tenant, canal, conversacion, direccion, offset, esLead)
                .as((t, c, cv, d, off, lead) -> new FilaMetricaSocial(
                        t, c, cv, d, BASE.plusSeconds(off), lead));
    }

    /** Lista multi-tenant de filas (0..40), que incluye el caso vacio. */
    @Provide
    Arbitrary<List<FilaMetricaSocial>> filasMultiTenant() {
        return fila().list().ofMinSize(0).ofMaxSize(40);
    }

    /**
     * Construye un mundo con una lista mezclada de al menos una fila y elige el primer
     * tenant candidato como objetivo del filtro (garantiza que hay filas de varios
     * tenants posibles para verificar el aislamiento).
     */
    @Provide
    Arbitrary<MundoMetricas> mundoMultiTenant() {
        return fila().list().ofMinSize(1).ofMaxSize(40)
                .map(filas -> new MundoMetricas(filas, TENANTS.get(0)));
    }
}

package com.dessti.crm.platform.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

/**
 * Pruebas basadas en propiedades (jqwik) de la <b>Property 24: Acotacion y
 * metadatos de paginacion</b> del diseno de {@code crm-anuncios-luminosos}
 * (tarea 5.4).
 *
 * <p>Se apoyan en la logica REAL de la tarea 5.2
 * ({@link PageRequestFactory}, {@link PaginaResponse}, {@link PaginacionConstantes})
 * sin reimplementarla. Todas las pruebas se ejecutan EN MEMORIA, sin BD,
 * sin Spring y sin Docker.</p>
 *
 * <p>Validates: Requirements 5.7, 6.8, 7.7, 7.8, 12.1, 14.7, 24.5.</p>
 */
class PaginacionPropertyTest {

    // ---------------------------------------------------------------------
    // Tamano efectivo dentro de rango 1..100 con default 20, y size > 100 se rechaza
    // ---------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // Para cualquier solicitud de listado, el tamano de pagina efectivo respeta el
    // rango 1..100 con valor por defecto 20, y los metadatos cumplen
    // totalPages = ceil(totalElements / size); una solicitud con tamano superior a
    // 100 se rechaza conforme al requisito aplicable.
    @Property(tries = 1000)
    void tamanoValidoSeAceptaYRespetaElRango(@ForAll("paginasValidas") int page,
                                             @ForAll("tamanosValidos") int size) {
        Pageable pageable = PageRequestFactory.of(page, size);

        assertThat(pageable.getPageNumber()).isEqualTo(page);
        assertThat(pageable.getPageSize())
                .isEqualTo(size)
                .isBetween(1, PaginacionConstantes.MAX_SIZE);
    }

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // Un size nulo aplica el valor por defecto (20) sin salir del rango 1..100.
    @Property(tries = 1000)
    void tamanoNuloAplicaElDefault(@ForAll("paginasValidas") int page) {
        Pageable pageable = PageRequestFactory.of(page, null);

        assertThat(pageable.getPageSize())
                .isEqualTo(PaginacionConstantes.DEFAULT_SIZE)
                .isEqualTo(20)
                .isBetween(1, PaginacionConstantes.MAX_SIZE);
        assertThat(pageable.getPageNumber()).isEqualTo(page);
    }

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // Una solicitud con size > 100 se RECHAZA con ParametrosPaginacionInvalidosException (=> HTTP 400, Req 7.8).
    @Property(tries = 1000)
    void tamanoMayorAlMaximoSeRechaza(@ForAll("paginasValidas") int page,
                                      @ForAll("tamanosMayoresAlMaximo") int size) {
        assertThatExceptionOfType(ParametrosPaginacionInvalidosException.class)
                .isThrownBy(() -> PageRequestFactory.of(page, size))
                .satisfies(ex -> assertThat(ex.campo()).isEqualTo("size"));
    }

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // Un size < 1 se rechaza siempre con 400 (no representa una pagina valida).
    @Property(tries = 1000)
    void tamanoMenorAUnoSeRechaza(@ForAll("paginasValidas") int page,
                                  @ForAll("tamanosMenoresAUno") int size) {
        assertThatExceptionOfType(ParametrosPaginacionInvalidosException.class)
                .isThrownBy(() -> PageRequestFactory.of(page, size))
                .satisfies(ex -> assertThat(ex.campo()).isEqualTo("size"));
    }

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // Un page < 0 se rechaza siempre con 400.
    @Property(tries = 1000)
    void paginaNegativaSeRechaza(@ForAll("paginasNegativas") int page,
                                 @ForAll("tamanosValidos") int size) {
        assertThatExceptionOfType(ParametrosPaginacionInvalidosException.class)
                .isThrownBy(() -> PageRequestFactory.of(page, size))
                .satisfies(ex -> assertThat(ex.campo()).isEqualTo("page"));
    }

    // ---------------------------------------------------------------------
    // Modo acotando(): size > 100 se limita en vez de rechazar
    // ---------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // En el modo de acotamiento, un size > 100 se limita a MAX_SIZE (100) sin rechazar,
    // permaneciendo dentro del rango 1..100.
    @Property(tries = 1000)
    void modoAcotandoLimitaAlMaximo(@ForAll("paginasValidas") int page,
                                    @ForAll("tamanosMayoresAlMaximo") int size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);

        assertThat(pageable.getPageSize())
                .isEqualTo(PaginacionConstantes.MAX_SIZE)
                .isBetween(1, PaginacionConstantes.MAX_SIZE);
    }

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // En el modo de acotamiento, un size dentro del rango 1..100 se conserva tal cual.
    @Property(tries = 1000)
    void modoAcotandoConservaTamanoValido(@ForAll("paginasValidas") int page,
                                          @ForAll("tamanosValidos") int size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);

        assertThat(pageable.getPageSize()).isEqualTo(size).isBetween(1, PaginacionConstantes.MAX_SIZE);
    }

    // ---------------------------------------------------------------------
    // Metadatos: totalPages == ceil(totalElements / size)
    // ---------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // Para cualquier size valido y cualquier totalElements (incluye 0, no multiplos
    // y casos limite), los metadatos de PaginaResponse cumplen
    // totalPages == ceil(totalElements / size).
    @Property(tries = 1000)
    void metadatosCumplenCeilDeTotalElementosEntreSize(@ForAll("tamanosValidos") int size,
                                                       @ForAll("totalElementos") long totalElements) {
        Pageable pageable = PageRequest.of(0, size);
        // Contenido de la primera pagina, acotado por el total disponible.
        int enPrimeraPagina = (int) Math.min(size, totalElements);
        List<String> contenido = generarContenido(enPrimeraPagina);

        Page<String> page = new PageImpl<>(contenido, pageable, totalElements);
        PaginaResponse<String> respuesta = PaginaResponse.de(page);

        long esperadoTotalPages = ceilDiv(totalElements, size);

        assertThat(respuesta.totalPages()).isEqualTo((int) esperadoTotalPages);
        assertThat(respuesta.totalElements()).isEqualTo(totalElements);
        assertThat(respuesta.size()).isEqualTo(size);
        assertThat(respuesta.page()).isZero();
        assertThat(respuesta.content()).hasSize(enPrimeraPagina);
    }

    // Feature: crm-anuncios-luminosos, Property 24: Acotacion y metadatos de paginacion
    // El mapeo de PaginaResponse.de(page, mapper) preserva los metadatos de paginacion
    // y aplica la transformacion a cada elemento.
    @Property(tries = 500)
    void mapeoDePaginaResponsePreservaMetadatos(@ForAll("tamanosValidos") int size,
                                                @ForAll @LongRange(min = 0, max = 5000) long totalElements) {
        Pageable pageable = PageRequest.of(0, size);
        int enPrimeraPagina = (int) Math.min(size, totalElements);
        List<Integer> contenido = java.util.stream.IntStream.range(0, enPrimeraPagina).boxed().toList();

        Page<Integer> page = new PageImpl<>(contenido, pageable, totalElements);
        PaginaResponse<String> respuesta = PaginaResponse.de(page, i -> "n:" + i);

        assertThat(respuesta.totalPages()).isEqualTo((int) ceilDiv(totalElements, size));
        assertThat(respuesta.totalElements()).isEqualTo(totalElements);
        assertThat(respuesta.content()).allSatisfy(s -> assertThat(s).startsWith("n:"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static long ceilDiv(long total, int size) {
        if (total == 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }

    private static List<String> generarContenido(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> "e" + i).toList();
    }

    // ---------------------------------------------------------------------
    // Generadores
    // ---------------------------------------------------------------------

    @Provide
    Arbitrary<Integer> paginasValidas() {
        return Arbitraries.integers().between(0, 10_000);
    }

    @Provide
    Arbitrary<Integer> paginasNegativas() {
        return Arbitraries.integers().between(Integer.MIN_VALUE / 2, -1);
    }

    /** Tamanos dentro del rango valido, incluyendo los limites 1 y 100. */
    @Provide
    Arbitrary<Integer> tamanosValidos() {
        return Arbitraries.oneOf(
                Arbitraries.just(1),
                Arbitraries.just(PaginacionConstantes.DEFAULT_SIZE),
                Arbitraries.just(PaginacionConstantes.MAX_SIZE),
                Arbitraries.integers().between(1, PaginacionConstantes.MAX_SIZE));
    }

    /** Tamanos por encima del maximo, incluyendo el limite 101. */
    @Provide
    Arbitrary<Integer> tamanosMayoresAlMaximo() {
        return Arbitraries.oneOf(
                Arbitraries.just(PaginacionConstantes.MAX_SIZE + 1),
                Arbitraries.integers().between(PaginacionConstantes.MAX_SIZE + 1, 1_000_000));
    }

    /** Tamanos invalidos por ser menores a 1, incluyendo 0. */
    @Provide
    Arbitrary<Integer> tamanosMenoresAUno() {
        return Arbitraries.oneOf(
                Arbitraries.just(0),
                Arbitraries.integers().between(Integer.MIN_VALUE / 2, 0));
    }

    /** Totales variados: 0, valores pequenos (limites) y grandes no multiplos. */
    @Provide
    Arbitrary<Long> totalElementos() {
        return Arbitraries.oneOf(
                Arbitraries.just(0L),
                Arbitraries.just(1L),
                Arbitraries.longs().between(0, 100),
                Arbitraries.longs().between(0, 1_000_000));
    }
}

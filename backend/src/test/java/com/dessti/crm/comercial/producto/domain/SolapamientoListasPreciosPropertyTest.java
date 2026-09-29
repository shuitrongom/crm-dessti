package com.dessti.crm.comercial.producto.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.time.api.Dates;

/**
 * Pruebas basadas en propiedades (jqwik) de la <strong>Property 31: No
 * coexistencia de listas de precios activas con vigencias solapadas por
 * alcance</strong> (Req 59.11).
 *
 * <p>Ejercita las piezas puras de produccion
 * {@link ListaPrecios#rangosSeSolapan(LocalDate, LocalDate, LocalDate, LocalDate)}
 * y {@link ListaPrecios#mismoAlcance(String, String)}, sin base de datos ni
 * contexto de Spring. Verifica el invariante universal de solapamiento de dos
 * rangos con <strong>fronteras inclusivas</strong> y fin abierto
 * ({@code null} = infinito), consistente con
 * {@link ListaPrecios#estaVigente(LocalDate)}.</p>
 *
 * <h2>Modelo de referencia</h2>
 * <p>Dos rangos {@code [iniA, finA]} y {@code [iniB, finB]} (fin inclusive,
 * {@code null} = +infinito) se solapan si y solo si existe alguna fecha comun a
 * ambos, esto es {@code max(iniA, iniB) <= min(finA, finB)}. La property compara
 * la implementacion de produccion contra este modelo independiente para todo par
 * de rangos, incluyendo los casos de fin abierto y la <strong>adyacencia</strong>
 * ({@code finA == iniB}), que bajo frontera inclusiva cuenta como solapamiento.</p>
 */
class SolapamientoListasPreciosPropertyTest {

    // ----------------------------------------------------------------------
    // Generadores
    // ----------------------------------------------------------------------

    /** Fechas acotadas a una ventana amplia y realista para las vigencias. */
    @Provide
    Arbitrary<LocalDate> fechas() {
        return Dates.dates().between(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31));
    }

    /**
     * Rango de vigencia valido: inicio obligatorio y fin >= inicio o
     * {@code null} (abierto), reflejando la invariante del dominio
     * ({@code vigenciaFin >= vigenciaInicio}).
     */
    @Provide
    Arbitrary<Rango> rangos() {
        Arbitrary<LocalDate> inicio = fechas();
        return inicio.flatMap(ini ->
                Arbitraries.oneOf(
                        // Fin abierto (null).
                        Arbitraries.just((LocalDate) null),
                        // Fin >= inicio.
                        Dates.dates().between(ini, LocalDate.of(2031, 12, 31))
                ).map(fin -> new Rango(ini, fin)));
    }

    // ----------------------------------------------------------------------
    // Property 31 — Invariantes de solapamiento de rangos
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 31: Dos listas de precios activas del mismo alcance no pueden tener vigencias solapadas; el solapamiento de rangos (fronteras inclusivas, fin abierto = infinito) se detecta si y solo si comparten al menos una fecha.
    @Property(tries = 2000)
    void solapamientoEquivaleAExistirFechaComun(@ForAll("rangos") Rango a, @ForAll("rangos") Rango b) {
        boolean produccion = ListaPrecios.rangosSeSolapan(a.inicio(), a.fin(), b.inicio(), b.fin());

        // Modelo de referencia: max(inicios) <= min(fines), con null como +infinito.
        LocalDate maxInicio = a.inicio().isAfter(b.inicio()) ? a.inicio() : b.inicio();
        LocalDate minFin = minFin(a.fin(), b.fin());
        boolean modelo = (minFin == null) || !maxInicio.isAfter(minFin);

        assertThat(produccion)
                .as("solapamiento de [%s,%s] y [%s,%s]", a.inicio(), a.fin(), b.inicio(), b.fin())
                .isEqualTo(modelo);
    }

    // Feature: crm-anuncios-luminosos, Property 31: el solapamiento de rangos es simetrico.
    @Property(tries = 2000)
    void solapamientoEsSimetrico(@ForAll("rangos") Rango a, @ForAll("rangos") Rango b) {
        boolean ab = ListaPrecios.rangosSeSolapan(a.inicio(), a.fin(), b.inicio(), b.fin());
        boolean ba = ListaPrecios.rangosSeSolapan(b.inicio(), b.fin(), a.inicio(), a.fin());
        assertThat(ab).as("la relacion de solapamiento debe ser simetrica").isEqualTo(ba);
    }

    // Feature: crm-anuncios-luminosos, Property 31: todo rango se solapa consigo mismo (reflexividad).
    @Property(tries = 1000)
    void todoRangoSeSolapaConsigoMismo(@ForAll("rangos") Rango a) {
        assertThat(ListaPrecios.rangosSeSolapan(a.inicio(), a.fin(), a.inicio(), a.fin()))
                .as("un rango siempre comparte fechas consigo mismo")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 31: la adyacencia (finA == inicioB) cuenta como solapamiento bajo frontera inclusiva.
    @Property(tries = 1000)
    void adyacenciaEnElMismoDiaCuentaComoSolapamiento(@ForAll("fechas") LocalDate corte) {
        // A termina exactamente el dia en que B comienza: comparten ese dia.
        LocalDate inicioA = corte.minusDays(10);
        LocalDate finA = corte;
        LocalDate inicioB = corte;
        LocalDate finB = corte.plusDays(10);
        assertThat(ListaPrecios.rangosSeSolapan(inicioA, finA, inicioB, finB))
                .as("finA == inicioB comparte el dia de corte -> solapa")
                .isTrue();
    }

    // Feature: crm-anuncios-luminosos, Property 31: rangos disjuntos (con un dia de separacion) NO se solapan.
    @Property(tries = 1000)
    void rangosDisjuntosNoSeSolapan(@ForAll("fechas") LocalDate corte) {
        // A termina el dia anterior al inicio de B: no comparten ninguna fecha.
        LocalDate inicioA = corte.minusDays(10);
        LocalDate finA = corte.minusDays(1);
        LocalDate inicioB = corte;
        LocalDate finB = corte.plusDays(10);
        assertThat(ListaPrecios.rangosSeSolapan(inicioA, finA, inicioB, finB))
                .as("finA (corte-1) < inicioB (corte) -> no solapan")
                .isFalse();
    }

    // Feature: crm-anuncios-luminosos, Property 31: dos vigencias abiertas (fin null) del mismo alcance siempre se solapan.
    @Property(tries = 1000)
    void dosVigenciasAbiertasSiempreSeSolapan(@ForAll("fechas") LocalDate iniA, @ForAll("fechas") LocalDate iniB) {
        assertThat(ListaPrecios.rangosSeSolapan(iniA, null, iniB, null))
                .as("dos vigencias sin fin comparten fechas desde el mayor inicio en adelante")
                .isTrue();
    }

    // ----------------------------------------------------------------------
    // Property 31 — Mismo alcance (segmento)
    // ----------------------------------------------------------------------

    // Feature: crm-anuncios-luminosos, Property 31: dos listas generales (segmento null) comparten alcance; con segmento, coinciden sin distinguir mayusculas.
    @Property(tries = 1000)
    void mismoAlcanceEsCaseInsensitiveYGeneralConGeneral(
            @ForAll("segmentos") String segmentoA, @ForAll("segmentos") String segmentoB) {
        boolean esperado;
        if (segmentoA == null || segmentoB == null) {
            esperado = segmentoA == null && segmentoB == null;
        } else {
            esperado = segmentoA.equalsIgnoreCase(segmentoB);
        }
        assertThat(ListaPrecios.mismoAlcance(segmentoA, segmentoB)).isEqualTo(esperado);
    }

    @Provide
    Arbitrary<String> segmentos() {
        return Arbitraries.of(null, "mayoreo", "MAYOREO", "Mayoreo", "menudeo", "gobierno");
    }

    // ----------------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------------

    /** Menor de dos fines de vigencia tratando {@code null} como +infinito. */
    private static LocalDate minFin(LocalDate finA, LocalDate finB) {
        if (finA == null) {
            return finB;
        }
        if (finB == null) {
            return finA;
        }
        return finA.isBefore(finB) ? finA : finB;
    }

    /** Par (inicio, fin) de vigencia; fin {@code null} = abierto. */
    private record Rango(LocalDate inicio, LocalDate fin) {
    }
}

package com.dessti.crm.comercial.producto.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de dominio de {@link ListaPrecios}, centradas en el predicado de no
 * coexistencia de vigencias solapadas por alcance (Req 59.11):
 * {@link ListaPrecios#seSolapaCon(ListaPrecios)}, con fronteras inclusivas y fin
 * abierto ({@code null} = infinito), consistente con
 * {@link ListaPrecios#estaVigente(LocalDate)}.
 */
class ListaPreciosTest {

    private static final String ACTOR = "ventas";

    private static ListaPrecios lista(String segmento, LocalDate inicio, LocalDate fin) {
        return ListaPrecios.crear("Lista", 1, segmento, inicio, fin, ACTOR);
    }

    @Nested
    @DisplayName("seSolapaCon: mismo alcance y rangos")
    class Solapamiento {

        @Test
        @DisplayName("dos listas generales con rangos que se traslapan se solapan")
        void generalesConRangosTraslapadosSolapan() {
            ListaPrecios a = lista(null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
            ListaPrecios b = lista(null, LocalDate.of(2024, 6, 1), LocalDate.of(2024, 12, 31));
            assertThat(a.seSolapaCon(b)).isTrue();
            assertThat(b.seSolapaCon(a)).isTrue();
        }

        @Test
        @DisplayName("adyacencia (fin de A == inicio de B) cuenta como solapamiento (frontera inclusiva)")
        void adyacenciaEnMismoDiaSolapa() {
            ListaPrecios a = lista(null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
            ListaPrecios b = lista(null, LocalDate.of(2024, 6, 30), LocalDate.of(2024, 12, 31));
            assertThat(a.seSolapaCon(b)).isTrue();
        }

        @Test
        @DisplayName("rangos disjuntos (un dia de separacion) no se solapan")
        void rangosDisjuntosNoSolapan() {
            ListaPrecios a = lista(null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 29));
            ListaPrecios b = lista(null, LocalDate.of(2024, 6, 30), LocalDate.of(2024, 12, 31));
            assertThat(a.seSolapaCon(b)).isFalse();
        }

        @Test
        @DisplayName("una vigencia abierta (fin null) se solapa con cualquier rango posterior del mismo alcance")
        void vigenciaAbiertaSolapaConPosterior() {
            ListaPrecios abierta = lista(null, LocalDate.of(2024, 1, 1), null);
            ListaPrecios posterior = lista(null, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31));
            assertThat(abierta.seSolapaCon(posterior)).isTrue();
        }
    }

    @Nested
    @DisplayName("seSolapaCon: distinto alcance no solapa")
    class DistintoAlcance {

        @Test
        @DisplayName("mismo rango pero distinto segmento no se solapa")
        void distintoSegmentoNoSolapa() {
            ListaPrecios mayoreo = lista("mayoreo", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            ListaPrecios menudeo = lista("menudeo", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            assertThat(mayoreo.seSolapaCon(menudeo)).isFalse();
        }

        @Test
        @DisplayName("lista general y lista con segmento no comparten alcance")
        void generalYSegmentoNoCompartenAlcance() {
            ListaPrecios general = lista(null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            ListaPrecios segmento = lista("mayoreo", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            assertThat(general.seSolapaCon(segmento)).isFalse();
        }

        @Test
        @DisplayName("mismo segmento sin distinguir mayusculas se solapa")
        void mismoSegmentoCaseInsensitiveSolapa() {
            ListaPrecios a = lista("Mayoreo", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            ListaPrecios b = lista("MAYOREO", LocalDate.of(2024, 6, 1), null);
            assertThat(a.seSolapaCon(b)).isTrue();
        }
    }

    @Nested
    @DisplayName("seSolapaCon: casos degenerados")
    class Degenerados {

        @Test
        @DisplayName("una lista no se solapa consigo misma (mismo id)")
        void mismaListaNoSolapaConsigo() {
            ListaPrecios a = lista(null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            assertThat(a.seSolapaCon(a)).isFalse();
        }

        @Test
        @DisplayName("comparar contra null nunca se solapa")
        void contraNullNoSolapa() {
            ListaPrecios a = lista(null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            assertThat(a.seSolapaCon(null)).isFalse();
        }
    }
}

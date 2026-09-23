package com.dessti.crm.estrategia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias por ejemplos del estado derivado de un Objetivo_Estrategico
 * ({@link DerivacionEstadoObjetivo#derivar(BigDecimal, LocalDate, LocalDate, LocalDate)};
 * Req 58.9, 58.10; tarea 37.3).
 *
 * <p>Toda la logica temporal se ejerce con fechas <strong>fijas</strong> para ser
 * determinista. El periodo de referencia dura 100 dias (del 1 de enero al 11 de
 * abril de 2025) para que la fraccion transcurrida sea aritmetica sencilla: el dia
 * {@code inicio + N} corresponde a {@code N%} del periodo transcurrido.</p>
 */
class EstadoDerivadoObjetivoTest {

    /** Inicio del periodo de referencia. */
    private static final LocalDate INICIO = LocalDate.parse("2025-01-01");

    /**
     * Fin del periodo: 100 dias despues del inicio (2025-01-01 + 100 dias =
     * 2025-04-11). Asi, al dia {@code INICIO + N}, la fraccion transcurrida es N%.
     */
    private static final LocalDate FIN = INICIO.plusDays(100); // 2025-04-11

    private static BigDecimal pct(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("avance 100 -> cumplido (independientemente del periodo)")
    void avanceCienEsCumplido() {
        // A mitad del periodo (dia +50 = 50% transcurrido), pero avance 100 => cumplido.
        LocalDate hoy = INICIO.plusDays(50);
        assertThat(DerivacionEstadoObjetivo.derivar(pct("100"), INICIO, FIN, hoy))
                .isEqualTo(EstadoObjetivo.CUMPLIDO);

        // Incluso antes de iniciar el periodo, avance 100 sigue siendo cumplido.
        assertThat(DerivacionEstadoObjetivo.derivar(pct("100"), INICIO, FIN, INICIO.minusDays(5)))
                .isEqualTo(EstadoObjetivo.CUMPLIDO);
    }

    @Test
    @DisplayName("avance por debajo de la fraccion del periodo -> en_riesgo (rezago)")
    void avanceBajoFraccionEsEnRiesgo() {
        // Dia +60 => 60% del periodo transcurrido; avance 40% < 60% => en_riesgo.
        LocalDate hoy = INICIO.plusDays(60);
        assertThat(DerivacionEstadoObjetivo.derivar(pct("40"), INICIO, FIN, hoy))
                .isEqualTo(EstadoObjetivo.EN_RIESGO);
    }

    @Test
    @DisplayName("avance igual a la fraccion del periodo (<100) -> en_curso (consistente)")
    void avanceIgualFraccionEsEnCurso() {
        // Dia +50 => 50% transcurrido; avance 50% == 50% => en_curso.
        LocalDate hoy = INICIO.plusDays(50);
        assertThat(DerivacionEstadoObjetivo.derivar(pct("50"), INICIO, FIN, hoy))
                .isEqualTo(EstadoObjetivo.EN_CURSO);
    }

    @Test
    @DisplayName("avance por encima de la fraccion del periodo (<100) -> en_curso (adelantado)")
    void avanceSobreFraccionEsEnCurso() {
        // Dia +30 => 30% transcurrido; avance 80% > 30% pero < 100 => en_curso.
        LocalDate hoy = INICIO.plusDays(30);
        assertThat(DerivacionEstadoObjetivo.derivar(pct("80"), INICIO, FIN, hoy))
                .isEqualTo(EstadoObjetivo.EN_CURSO);
    }

    @Test
    @DisplayName("antes del inicio del periodo -> en_curso (aun no se espera avance)")
    void antesDelInicioEsEnCurso() {
        // hoy < inicio => fraccion transcurrida 0%; avance 0% >= 0% => en_curso (no hay rezago).
        LocalDate hoy = INICIO.minusDays(10);
        assertThat(DerivacionEstadoObjetivo.derivar(pct("0"), INICIO, FIN, hoy))
                .isEqualTo(EstadoObjetivo.EN_CURSO);
        // Justo en el inicio, la fraccion sigue siendo 0% => en_curso.
        assertThat(DerivacionEstadoObjetivo.derivar(pct("0"), INICIO, FIN, INICIO))
                .isEqualTo(EstadoObjetivo.EN_CURSO);
    }

    @Test
    @DisplayName("despues del fin del periodo con avance < 100 -> en_riesgo (periodo agotado sin cumplir)")
    void despuesDelFinConAvanceIncompletoEsEnRiesgo() {
        // hoy >= fin => fraccion transcurrida 100%; avance 99% < 100% => en_riesgo.
        LocalDate hoy = FIN.plusDays(5);
        assertThat(DerivacionEstadoObjetivo.derivar(pct("99"), INICIO, FIN, hoy))
                .isEqualTo(EstadoObjetivo.EN_RIESGO);
        // Justo en el fin del periodo, la fraccion es 100% => en_riesgo si avance < 100.
        assertThat(DerivacionEstadoObjetivo.derivar(pct("99.99"), INICIO, FIN, FIN))
                .isEqualTo(EstadoObjetivo.EN_RIESGO);
    }

    @Test
    @DisplayName("periodo de un solo dia: en_curso antes del fin, cumplido con avance 100")
    void periodoDeUnDia() {
        LocalDate dia = LocalDate.parse("2025-06-15");
        // inicio == fin; hoy < fin => fraccion 0% => en_curso (avance < 100).
        assertThat(DerivacionEstadoObjetivo.derivar(pct("0"), dia, dia, dia.minusDays(1)))
                .isEqualTo(EstadoObjetivo.EN_CURSO);
        // En el dia del periodo con avance 100 => cumplido.
        assertThat(DerivacionEstadoObjetivo.derivar(pct("100"), dia, dia, dia))
                .isEqualTo(EstadoObjetivo.CUMPLIDO);
        // En el dia del periodo con avance < 100 => la fraccion es 100% => en_riesgo.
        assertThat(DerivacionEstadoObjetivo.derivar(pct("50"), dia, dia, dia))
                .isEqualTo(EstadoObjetivo.EN_RIESGO);
    }

    @Test
    @DisplayName("un fin de periodo anterior al inicio es invalido")
    void periodoInvertidoEsRechazado() {
        assertThatThrownBy(() ->
                DerivacionEstadoObjetivo.derivar(pct("10"), FIN, INICIO, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

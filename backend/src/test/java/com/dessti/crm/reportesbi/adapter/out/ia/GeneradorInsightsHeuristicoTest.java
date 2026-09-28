package com.dessti.crm.reportesbi.adapter.out.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dessti.crm.reportesbi.application.ia.IndicadorInsight;
import com.dessti.crm.reportesbi.application.ia.ResultadoInsights;
import com.dessti.crm.reportesbi.application.ia.SolicitudInsights;

/**
 * Pruebas unitarias del {@link GeneradorInsightsHeuristico} (suite BI+IA). Verifican
 * que el narrativo es <strong>determinista</strong> y de solo lectura: sin red ni
 * credenciales, marca siempre {@code generadoPorIa=false}, refleja la ausencia de datos,
 * resume las tendencias del periodo y levanta una alerta ante caidas pronunciadas.
 */
class GeneradorInsightsHeuristicoTest {

    private final GeneradorInsightsHeuristico generador = new GeneradorInsightsHeuristico();

    private static IndicadorInsight indicador(String area, String clave, String etiqueta,
                                              String valor, String comparativo, String unidad) {
        BigDecimal v = new BigDecimal(valor);
        BigDecimal c = comparativo == null ? null : new BigDecimal(comparativo);
        BigDecimal var = c == null ? null : v.subtract(c);
        return new IndicadorInsight(area, clave, etiqueta, v, unidad, c, var);
    }

    @Test
    @DisplayName("Sin datos: resumen que invita a ajustar el filtro, sin hallazgos, heuristico")
    void sinDatos() {
        ResultadoInsights r = generador.generar(
                new SolicitudInsights(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                        null, List.of()));

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.hallazgos()).isEmpty();
        assertThat(r.resumenEjecutivo()).contains("No hay indicadores con datos");
    }

    @Test
    @DisplayName("Solicitud nula: degrada a un resumen de sin datos sin lanzar")
    void solicitudNula() {
        ResultadoInsights r = generador.generar(null);

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumenEjecutivo()).isNotBlank();
    }

    @Test
    @DisplayName("Con datos sin comparativo: no deriva tendencias y lo dice")
    void sinComparativo() {
        ResultadoInsights r = generador.generar(new SolicitudInsights(
                null, null, null,
                List.of(indicador("COMERCIAL", "cotizaciones", "Cotizaciones", "10", null, "conteo"))));

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumenEjecutivo()).contains("1 indicador con datos");
        assertThat(r.resumenEjecutivo()).contains("No hay comparativo");
        assertThat(r.hallazgos()).isEmpty();
    }

    @Test
    @DisplayName("Con subidas y bajadas: cuenta tendencias y lista hallazgos al alza y a la baja")
    void subidasYBajadas() {
        ResultadoInsights r = generador.generar(new SolicitudInsights(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), null,
                List.of(
                        indicador("COMERCIAL", "cotiz", "Cotizaciones aprobadas", "12", "8", "conteo"),
                        indicador("FINANZAS", "fact", "Facturacion", "80000", "100000", "MXN"))));

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumenEjecutivo()).contains("2 indicadores con datos");
        assertThat(r.resumenEjecutivo()).contains("1 al alza");
        assertThat(r.resumenEjecutivo()).contains("1 a la baja");
        assertThat(r.hallazgos()).anyMatch(h -> h.startsWith("Al alza:"));
        assertThat(r.hallazgos()).anyMatch(h -> h.startsWith("A la baja:"));
    }

    @Test
    @DisplayName("Caida pronunciada (>=20%): emite una alerta especifica")
    void alertaCaidaPronunciada() {
        ResultadoInsights r = generador.generar(new SolicitudInsights(
                null, null, null,
                // 100 -> 50 = -50% (supera el umbral de -20%).
                List.of(indicador("FINANZAS", "fact", "Facturacion", "50", "100", "MXN"))));

        assertThat(r.hallazgos()).anyMatch(h -> h.startsWith("Alerta:") && h.contains("Facturacion"));
    }

    @Test
    @DisplayName("Comparativo cero: no divide por cero ni rompe (variacion relativa nula)")
    void comparativoCeroNoRompe() {
        ResultadoInsights r = generador.generar(new SolicitudInsights(
                null, null, null,
                List.of(indicador("COMERCIAL", "nuevos", "Clientes nuevos", "5", "0", "conteo"))));

        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumenEjecutivo()).isNotBlank();
        // Sube respecto a cero: aparece como hallazgo al alza sin porcentaje.
        assertThat(r.hallazgos()).anyMatch(h -> h.startsWith("Al alza:"));
    }
}

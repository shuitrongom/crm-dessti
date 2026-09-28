package com.dessti.crm.reportesbi.adapter.out.ia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.dessti.crm.reportesbi.application.ia.GeneradorInsightsPort;
import com.dessti.crm.reportesbi.application.ia.IndicadorInsight;
import com.dessti.crm.reportesbi.application.ia.ResultadoInsights;
import com.dessti.crm.reportesbi.application.ia.SolicitudInsights;

/**
 * Generador de insights <strong>heuristico determinista</strong> (Req 48, suite BI+IA).
 * Es la implementacion por defecto del {@link GeneradorInsightsPort} y, a la vez, el
 * respaldo de <em>degradacion gracil</em> del adaptador HTTP real: produce un resumen
 * ejecutivo y hallazgos en espanol de Mexico <strong>puramente aritmeticos</strong>, sin
 * red ni credenciales, de modo que la suite BI siempre entrega un narrativo util aunque
 * la IA no este configurada o falle.
 *
 * <h2>Que narra</h2>
 * <ul>
 *   <li>Un encabezado con el periodo analizado y el alcance (todas las areas o el area
 *       filtrada) y el numero de indicadores con datos.</li>
 *   <li>Los movimientos mas relevantes respecto al periodo anterior: mayores subidas,
 *       mayores caidas y una alerta cuando una metrica cae de forma pronunciada.</li>
 * </ul>
 *
 * <p>El resultado es reproducible: mismas entradas producen exactamente el mismo texto,
 * lo que lo hace apto para pruebas unitarias sin dobles de red. Marca siempre
 * {@link ResultadoInsights#generadoPorIa()} como {@code false}.</p>
 */
public class GeneradorInsightsHeuristico implements GeneradorInsightsPort {

    /** Locale es-MX para el formato de numeros y fechas. */
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(ES_MX);

    /** Umbral de variacion relativa a partir del cual una caida se considera alerta. */
    private static final BigDecimal UMBRAL_ALERTA_CAIDA = new BigDecimal("-0.20");

    /** Numero maximo de hallazgos por categoria (subidas/caidas) para no saturar. */
    private static final int MAX_POR_CATEGORIA = 3;

    @Override
    public ResultadoInsights generar(SolicitudInsights solicitud) {
        if (solicitud == null || solicitud.sinDatos()) {
            return ResultadoInsights.deHeuristico(
                    "No hay indicadores con datos para el periodo seleccionado. Ajusta el "
                            + "rango de fechas o el area para obtener un analisis.",
                    List.of());
        }

        List<IndicadorInsight> indicadores = solicitud.indicadores();
        List<IndicadorInsight> conComparativo = indicadores.stream()
                .filter(IndicadorInsight::tieneComparativo)
                .toList();

        String resumen = componerResumen(solicitud, indicadores.size(), conComparativo);
        List<String> hallazgos = componerHallazgos(conComparativo);
        return ResultadoInsights.deHeuristico(resumen, hallazgos);
    }

    // ------------------------------------------------------------------
    // Composicion del narrativo
    // ------------------------------------------------------------------

    private String componerResumen(SolicitudInsights solicitud, int totalIndicadores,
                                   List<IndicadorInsight> conComparativo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analisis del ").append(alcance(solicitud.area()));
        sb.append(rangoPeriodo(solicitud.desde(), solicitud.hasta()));
        sb.append(". Se consolidaron ").append(totalIndicadores)
                .append(totalIndicadores == 1 ? " indicador con datos" : " indicadores con datos");

        if (conComparativo.isEmpty()) {
            sb.append(". No hay comparativo con un periodo anterior, por lo que no se derivan "
                    + "tendencias; define un rango de fechas acotado para habilitarlas.");
            return sb.toString();
        }

        long suben = conComparativo.stream().filter(i -> i.variacion().signum() > 0).count();
        long bajan = conComparativo.stream().filter(i -> i.variacion().signum() < 0).count();
        long estables = conComparativo.size() - suben - bajan;
        sb.append(", de los cuales ").append(conComparativo.size())
                .append(" tienen comparativo: ").append(suben).append(" al alza, ")
                .append(bajan).append(" a la baja y ").append(estables).append(" sin cambio.");

        IndicadorInsight mayorSubida = extremo(conComparativo, true);
        IndicadorInsight mayorCaida = extremo(conComparativo, false);
        if (mayorSubida != null && mayorSubida.variacion().signum() > 0) {
            sb.append(" Destaca al alza ").append(frase(mayorSubida)).append('.');
        }
        if (mayorCaida != null && mayorCaida.variacion().signum() < 0) {
            sb.append(" Requiere atencion ").append(frase(mayorCaida)).append('.');
        }
        return sb.toString();
    }

    private List<String> componerHallazgos(List<IndicadorInsight> conComparativo) {
        if (conComparativo.isEmpty()) {
            return List.of();
        }
        List<String> hallazgos = new ArrayList<>();

        // Mayores subidas (variacion positiva), de mayor a menor.
        conComparativo.stream()
                .filter(i -> i.variacion().signum() > 0)
                .sorted(porMagnitudRelativaDesc())
                .limit(MAX_POR_CATEGORIA)
                .forEach(i -> hallazgos.add("Al alza: " + frase(i) + "."));

        // Mayores caidas (variacion negativa), de mayor caida a menor.
        conComparativo.stream()
                .filter(i -> i.variacion().signum() < 0)
                .sorted(porMagnitudRelativaDesc())
                .limit(MAX_POR_CATEGORIA)
                .forEach(i -> hallazgos.add("A la baja: " + frase(i) + "."));

        // Alertas: caidas pronunciadas por encima del umbral relativo.
        conComparativo.stream()
                .filter(this::esCaidaPronunciada)
                .sorted(porMagnitudRelativaDesc())
                .forEach(i -> hallazgos.add("Alerta: " + i.etiqueta() + " (" + i.area()
                        + ") cayo " + porcentaje(i.variacionRelativa()) + " respecto al periodo "
                        + "anterior; conviene revisar la causa."));

        return hallazgos;
    }

    // ------------------------------------------------------------------
    // Utilidades de ordenamiento y frases
    // ------------------------------------------------------------------

    private Comparator<IndicadorInsight> porMagnitudRelativaDesc() {
        return Comparator.comparing(this::magnitudRelativa).reversed();
    }

    private BigDecimal magnitudRelativa(IndicadorInsight indicador) {
        BigDecimal relativa = indicador.variacionRelativa();
        if (relativa != null) {
            return relativa.abs();
        }
        // Sin base relativa (comparativo cero): ordena por variacion absoluta normalizada
        // al final, para que no domine a las variaciones relativas legitimas.
        return indicador.variacion() == null ? BigDecimal.ZERO : indicador.variacion().abs();
    }

    private boolean esCaidaPronunciada(IndicadorInsight indicador) {
        BigDecimal relativa = indicador.variacionRelativa();
        return relativa != null && relativa.compareTo(UMBRAL_ALERTA_CAIDA) <= 0;
    }

    private IndicadorInsight extremo(List<IndicadorInsight> lista, boolean maximo) {
        Comparator<IndicadorInsight> porVariacion =
                Comparator.comparing(IndicadorInsight::variacion);
        return lista.stream()
                .max(maximo ? porVariacion : porVariacion.reversed())
                .orElse(null);
    }

    /**
     * Frase legible de un indicador con su variacion, por ejemplo:
     * "Cotizaciones aprobadas (COMERCIAL), que paso de 8 a 12 (+50.0%)".
     */
    private String frase(IndicadorInsight indicador) {
        StringBuilder sb = new StringBuilder();
        sb.append(indicador.etiqueta()).append(" (").append(indicador.area()).append("), que paso de ")
                .append(valor(indicador.comparativo(), indicador.unidad()))
                .append(" a ").append(valor(indicador.valor(), indicador.unidad()));
        BigDecimal relativa = indicador.variacionRelativa();
        if (relativa != null) {
            sb.append(" (").append(signo(indicador.variacion())).append(porcentaje(relativa.abs()))
                    .append(')');
        } else {
            sb.append(" (").append(signo(indicador.variacion()))
                    .append(valor(indicador.variacion().abs(), indicador.unidad())).append(')');
        }
        return sb.toString();
    }

    private String alcance(String area) {
        return (area == null || area.isBlank())
                ? "desempeno consolidado de todas las areas"
                : "desempeno del area " + area;
    }

    private String rangoPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null) {
            return " para el periodo del " + desde.format(FORMATO_FECHA) + " al "
                    + hasta.format(FORMATO_FECHA);
        }
        if (desde != null) {
            return " a partir del " + desde.format(FORMATO_FECHA);
        }
        if (hasta != null) {
            return " hasta el " + hasta.format(FORMATO_FECHA);
        }
        return " sin filtro de fechas (historico completo)";
    }

    private String signo(BigDecimal variacion) {
        return variacion != null && variacion.signum() < 0 ? "-" : "+";
    }

    /** Formatea un valor segun su unidad: importes con prefijo de moneda, resto como numero. */
    private String valor(BigDecimal valor, String unidad) {
        BigDecimal v = (valor == null) ? BigDecimal.ZERO : valor;
        if (unidad != null && !unidad.isBlank()
                && !"conteo".equalsIgnoreCase(unidad)
                && !"porcentaje".equalsIgnoreCase(unidad)
                && !"dias".equalsIgnoreCase(unidad)) {
            NumberFormat nf = NumberFormat.getNumberInstance(ES_MX);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return unidad + " " + nf.format(v.setScale(2, RoundingMode.HALF_UP));
        }
        NumberFormat nf = NumberFormat.getNumberInstance(ES_MX);
        nf.setMaximumFractionDigits(2);
        return nf.format(v.stripTrailingZeros());
    }

    /** Formatea una fraccion como porcentaje con un decimal (0.5 -> "50.0%"). */
    private String porcentaje(BigDecimal fraccion) {
        if (fraccion == null) {
            return "0.0%";
        }
        BigDecimal pct = fraccion.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
        NumberFormat nf = NumberFormat.getNumberInstance(ES_MX);
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(pct) + "%";
    }
}

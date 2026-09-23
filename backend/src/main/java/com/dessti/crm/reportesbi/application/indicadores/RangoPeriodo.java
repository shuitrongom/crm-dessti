package com.dessti.crm.reportesbi.application.indicadores;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Utilidad de solo lectura que traduce el rango de fechas ({@link LocalDate}) de un
 * {@link FiltroIndicadores} a limites {@link Instant} en UTC, para acotar las
 * agregaciones de los adaptadores de {@link IndicadorAreaPort} sobre columnas
 * {@code created_at}/timestamp (Req 22.3, 48.4).
 *
 * <p>Convencion: {@code desde} se toma al inicio del dia (inclusivo) y {@code hasta}
 * al inicio del dia siguiente (exclusivo), de modo que el dia {@code hasta} queda
 * incluido por completo. Un limite nulo produce {@code null}, que los repositorios
 * interpretan como "sin filtro por fecha". Clase de utilidad, no instanciable.</p>
 */
public final class RangoPeriodo {

    /**
     * Cota inferior centinela para un {@code desde} nulo sobre columnas timestamp.
     * Equivale a "sin limite inferior": todo dato realista es posterior a la epoca
     * Unix ({@code 1970-01-01T00:00:00Z}). Se usa para que el parametro viaje SIEMPRE
     * tipado (no nulo) y PostgreSQL pueda inferir su tipo, evitando el error
     * "could not determine data type of parameter" que produce el patron
     * {@code (:desde IS NULL OR ...)}.
     */
    public static final Instant INSTANTE_MINIMO = Instant.EPOCH;

    /**
     * Cota superior centinela para un {@code hasta} nulo sobre columnas timestamp.
     * Equivale a "sin limite superior": todo dato realista es anterior a esta fecha,
     * dentro del rango soportado por PostgreSQL. Mantiene la semantica de comparacion
     * exclusiva ({@code < :hasta}) e inclusiva ({@code <= :hasta}) sin descartar filas
     * reales.
     */
    public static final Instant INSTANTE_MAXIMO =
            LocalDate.of(9999, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant();

    /**
     * Cota inferior centinela para un {@code desde} nulo sobre columnas {@code date}.
     * Equivale a "sin limite inferior" y queda dentro del rango de fechas soportado por
     * PostgreSQL (evita {@link LocalDate#MIN}, que excede dicho rango).
     */
    public static final LocalDate FECHA_MINIMA = LocalDate.of(1, 1, 1);

    /**
     * Cota superior centinela para un {@code hasta} nulo sobre columnas {@code date}.
     * Equivale a "sin limite superior" y queda dentro del rango de fechas soportado por
     * PostgreSQL (evita {@link LocalDate#MAX}, que excede dicho rango).
     */
    public static final LocalDate FECHA_MAXIMA = LocalDate.of(9999, 12, 31);

    private RangoPeriodo() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Convierte el limite inferior {@code desde} al inicio del dia en UTC (inclusivo).
     *
     * @param desde fecha inicial del periodo; {@code null} para no filtrar.
     * @return el instante de inicio del periodo, o {@code null}.
     */
    public static Instant desdeInclusivo(LocalDate desde) {
        if (desde == null) {
            return null;
        }
        return desde.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Convierte el limite superior {@code hasta} al inicio del dia siguiente en UTC
     * (exclusivo), de forma que el dia {@code hasta} queda incluido por completo.
     *
     * @param hasta fecha final del periodo (inclusiva en dias); {@code null} para no
     *              filtrar.
     * @return el instante exclusivo de fin del periodo, o {@code null}.
     */
    public static Instant hastaExclusivo(LocalDate hasta) {
        if (hasta == null) {
            return null;
        }
        return hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Variante <em>null-safe</em> de {@link #desdeInclusivo(LocalDate)} que nunca
     * devuelve {@code null}: un {@code desde} nulo se traduce a {@link #INSTANTE_MINIMO}
     * (epoca Unix). Preserva la semantica "sin limite inferior" para datos reales y
     * permite que los repositorios comparen con un parametro SIEMPRE tipado
     * ({@code o.createdAt >= :desde}), sin el patron {@code (:desde IS NULL OR ...)}
     * que impide a PostgreSQL inferir el tipo del bind.
     *
     * @param desde fecha inicial del periodo; {@code null} equivale a "sin limite".
     * @return el instante de inicio del periodo, o {@link #INSTANTE_MINIMO} si es nulo.
     */
    public static Instant desdeInclusivoOMinimo(LocalDate desde) {
        Instant limite = desdeInclusivo(desde);
        return limite != null ? limite : INSTANTE_MINIMO;
    }

    /**
     * Variante <em>null-safe</em> de {@link #hastaExclusivo(LocalDate)} que nunca
     * devuelve {@code null}: un {@code hasta} nulo se traduce a {@link #INSTANTE_MAXIMO}.
     * Preserva la semantica "sin limite superior" para datos reales y permite que los
     * repositorios comparen con un parametro SIEMPRE tipado ({@code o.createdAt < :hasta}),
     * sin el patron {@code (:hasta IS NULL OR ...)}.
     *
     * @param hasta fecha final del periodo (inclusiva en dias); {@code null} equivale a
     *              "sin limite".
     * @return el instante exclusivo de fin del periodo, o {@link #INSTANTE_MAXIMO} si es
     *         nulo.
     */
    public static Instant hastaExclusivoOMaximo(LocalDate hasta) {
        Instant limite = hastaExclusivo(hasta);
        return limite != null ? limite : INSTANTE_MAXIMO;
    }

    /**
     * Sustituye un limite inferior {@link Instant} nulo por {@link #INSTANTE_MINIMO}.
     * Util para adaptadores que ya trabajan con {@link Instant} y necesitan un bind
     * tipado no nulo.
     *
     * @param desde limite inferior; {@code null} equivale a "sin limite".
     * @return {@code desde}, o {@link #INSTANTE_MINIMO} si es nulo.
     */
    public static Instant instanteDesdeOMinimo(Instant desde) {
        return desde != null ? desde : INSTANTE_MINIMO;
    }

    /**
     * Sustituye un limite superior {@link Instant} nulo por {@link #INSTANTE_MAXIMO}.
     *
     * @param hasta limite superior; {@code null} equivale a "sin limite".
     * @return {@code hasta}, o {@link #INSTANTE_MAXIMO} si es nulo.
     */
    public static Instant instanteHastaOMaximo(Instant hasta) {
        return hasta != null ? hasta : INSTANTE_MAXIMO;
    }

    /**
     * Sustituye un limite inferior {@link LocalDate} nulo por {@link #FECHA_MINIMA}, para
     * agregaciones que comparan directamente contra columnas {@code date} (p. ej.
     * {@code fecha_programada}, {@code periodo_fin}, {@code fecha}). Preserva la semantica
     * "sin limite inferior" y mantiene el parametro tipado.
     *
     * @param desde limite inferior; {@code null} equivale a "sin limite".
     * @return {@code desde}, o {@link #FECHA_MINIMA} si es nulo.
     */
    public static LocalDate fechaDesdeOMinima(LocalDate desde) {
        return desde != null ? desde : FECHA_MINIMA;
    }

    /**
     * Sustituye un limite superior {@link LocalDate} nulo por {@link #FECHA_MAXIMA}, para
     * agregaciones que comparan directamente contra columnas {@code date}. Preserva la
     * semantica "sin limite superior" y mantiene el parametro tipado.
     *
     * @param hasta limite superior; {@code null} equivale a "sin limite".
     * @return {@code hasta}, o {@link #FECHA_MAXIMA} si es nulo.
     */
    public static LocalDate fechaHastaOMaxima(LocalDate hasta) {
        return hasta != null ? hasta : FECHA_MAXIMA;
    }
}

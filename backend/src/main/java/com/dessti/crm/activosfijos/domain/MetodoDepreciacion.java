package com.dessti.crm.activosfijos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Metodo de depreciacion de un {@link ActivoFijo} (Req 44.1). Cada constante
 * conoce su etiqueta ASCII en minusculas persistida en la columna
 * {@code activo_fijo.metodo_depreciacion} (VARCHAR con CHECK {@code IN
 * ('linea_recta','saldos_decrecientes')} de la migracion V37), coherente con la
 * convencion de etiquetas de V17/V33.
 *
 * <h2>Calculo del monto del periodo (funcion pura, Req 44.3)</h2>
 * <ul>
 *   <li>{@link #LINEA_RECTA}: distribuye la base depreciable
 *       {@code (costo - valorResidual)} de forma uniforme a lo largo de la vida
 *       util en meses: {@code (costo - valorResidual) / vidaUtilMeses}. El
 *       resultado se redondea a 2 decimales HALF_UP.</li>
 *   <li>{@link #SALDOS_DECRECIENTES}: metodo de doble saldo decreciente. Aplica una
 *       tasa {@code 2 / vidaUtilMeses} sobre el <em>valor en libros</em> del
 *       periodo {@code (costo - depreciacionAcumulada)}:
 *       {@code (costo - depreciacionAcumulada) * (2 / vidaUtilMeses)}. El resultado
 *       se redondea a 2 decimales HALF_UP.</li>
 * </ul>
 *
 * <p>El monto calculado aqui es el <strong>candidato</strong> del periodo; el
 * {@link ActivoFijo} lo <em>acota</em> (clamp) despues para que la depreciacion
 * acumulada nunca exceda la base depreciable {@code (costo - valorResidual)}
 * (invariante de acumulacion, ver {@link ActivoFijo#aplicarDepreciacion}).</p>
 */
public enum MetodoDepreciacion {

    /** Linea recta: base depreciable repartida uniformemente por la vida util. */
    LINEA_RECTA("linea_recta") {
        @Override
        public BigDecimal calcularMontoPeriodo(BigDecimal costo, BigDecimal valorResidual,
                                               BigDecimal depreciacionAcumulada, int vidaUtilMeses) {
            BigDecimal base = costo.subtract(valorResidual);
            return base.divide(BigDecimal.valueOf(vidaUtilMeses), 2, RoundingMode.HALF_UP);
        }
    },

    /** Doble saldo decreciente: tasa {@code 2/vidaUtilMeses} sobre el valor en libros. */
    SALDOS_DECRECIENTES("saldos_decrecientes") {
        @Override
        public BigDecimal calcularMontoPeriodo(BigDecimal costo, BigDecimal valorResidual,
                                               BigDecimal depreciacionAcumulada, int vidaUtilMeses) {
            BigDecimal valorEnLibros = costo.subtract(depreciacionAcumulada);
            BigDecimal tasa = BigDecimal.valueOf(2)
                    .divide(BigDecimal.valueOf(vidaUtilMeses), 10, RoundingMode.HALF_UP);
            return valorEnLibros.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        }
    };

    private final String valorBd;

    MetodoDepreciacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Calcula el monto <strong>candidato</strong> de depreciacion del proximo
     * periodo segun el metodo (Req 44.3). No aplica el clamp de la base
     * depreciable: eso lo hace {@link ActivoFijo#aplicarDepreciacion}.
     *
     * @param costo                  costo de adquisicion (positivo).
     * @param valorResidual          valor residual estimado (en {@code [0, costo]}).
     * @param depreciacionAcumulada  depreciacion acumulada hasta el periodo previo.
     * @param vidaUtilMeses          vida util en meses (positiva).
     * @return el monto candidato del periodo, con escala 2 (HALF_UP).
     */
    public abstract BigDecimal calcularMontoPeriodo(BigDecimal costo, BigDecimal valorResidual,
                                                    BigDecimal depreciacionAcumulada,
                                                    int vidaUtilMeses);

    /**
     * Etiqueta ASCII persistida en la columna {@code activo_fijo.metodo_depreciacion}
     * (coincide con el CHECK de V37).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "linea_recta"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve el metodo a partir de su etiqueta de base de datos. La comparacion
     * es insensible a mayusculas y recorta espacios.
     *
     * @param valor etiqueta persistida ({@code linea_recta}, {@code saldos_decrecientes}).
     * @return el metodo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static MetodoDepreciacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El metodo de depreciacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (MetodoDepreciacion metodo : values()) {
            if (metodo.valorBd.equals(normalizado)) {
                return metodo;
            }
        }
        throw new IllegalArgumentException("Metodo de depreciacion desconocido: " + valor);
    }
}

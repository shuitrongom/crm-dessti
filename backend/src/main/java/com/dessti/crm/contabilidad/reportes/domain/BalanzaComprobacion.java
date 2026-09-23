package com.dessti.crm.contabilidad.reportes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Valor de dominio <strong>puro e inmutable</strong> de la <strong>balanza de
 * comprobacion</strong> de un periodo, derivada de los {@link SaldoCuenta saldos por
 * cuenta} (Req 47.1). Es el resultado de {@link EstadosFinancieros#balanzaDeComprobacion}.
 *
 * <p>La balanza lista, por cada Cuenta_Contable con movimiento en el periodo, el
 * total de sus cargos y de sus abonos, y expone los grandes totales
 * {@link #totalCargos()} y {@link #totalAbonos()}. Como cada Poliza_Contable esta
 * balanceada (Property 16, Req 38.3), la balanza cumple
 * <strong>{@code total_cargos == total_abonos}</strong> ({@link #cuadra()}).</p>
 *
 * <p>Todos los importes se manejan a escala {@value #ESCALA_MONETARIA} con redondeo
 * {@code HALF_UP}.</p>
 *
 * @param renglones  detalle por cuenta (cargos/abonos del periodo).
 * @param totalCargos suma de los cargos de todas las cuentas, escala 2.
 * @param totalAbonos suma de los abonos de todas las cuentas, escala 2.
 */
public record BalanzaComprobacion(
        List<Renglon> renglones,
        BigDecimal totalCargos,
        BigDecimal totalAbonos) {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    /**
     * Normaliza los importes a escala 2 (HALF_UP) y protege la lista de renglones.
     */
    public BalanzaComprobacion {
        renglones = (renglones == null) ? List.of() : List.copyOf(renglones);
        totalCargos = normalizar(totalCargos);
        totalAbonos = normalizar(totalAbonos);
    }

    /**
     * Indica si la balanza <strong>cuadra</strong>: la suma de todos los cargos es
     * igual a la suma de todos los abonos (Req 47.1), consecuencia del balance de
     * cada Poliza_Contable (Property 16).
     *
     * @return {@code true} si {@code total_cargos == total_abonos}.
     */
    public boolean cuadra() {
        return totalCargos.compareTo(totalAbonos) == 0;
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        BigDecimal valor = (importe == null) ? BigDecimal.ZERO : importe;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Renglon de la balanza de comprobacion: los totales de cargos y abonos del
     * periodo de una Cuenta_Contable (Req 47.1).
     *
     * @param cuentaId identificador de la Cuenta_Contable.
     * @param codigo   codigo de la Cuenta_Contable.
     * @param nombre   nombre de la Cuenta_Contable.
     * @param cargos   total de cargos del periodo, escala 2.
     * @param abonos   total de abonos del periodo, escala 2.
     */
    public record Renglon(
            UUID cuentaId,
            String codigo,
            String nombre,
            BigDecimal cargos,
            BigDecimal abonos) {

        /**
         * Normaliza los importes del renglon a escala 2 (HALF_UP).
         */
        public Renglon {
            cargos = normalizar(cargos);
            abonos = normalizar(abonos);
        }
    }
}

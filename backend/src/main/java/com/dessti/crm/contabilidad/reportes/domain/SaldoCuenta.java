package com.dessti.crm.contabilidad.reportes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.NaturalezaCuenta;
import com.dessti.crm.contabilidad.polizas.domain.TipoCuentaContable;

/**
 * Valor de dominio <strong>puro e inmutable</strong> que representa el saldo neto de
 * una {@link com.dessti.crm.contabilidad.polizas.domain.CuentaContable Cuenta_Contable}
 * de un periodo, ya derivado de sus renglones ({@code movimiento_poliza}) y
 * clasificado por su {@link TipoCuentaContable tipo} contable (Req 47.1, 47.3).
 *
 * <p>Es la unidad de entrada de las funciones puras de {@link EstadosFinancieros}
 * (balance general, estado de resultados y balanza de comprobacion): un conjunto de
 * {@code SaldoCuenta} —uno por cuenta con movimiento en el periodo— es suficiente
 * para derivar los estados financieros sin base de datos ni framework, de modo que
 * la <strong>Property 17</strong> (ecuacion contable) se verifica directamente sobre
 * este modelo.</p>
 *
 * <h2>Derivacion del saldo (convencion documentada, Req 47.3)</h2>
 * <p>El saldo de una cuenta se obtiene de la suma de sus cargos y abonos del periodo
 * segun su {@link NaturalezaCuenta naturaleza}:</p>
 * <ul>
 *   <li><strong>Deudora</strong> (tipicamente activo y gasto): {@code saldo = cargos - abonos}.</li>
 *   <li><strong>Acreedora</strong> (tipicamente pasivo, capital e ingreso): {@code saldo = abonos - cargos}.</li>
 * </ul>
 * <p>Asi el saldo queda expresado en el signo natural de la cuenta (normalmente
 * positivo), y {@link #cargos()}/{@link #abonos()} se conservan para armar la
 * balanza de comprobacion (Req 47.1). Todos los importes se manejan a escala
 * {@value #ESCALA_MONETARIA} con redondeo {@code HALF_UP}, coherente con
 * {@code NUMERIC(18,2)}.</p>
 *
 * @param cuentaId   identificador de la Cuenta_Contable.
 * @param codigo     codigo de la Cuenta_Contable (para el detalle de la balanza).
 * @param nombre     nombre de la Cuenta_Contable (para el detalle de la balanza).
 * @param tipo       tipo contable (activo, pasivo, capital, ingreso, gasto).
 * @param naturaleza naturaleza del saldo (deudora o acreedora).
 * @param cargos     suma de los cargos del periodo a la cuenta; no negativa, escala 2.
 * @param abonos     suma de los abonos del periodo a la cuenta; no negativa, escala 2.
 */
public record SaldoCuenta(
        UUID cuentaId,
        String codigo,
        String nombre,
        TipoCuentaContable tipo,
        NaturalezaCuenta naturaleza,
        BigDecimal cargos,
        BigDecimal abonos) {

    /** Escala monetaria coherente con NUMERIC(18,2) de las migraciones contables. */
    public static final int ESCALA_MONETARIA = 2;

    /**
     * Compacta la validacion minima y normaliza los importes a escala 2 (HALF_UP)
     * para garantizar comparaciones exactas por {@code compareTo}.
     */
    public SaldoCuenta {
        if (tipo == null) {
            throw new IllegalArgumentException("El SaldoCuenta debe indicar el tipo contable.");
        }
        if (naturaleza == null) {
            throw new IllegalArgumentException("El SaldoCuenta debe indicar la naturaleza.");
        }
        cargos = normalizar(cargos);
        abonos = normalizar(abonos);
    }

    /**
     * Crea un {@code SaldoCuenta} a partir de los totales de cargos y abonos del
     * periodo de una cuenta.
     *
     * @param cuentaId   identificador de la cuenta.
     * @param codigo     codigo de la cuenta.
     * @param nombre     nombre de la cuenta.
     * @param tipo       tipo contable.
     * @param naturaleza naturaleza del saldo.
     * @param cargos     suma de cargos del periodo.
     * @param abonos     suma de abonos del periodo.
     * @return el {@code SaldoCuenta} inmutable con importes normalizados a escala 2.
     */
    public static SaldoCuenta de(UUID cuentaId, String codigo, String nombre,
                                 TipoCuentaContable tipo, NaturalezaCuenta naturaleza,
                                 BigDecimal cargos, BigDecimal abonos) {
        return new SaldoCuenta(cuentaId, codigo, nombre, tipo, naturaleza, cargos, abonos);
    }

    /**
     * Saldo neto de la cuenta expresado en el signo natural de su naturaleza
     * (convencion documentada en la cabecera de esta clase):
     * {@code deudora -> cargos - abonos}; {@code acreedora -> abonos - cargos}.
     *
     * @return el saldo neto de la cuenta, escala 2.
     */
    public BigDecimal saldo() {
        BigDecimal saldo = (naturaleza == NaturalezaCuenta.DEUDORA)
                ? cargos.subtract(abonos)
                : abonos.subtract(cargos);
        return saldo.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizar(BigDecimal importe) {
        BigDecimal valor = (importe == null)
                ? BigDecimal.ZERO
                : importe;
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}

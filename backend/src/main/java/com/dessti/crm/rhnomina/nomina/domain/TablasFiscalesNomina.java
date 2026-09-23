package com.dessti.crm.rhnomina.nomina.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Tablas fiscales VERSIONADAS de la nomina mexicana (Req 41.2): tarifa de ISR
 * mensual (Art. 96 de la Ley del ISR), tabla del subsidio al empleo mensual, y
 * las tasas simplificadas de la cuota obrera del IMSS y del descuento de Infonavit.
 * Componente de dominio <strong>puro</strong> (sin Spring/JPA) que el motor de
 * calculo {@link CalculoNomina} consume, y que las pruebas unitarias de tablas
 * fiscales (tarea 35.3) ejercitan con montos verificados a mano.
 *
 * <h2>Vigencia (revisar anualmente)</h2>
 * <p>Los valores aqui codificados corresponden a la <strong>tarifa mensual vigente
 * 2024/2026</strong> (Anexo 8 de la RMF, Art. 96 LISR) y a una representacion
 * <strong>fiel pero mantenible</strong> de las cuotas obrero-patronales. Deben
 * <strong>revisarse cada ejercicio</strong> cuando el SAT publique la actualizacion
 * por inflacion (INPC). La arquitectura permite actualizar los tramos aqui
 * <em>sin tocar</em> el motor de calculo: {@link CalculoNomina} depende solo de las
 * funciones publicas de esta clase.</p>
 *
 * <h2>Simplificaciones documentadas (Req 41.2)</h2>
 * <ul>
 *   <li><strong>IMSS (cuota obrera):</strong> el IMSS real se compone de varios
 *       ramos (enfermedad y maternidad, invalidez y vida, cesantia y vejez, etc.)
 *       con topes en UMA y prestaciones en especie/dinero. Aqui se aplica una
 *       <strong>tasa obrera unica</strong> {@link #TASA_IMSS_OBRERA} sobre el
 *       salario base del periodo, como aproximacion documentada y facil de
 *       mantener. Refinar los ramos no cambiaria el esquema de datos ni el motor.</li>
 *   <li><strong>Infonavit:</strong> solo aplica si el Empleado tiene un credito
 *       Infonavit vigente; el descuento se modela como una <strong>tasa</strong>
 *       sobre el salario base ({@code 0} por defecto cuando no hay credito).</li>
 *   <li><strong>Subsidio al empleo:</strong> tabla mensual simplificada: si el
 *       ingreso gravable no excede {@link #TOPE_SUBSIDIO_MENSUAL}, corresponde el
 *       subsidio {@link #SUBSIDIO_MENSUAL}; por encima de ese tope, el subsidio es
 *       cero. Es la regla dominante de la tabla oficial de subsidio.</li>
 * </ul>
 *
 * <p>Toda la aritmetica monetaria usa escala {@value #ESCALA_MONETARIA} con
 * redondeo half-up, coherente con {@code CalculoFiscalCfdi} y el resto del sistema
 * (NUMERIC(18,2)).</p>
 */
public final class TablasFiscalesNomina {

    /** Escala monetaria del sistema (2 decimales), coherente con NUMERIC(18,2). */
    public static final int ESCALA_MONETARIA = 2;

    /**
     * Tasa obrera del IMSS (SIMPLIFICADA, Req 41.2): tasa unica aplicada sobre el
     * salario base del periodo como aproximacion de la suma de cuotas obreras.
     * Vigente 2024/2026 como valor de referencia; revisar anualmente.
     */
    public static final BigDecimal TASA_IMSS_OBRERA = new BigDecimal("0.025");

    /**
     * Tasa de descuento de Infonavit cuando el Empleado NO tiene credito vigente
     * (por defecto 0). Cuando si lo tiene, la tasa la aporta la capa de aplicacion.
     */
    public static final BigDecimal TASA_INFONAVIT_SIN_CREDITO = BigDecimal.ZERO;

    /**
     * Ingreso gravable mensual maximo con derecho a subsidio al empleo (tabla
     * mensual simplificada, vigente 2024/2026; revisar anualmente).
     */
    public static final BigDecimal TOPE_SUBSIDIO_MENSUAL = new BigDecimal("7382.34");

    /**
     * Importe mensual del subsidio al empleo cuando el ingreso gravable no excede
     * {@link #TOPE_SUBSIDIO_MENSUAL} (tabla mensual simplificada, vigente
     * 2024/2026; revisar anualmente).
     */
    public static final BigDecimal SUBSIDIO_MENSUAL = new BigDecimal("475.00");

    /**
     * Tarifa mensual de ISR del Art. 96 LISR <strong>vigente 2024/2026</strong>
     * (Anexo 8 RMF): 11 tramos con limite inferior, cuota fija y porcentaje sobre
     * el excedente del limite inferior. Revisar anualmente cuando el SAT actualice
     * por inflacion. Inmutable: {@link List#of(Object...)} produce una vista no
     * modificable y {@link TramoIsr} es un record inmutable.
     */
    public static final List<TramoIsr> TARIFA_ISR_MENSUAL = List.of(
            new TramoIsr(new BigDecimal("0.01"),        new BigDecimal("0.00"),       new BigDecimal("0.0192")),
            new TramoIsr(new BigDecimal("746.05"),      new BigDecimal("14.32"),      new BigDecimal("0.0640")),
            new TramoIsr(new BigDecimal("6332.06"),     new BigDecimal("371.83"),     new BigDecimal("0.1088")),
            new TramoIsr(new BigDecimal("11128.02"),    new BigDecimal("893.63"),     new BigDecimal("0.1600")),
            new TramoIsr(new BigDecimal("12935.83"),    new BigDecimal("1182.88"),    new BigDecimal("0.1792")),
            new TramoIsr(new BigDecimal("15487.72"),    new BigDecimal("1640.18"),    new BigDecimal("0.2136")),
            new TramoIsr(new BigDecimal("31236.50"),    new BigDecimal("5004.12"),    new BigDecimal("0.2352")),
            new TramoIsr(new BigDecimal("49233.01"),    new BigDecimal("9236.89"),    new BigDecimal("0.3000")),
            new TramoIsr(new BigDecimal("93993.91"),    new BigDecimal("22665.17"),   new BigDecimal("0.3200")),
            new TramoIsr(new BigDecimal("125325.21"),   new BigDecimal("32691.18"),   new BigDecimal("0.3400")),
            new TramoIsr(new BigDecimal("375975.62"),   new BigDecimal("117912.32"),  new BigDecimal("0.3500")));

    private TablasFiscalesNomina() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Calcula el ISR mensual de una base gravable segun la tarifa del Art. 96 LISR
     * (Req 41.2): ubica el tramo cuyo limite inferior es el mayor no superior a la
     * base y aplica {@code ISR = cuota_fija + (base - limite_inferior) * porcentaje},
     * redondeado a escala 2 (half-up). Una base de cero (o dentro del primer tramo)
     * produce el ISR del primer tramo.
     *
     * @param baseGravable base gravable mensual; obligatoria y no negativa.
     * @return el ISR mensual a escala 2 (half-up).
     * @throws ReglaNegocioException si la base es nula o negativa (422).
     */
    public static BigDecimal isrMensual(BigDecimal baseGravable) {
        if (baseGravable == null) {
            throw new ReglaNegocioException("La base gravable del ISR es obligatoria.");
        }
        if (baseGravable.signum() < 0) {
            throw new ReglaNegocioException("La base gravable del ISR no puede ser negativa.");
        }
        TramoIsr tramo = tramoAplicable(baseGravable);
        BigDecimal excedente = baseGravable.subtract(tramo.limiteInferior());
        BigDecimal impuestoMarginal = excedente.multiply(tramo.porcentajeExcedente());
        return redondear(tramo.cuotaFija().add(impuestoMarginal));
    }

    /**
     * Determina el subsidio al empleo mensual que corresponde a un ingreso gravable
     * segun la tabla mensual simplificada (Req 41.1, 41.2): {@link #SUBSIDIO_MENSUAL}
     * si el ingreso no excede {@link #TOPE_SUBSIDIO_MENSUAL}; cero en caso contrario.
     *
     * @param ingresoGravable ingreso gravable mensual; obligatorio y no negativo.
     * @return el subsidio al empleo a escala 2 (half-up).
     * @throws ReglaNegocioException si el ingreso es nulo o negativo (422).
     */
    public static BigDecimal subsidioAlEmpleoMensual(BigDecimal ingresoGravable) {
        if (ingresoGravable == null) {
            throw new ReglaNegocioException("El ingreso gravable del subsidio es obligatorio.");
        }
        if (ingresoGravable.signum() < 0) {
            throw new ReglaNegocioException("El ingreso gravable del subsidio no puede ser negativo.");
        }
        BigDecimal subsidio = (ingresoGravable.compareTo(TOPE_SUBSIDIO_MENSUAL) <= 0)
                ? SUBSIDIO_MENSUAL
                : BigDecimal.ZERO;
        return redondear(subsidio);
    }

    /**
     * Calcula la cuota obrera del IMSS (SIMPLIFICADA, Req 41.2): {@code round(base *
     * TASA_IMSS_OBRERA, 2)} half-up.
     *
     * @param salarioBase salario base del periodo; obligatorio y no negativo.
     * @return la cuota obrera del IMSS a escala 2 (half-up).
     * @throws ReglaNegocioException si el salario base es nulo o negativo (422).
     */
    public static BigDecimal cuotaImssObrera(BigDecimal salarioBase) {
        return redondear(exigirNoNegativo(salarioBase, "El salario base del IMSS")
                .multiply(TASA_IMSS_OBRERA));
    }

    /**
     * Calcula el descuento de Infonavit (Req 41.2): {@code round(base * tasa, 2)}
     * half-up. La tasa es {@code 0} cuando el Empleado no tiene credito Infonavit.
     *
     * @param salarioBase    salario base del periodo; obligatorio y no negativo.
     * @param tasaInfonavit  tasa de descuento en {@code [0, 1]}; obligatoria
     *                       ({@link #TASA_INFONAVIT_SIN_CREDITO} si no aplica).
     * @return el descuento de Infonavit a escala 2 (half-up).
     * @throws ReglaNegocioException si el salario base es negativo o la tasa esta
     *         fuera de {@code [0, 1]} (422).
     */
    public static BigDecimal descuentoInfonavit(BigDecimal salarioBase, BigDecimal tasaInfonavit) {
        BigDecimal base = exigirNoNegativo(salarioBase, "El salario base de Infonavit");
        if (tasaInfonavit == null) {
            throw new ReglaNegocioException("La tasa de Infonavit es obligatoria (0 si no aplica).");
        }
        if (tasaInfonavit.signum() < 0 || tasaInfonavit.compareTo(BigDecimal.ONE) > 0) {
            throw new ReglaNegocioException("La tasa de Infonavit debe estar entre 0 y 1.");
        }
        return redondear(base.multiply(tasaInfonavit));
    }

    /**
     * Normaliza un importe monetario a escala 2 con redondeo al valor mas cercano
     * (half-up), coherente con el resto de la aritmetica monetaria del sistema.
     *
     * @param valor importe a normalizar; obligatorio.
     * @return el importe a escala 2 (half-up).
     */
    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private static TramoIsr tramoAplicable(BigDecimal baseGravable) {
        TramoIsr aplicable = TARIFA_ISR_MENSUAL.get(0);
        for (TramoIsr tramo : TARIFA_ISR_MENSUAL) {
            if (baseGravable.compareTo(tramo.limiteInferior()) >= 0) {
                aplicable = tramo;
            } else {
                break;
            }
        }
        return aplicable;
    }

    private static BigDecimal exigirNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new ReglaNegocioException(etiqueta + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new ReglaNegocioException(etiqueta + " no puede ser negativo.");
        }
        return valor;
    }

    /**
     * Tramo de la tarifa mensual de ISR (Art. 96 LISR): limite inferior del tramo,
     * cuota fija y porcentaje aplicable sobre el excedente del limite inferior.
     * Record inmutable (Req 41.2).
     *
     * @param limiteInferior      limite inferior del tramo (inclusive).
     * @param cuotaFija           cuota fija del tramo.
     * @param porcentajeExcedente porcentaje sobre el excedente del limite inferior
     *                            (por ejemplo {@code 0.0192} para 1.92%).
     */
    public record TramoIsr(
            BigDecimal limiteInferior,
            BigDecimal cuotaFija,
            BigDecimal porcentajeExcedente) {
    }
}

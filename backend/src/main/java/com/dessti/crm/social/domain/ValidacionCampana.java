package com.dessti.crm.social.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion <strong>puras</strong> del presupuesto y el periodo de
 * una {@link CampanaPublicitaria} (Req 65.7, 65.8). Centraliza las reglas de rango
 * monetario y de coherencia de fechas para evitar duplicarlas y garantizar un
 * comportamiento consistente y verificable (<strong>Property 39</strong>). Sigue
 * el mismo estilo que {@code CotizacionValidaciones}.
 *
 * <h2>Reglas (Req 65.7, 65.8)</h2>
 * <ul>
 *   <li><strong>Presupuesto:</strong> dentro del rango cerrado
 *       {@code [0.01, 999,999,999.99]}, escala 2 (redondeo al valor mas cercano,
 *       HALF_UP). Fuera de rango se rechaza (Property 39). El redondeo se aplica
 *       <em>antes</em> de la comprobacion de rango, con identica semantica a
 *       {@code CotizacionValidaciones.validarPrecioUnitario}.</li>
 *   <li><strong>Periodo:</strong> {@code fecha_fin >= fecha_inicio}. Un periodo con
 *       {@code fecha_fin} anterior a {@code fecha_inicio} se rechaza (Property 39).</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), nombrando el dato invalido, coherente
 * con el resto del dominio (Req 65.8).</p>
 */
public final class ValidacionCampana {

    /** Escala monetaria del sistema (2 decimales). */
    public static final int ESCALA_MONETARIA = 2;

    /** Presupuesto minimo aceptado (Req 65.7, 65.8). */
    public static final BigDecimal PRESUPUESTO_MINIMO = new BigDecimal("0.01");

    /** Presupuesto maximo aceptado (Req 65.7, 65.8). */
    public static final BigDecimal PRESUPUESTO_MAXIMO = new BigDecimal("999999999.99");

    private ValidacionCampana() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida de forma conjunta el presupuesto y el periodo de una
     * Campaña_Publicitaria (Req 65.7, 65.8; Property 39). Comprueba primero el
     * presupuesto (rango tras redondeo HALF_UP a escala 2) y despues la coherencia
     * del periodo ({@code fecha_fin >= fecha_inicio}). Es una funcion pura: mismo
     * resultado para las mismas entradas, sin efectos secundarios.
     *
     * @param presupuesto presupuesto de la campaña; obligatorio.
     * @param fechaInicio fecha de inicio del periodo; obligatoria.
     * @param fechaFin    fecha de fin del periodo; obligatoria.
     * @return el presupuesto normalizado a escala 2 (HALF_UP) si todo es valido.
     * @throws ReglaNegocioException si el presupuesto esta fuera de rango o el
     *         periodo es incoherente (422, Req 65.8).
     */
    public static BigDecimal validar(BigDecimal presupuesto, LocalDate fechaInicio, LocalDate fechaFin) {
        BigDecimal presupuestoNormalizado = validarPresupuesto(presupuesto);
        validarPeriodo(fechaInicio, fechaFin);
        return presupuestoNormalizado;
    }

    /**
     * Valida el presupuesto y lo normaliza a escala 2 (HALF_UP). Debe estar dentro
     * del rango cerrado {@code [0.01, 999,999,999.99]} (Req 65.7, 65.8; Property 39).
     *
     * <p>El redondeo se aplica <em>antes</em> de la comprobacion de rango, de modo
     * que un valor con mas de 2 decimales se evalua por su representacion monetaria
     * efectiva (por ejemplo, {@code 0.004} redondea a {@code 0.00} y se rechaza).</p>
     *
     * @param presupuesto presupuesto a validar; obligatorio.
     * @return el presupuesto normalizado a escala 2.
     * @throws ReglaNegocioException si es nulo o queda fuera del rango (422).
     */
    public static BigDecimal validarPresupuesto(BigDecimal presupuesto) {
        if (presupuesto == null) {
            throw new ReglaNegocioException("El presupuesto de la Campaña_Publicitaria es obligatorio.");
        }
        BigDecimal normalizado = presupuesto.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (normalizado.compareTo(PRESUPUESTO_MINIMO) < 0 || normalizado.compareTo(PRESUPUESTO_MAXIMO) > 0) {
            throw new ReglaNegocioException(
                    "El presupuesto " + normalizado.toPlainString()
                            + " esta fuera del rango permitido [" + PRESUPUESTO_MINIMO.toPlainString()
                            + ", " + PRESUPUESTO_MAXIMO.toPlainString() + "].");
        }
        return normalizado;
    }

    /**
     * Valida la coherencia del periodo: ambas fechas son obligatorias y
     * {@code fecha_fin} no puede ser anterior a {@code fecha_inicio} (Req 65.7,
     * 65.8; Property 39).
     *
     * @param fechaInicio fecha de inicio; obligatoria.
     * @param fechaFin    fecha de fin; obligatoria.
     * @throws ReglaNegocioException si falta alguna fecha o el periodo es
     *         incoherente ({@code fecha_fin < fecha_inicio}) (422).
     */
    public static void validarPeriodo(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null) {
            throw new ReglaNegocioException("La fecha de inicio de la Campaña_Publicitaria es obligatoria.");
        }
        if (fechaFin == null) {
            throw new ReglaNegocioException("La fecha de fin de la Campaña_Publicitaria es obligatoria.");
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new ReglaNegocioException(
                    "La fecha de fin " + fechaFin + " no puede ser anterior a la fecha de inicio "
                            + fechaInicio + ".");
        }
    }
}

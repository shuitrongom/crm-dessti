package com.dessti.crm.comercial.cliente.domain;

import java.math.BigDecimal;

/**
 * Portador inmutable de las CONDICIONES DE CREDITO del {@link Cliente} (Req 5,
 * V90): dias de credito y limite de credito. Las condiciones de credito son un
 * concepto financiero cohesivo, distinto de los datos descriptivos de
 * {@link DatosBasicosCliente}, por lo que se agrupan en su propio value object
 * para no alargar las firmas de {@link Cliente#crear} / {@link Cliente#actualizar}.
 *
 * <p>A diferencia de {@link DatosBasicosCliente} (donde todos los campos son
 * opcionales y {@code null} significa "sin dato"), {@code dias_credito} tiene
 * semantica de negocio (contado vs credito) y es {@code NOT NULL DEFAULT 0} en la
 * base. Por eso {@code diasCredito} se declara {@link Integer} (nullable) solo
 * para que el llamador pueda transportar "no especificado"; la entidad
 * {@link Cliente} lo normaliza a {@code 0} cuando llega {@code null}. La
 * validacion de rango (0..365) y de no-negatividad del limite la aplica
 * {@link Cliente}.</p>
 *
 * @param diasCredito   dias de credito (0..365); {@code null} => la entidad aplica
 *                      {@code 0} (contado). 0 = CONTADO (PUE), >0 = CREDITO (PPD).
 * @param limiteCredito limite de credito en MXN (escala 2, >= 0); {@code null} =
 *                      sin limite definido.
 */
public record CondicionesCredito(Integer diasCredito, BigDecimal limiteCredito) {

    /** Contado sin limite: dias_credito = 0, limite_credito = null. */
    public static final CondicionesCredito CONTADO = new CondicionesCredito(0, null);

    /**
     * Devuelve la instancia recibida o {@link #CONTADO} si es {@code null}, para
     * que la entidad pueda tratar de forma uniforme la ausencia de condiciones de
     * credito (crear/actualizar sin especificarlas).
     *
     * @param condiciones condiciones de credito; puede ser {@code null}.
     * @return {@code condiciones} si no es nulo; {@link #CONTADO} en otro caso.
     */
    public static CondicionesCredito orContado(CondicionesCredito condiciones) {
        return (condiciones == null) ? CONTADO : condiciones;
    }
}

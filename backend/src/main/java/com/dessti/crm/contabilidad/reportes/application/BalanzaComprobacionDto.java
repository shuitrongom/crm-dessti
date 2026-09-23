package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.reportes.domain.BalanzaComprobacion;

/**
 * DTO de salida de la <strong>balanza de comprobacion</strong> de un periodo
 * (Req 47.1), distinto del valor de dominio. Presenta el detalle por cuenta y los
 * grandes totales de cargos y abonos, junto con la bandera {@link #cuadra} que indica
 * si {@code total_cargos == total_abonos}.
 *
 * @param desde       inicio del periodo (inclusivo); {@code null} si no se acoto.
 * @param hasta       fin del periodo (inclusivo); {@code null} si no se acoto.
 * @param renglones   detalle por cuenta (cargos/abonos del periodo).
 * @param totalCargos suma de los cargos de todas las cuentas.
 * @param totalAbonos suma de los abonos de todas las cuentas.
 * @param cuadra      {@code true} si {@code total_cargos == total_abonos}.
 */
public record BalanzaComprobacionDto(
        LocalDate desde,
        LocalDate hasta,
        List<RenglonDto> renglones,
        BigDecimal totalCargos,
        BigDecimal totalAbonos,
        boolean cuadra) {

    /**
     * Proyecta una {@link BalanzaComprobacion} de dominio a su DTO de salida,
     * anexando el periodo consultado.
     *
     * @param balanza balanza de comprobacion de dominio.
     * @param desde   inicio del periodo (inclusivo); puede ser {@code null}.
     * @param hasta   fin del periodo (inclusivo); puede ser {@code null}.
     * @return el DTO correspondiente.
     */
    public static BalanzaComprobacionDto de(BalanzaComprobacion balanza,
                                            LocalDate desde, LocalDate hasta) {
        List<RenglonDto> renglones = balanza.renglones().stream()
                .map(RenglonDto::de)
                .toList();
        return new BalanzaComprobacionDto(
                desde,
                hasta,
                renglones,
                balanza.totalCargos(),
                balanza.totalAbonos(),
                balanza.cuadra());
    }

    /**
     * Renglon del DTO de la balanza: los totales de cargos y abonos del periodo de
     * una Cuenta_Contable (Req 47.1).
     *
     * @param cuentaId identificador de la Cuenta_Contable.
     * @param codigo   codigo de la Cuenta_Contable.
     * @param nombre   nombre de la Cuenta_Contable.
     * @param cargos   total de cargos del periodo.
     * @param abonos   total de abonos del periodo.
     */
    public record RenglonDto(
            UUID cuentaId,
            String codigo,
            String nombre,
            BigDecimal cargos,
            BigDecimal abonos) {

        /**
         * Proyecta un renglon de dominio a su DTO de salida.
         *
         * @param renglon renglon de la balanza de dominio.
         * @return el DTO correspondiente.
         */
        public static RenglonDto de(BalanzaComprobacion.Renglon renglon) {
            return new RenglonDto(
                    renglon.cuentaId(),
                    renglon.codigo(),
                    renglon.nombre(),
                    renglon.cargos(),
                    renglon.abonos());
        }
    }
}

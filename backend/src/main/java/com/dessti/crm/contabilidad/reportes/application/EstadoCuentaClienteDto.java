package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar;

/**
 * DTO de salida del <strong>estado de cuenta por Cliente</strong> (Req 39.1): el
 * detalle de las Cuentas_Por_Cobrar del Cliente en un periodo, con su total original
 * y su saldo pendiente, y los grandes totales facturado y por cobrar. Es una
 * agregacion de solo lectura (Req 39.2).
 *
 * @param clienteId       Cliente del estado de cuenta.
 * @param desde           inicio del periodo (inclusivo); {@code null} si no se acoto.
 * @param hasta           fin del periodo (inclusivo); {@code null} si no se acoto.
 * @param renglones       detalle de las CxC del Cliente en el periodo.
 * @param totalFacturado  suma de los totales originales de las CxC del periodo.
 * @param saldoPendiente  suma de los saldos pendientes de las CxC del periodo.
 */
public record EstadoCuentaClienteDto(
        UUID clienteId,
        LocalDate desde,
        LocalDate hasta,
        List<RenglonDto> renglones,
        BigDecimal totalFacturado,
        BigDecimal saldoPendiente) {

    /** Escala monetaria coherente con NUMERIC(18,2). */
    private static final int ESCALA_MONETARIA = 2;

    /**
     * Construye el estado de cuenta de un Cliente a partir de sus CxC del periodo,
     * acumulando los grandes totales (solo lectura).
     *
     * @param clienteId Cliente del estado de cuenta.
     * @param cuentas   CxC del Cliente en el periodo, ordenadas por fecha de emision.
     * @param desde     inicio del periodo (inclusivo); puede ser {@code null}.
     * @param hasta     fin del periodo (inclusivo); puede ser {@code null}.
     * @return el DTO del estado de cuenta del Cliente.
     */
    public static EstadoCuentaClienteDto de(UUID clienteId, List<CuentaPorCobrar> cuentas,
                                            LocalDate desde, LocalDate hasta) {
        BigDecimal totalFacturado = cero();
        BigDecimal saldoPendiente = cero();
        List<RenglonDto> renglones = new java.util.ArrayList<>();
        for (CuentaPorCobrar cxc : cuentas) {
            renglones.add(RenglonDto.de(cxc));
            totalFacturado = totalFacturado.add(cxc.getTotal());
            saldoPendiente = saldoPendiente.add(cxc.getSaldo());
        }
        return new EstadoCuentaClienteDto(
                clienteId, desde, hasta, List.copyOf(renglones),
                totalFacturado.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP),
                saldoPendiente.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP));
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Renglon del estado de cuenta: una Cuenta_Por_Cobrar del Cliente con su total,
     * saldo, estado y fechas (Req 39.1).
     *
     * @param cxcId            identificador de la CxC.
     * @param facturaId        Factura de origen.
     * @param total            total original de la Factura.
     * @param saldo            saldo pendiente de cobro.
     * @param estado           etiqueta del estado de la CxC.
     * @param fechaEmision     fecha/hora de emision (UTC).
     * @param fechaVencimiento fecha de vencimiento; {@code null} si no aplica.
     */
    public record RenglonDto(
            UUID cxcId,
            UUID facturaId,
            BigDecimal total,
            BigDecimal saldo,
            String estado,
            Instant fechaEmision,
            LocalDate fechaVencimiento) {

        /**
         * Proyecta una {@link CuentaPorCobrar} a su renglon del estado de cuenta.
         *
         * @param cxc Cuenta_Por_Cobrar a proyectar.
         * @return el renglon correspondiente.
         */
        public static RenglonDto de(CuentaPorCobrar cxc) {
            return new RenglonDto(
                    cxc.getId(),
                    cxc.getFacturaId(),
                    cxc.getTotal(),
                    cxc.getSaldo(),
                    cxc.getEstado().valorBd(),
                    cxc.getFechaEmision(),
                    cxc.getFechaVencimiento());
        }
    }
}

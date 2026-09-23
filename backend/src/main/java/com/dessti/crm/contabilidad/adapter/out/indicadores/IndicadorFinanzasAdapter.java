package com.dessti.crm.contabilidad.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.CuentaPorCobrarRepository;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorFinanzasPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorFinanzasPort}
 * (Req 22.1, 48.1) del area finanzas/facturacion. Deriva la facturacion timbrada del
 * periodo y el IVA trasladado del modulo de facturacion, y las Cuentas_Por_Cobrar
 * vencidas del modulo de contabilidad, agregando unicamente con consultas {@code SUM}/
 * {@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorFinanzasVacio}. El aislamiento por tenant (Req 23) lo garantizan
 * el filtro global de Hibernate y la RLS de PostgreSQL activos sobre los repositorios de
 * ambos modulos; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorFinanzasAdapter implements IndicadorFinanzasPort {

    private final FacturaRepository facturaRepository;
    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;

    /**
     * Crea el adaptador con los repositorios de solo lectura de facturacion y CxC.
     *
     * @param facturaRepository         repositorio de Facturas (CFDI).
     * @param cuentaPorCobrarRepository repositorio de Cuentas_Por_Cobrar.
     */
    public IndicadorFinanzasAdapter(FacturaRepository facturaRepository,
                                    CuentaPorCobrarRepository cuentaPorCobrarRepository) {
        this.facturaRepository = facturaRepository;
        this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas no nulas [EPOCH, 9999): preservan la semantica "sin limite" y evitan el
        // fallo de inferencia de tipo de PostgreSQL sobre la columna timestamp fecha_timbrado.
        Instant desde = RangoPeriodo.desdeInclusivoOMinimo(filtro.desde());
        Instant hasta = RangoPeriodo.hastaExclusivoOMaximo(filtro.hasta());

        BigDecimal facturacion = facturaRepository.sumarFacturacionTimbrada(desde, hasta);
        BigDecimal iva = facturaRepository.sumarIvaTimbrado(desde, hasta);
        long facturasTimbradas = facturaRepository.contarTimbradas(desde, hasta);

        LocalDate referencia = LocalDate.now();
        BigDecimal cxcVencidasSaldo = cuentaPorCobrarRepository.sumarSaldoVencido(referencia);
        long cxcVencidasConteo = cuentaPorCobrarRepository.contarVencidas(referencia);

        return new IndicadoresArea(AreaIndicador.FINANZAS, List.of(
                ValorIndicador.monetario(
                        "facturacion_periodo",
                        "Facturacion timbrada del periodo",
                        facturacion != null ? facturacion : BigDecimal.ZERO),
                ValorIndicador.conteo(
                        "facturas_timbradas_periodo",
                        "Facturas timbradas del periodo",
                        BigDecimal.valueOf(facturasTimbradas)),
                ValorIndicador.monetario(
                        "iva_trasladado_periodo",
                        "IVA trasladado del periodo",
                        iva != null ? iva : BigDecimal.ZERO),
                ValorIndicador.monetario(
                        "cxc_vencidas_saldo",
                        "Saldo de cuentas por cobrar vencidas",
                        cxcVencidasSaldo != null ? cxcVencidasSaldo : BigDecimal.ZERO),
                ValorIndicador.conteo(
                        "cxc_vencidas_conteo",
                        "Cuentas por cobrar vencidas",
                        BigDecimal.valueOf(cxcVencidasConteo))));
    }
}

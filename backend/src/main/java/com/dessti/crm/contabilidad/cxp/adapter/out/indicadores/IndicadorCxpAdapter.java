package com.dessti.crm.contabilidad.cxp.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.cxp.adapter.out.persistence.CuentaPorPagarRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorCxpPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorCxpPort}
 * (Req 22.1, 48.1). Deriva del propio submodulo de cuentas por pagar el saldo y el numero
 * de Cuentas_Por_Pagar vencidas, agregando unicamente con consultas {@code SUM}/{@code COUNT}
 * que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorCxpVacio}. El aislamiento por tenant (Req 23) lo garantizan el
 * filtro global de Hibernate y la RLS de PostgreSQL activos sobre el repositorio del
 * modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 *
 * <p>El vencimiento es un estado <em>actual</em> respecto a la fecha de hoy, no un flujo
 * del periodo; por ello el rango de fechas del filtro no acota estos indicadores.</p>
 */
@Component
public class IndicadorCxpAdapter implements IndicadorCxpPort {

    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Cuentas_Por_Pagar.
     *
     * @param cuentaPorPagarRepository repositorio de Cuentas_Por_Pagar.
     */
    public IndicadorCxpAdapter(CuentaPorPagarRepository cuentaPorPagarRepository) {
        this.cuentaPorPagarRepository = cuentaPorPagarRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        LocalDate referencia = LocalDate.now();
        BigDecimal saldoVencido = cuentaPorPagarRepository.sumarSaldoVencido(referencia);
        long conteoVencidas = cuentaPorPagarRepository.contarVencidas(referencia);

        return new IndicadoresArea(AreaIndicador.CXP, List.of(
                ValorIndicador.monetario(
                        "cxp_vencidas_saldo",
                        "Saldo de cuentas por pagar vencidas",
                        saldoVencido != null ? saldoVencido : BigDecimal.ZERO),
                ValorIndicador.conteo(
                        "cxp_vencidas_conteo",
                        "Cuentas por pagar vencidas",
                        BigDecimal.valueOf(conteoVencidas))));
    }
}

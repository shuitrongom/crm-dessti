package com.dessti.crm.tesoreria.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorTesoreriaPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;
import com.dessti.crm.tesoreria.adapter.out.persistence.CuentaBancariaRepository;
import com.dessti.crm.tesoreria.adapter.out.persistence.MovimientoBancarioRepository;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorTesoreriaPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de tesoreria los saldos bancarios (suma de
 * movimientos con signo) y las partidas pendientes de conciliacion, agregando unicamente
 * con consultas {@code SUM}/{@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorTesoreriaVacio}. El aislamiento por tenant (Req 23) lo garantizan
 * el filtro global de Hibernate y la RLS de PostgreSQL activos sobre los repositorios del
 * modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorTesoreriaAdapter implements IndicadorTesoreriaPort {

    private final MovimientoBancarioRepository movimientoBancarioRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;

    /**
     * Crea el adaptador con los repositorios de solo lectura de tesoreria.
     *
     * @param movimientoBancarioRepository repositorio de Movimiento_Bancario.
     * @param cuentaBancariaRepository      repositorio de Cuentas_Bancarias.
     */
    public IndicadorTesoreriaAdapter(MovimientoBancarioRepository movimientoBancarioRepository,
                                     CuentaBancariaRepository cuentaBancariaRepository) {
        this.movimientoBancarioRepository = movimientoBancarioRepository;
        this.cuentaBancariaRepository = cuentaBancariaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas de fecha SIEMPRE no nulas (columna date): las consultas de indicadores
        // comparan directamente (sin ":param IS NULL OR ...") y PostgreSQL infiere el tipo.
        LocalDate desde = RangoPeriodo.fechaDesdeOMinima(filtro.desde());
        LocalDate hasta = RangoPeriodo.fechaHastaOMaxima(filtro.hasta());

        BigDecimal saldo = movimientoBancarioRepository.sumarSaldoBancario(desde, hasta);
        long pendientes = movimientoBancarioRepository.contarPendientesConciliacion(desde, hasta);
        long cuentasActivas = cuentaBancariaRepository.countByActivaTrue();

        return new IndicadoresArea(AreaIndicador.TESORERIA, List.of(
                ValorIndicador.monetario(
                        "saldo_bancario_total",
                        "Saldo bancario acumulado",
                        saldo != null ? saldo : BigDecimal.ZERO),
                ValorIndicador.conteo(
                        "cuentas_bancarias_activas",
                        "Cuentas bancarias activas",
                        BigDecimal.valueOf(cuentasActivas)),
                ValorIndicador.conteo(
                        "partidas_conciliacion_pendientes",
                        "Partidas pendientes de conciliacion",
                        BigDecimal.valueOf(pendientes))));
    }
}

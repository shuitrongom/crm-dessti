package com.dessti.crm.operacion.inventario.adapter.out.indicadores;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorInventarioPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorInventarioPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de inventario los Materiales bajo stock minimo
 * y el total de Materiales activos, agregando unicamente con consultas {@code COUNT} que no
 * modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorInventarioVacio}. El aislamiento por tenant (Req 23) lo garantizan
 * el filtro global de Hibernate y la RLS de PostgreSQL activos sobre el repositorio del
 * modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 *
 * <p>Las existencias son un estado <em>actual</em> del inventario, no un flujo del periodo;
 * por ello el rango de fechas del filtro no acota estos indicadores.</p>
 */
@Component
public class IndicadorInventarioAdapter implements IndicadorInventarioPort {

    private final MaterialRepository materialRepository;

    /**
     * Crea el adaptador con el repositorio de solo lectura de Materiales.
     *
     * @param materialRepository repositorio de Materiales.
     */
    public IndicadorInventarioAdapter(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        long bajoStock = materialRepository.contarMaterialesBajoStockMinimo();
        long activos = materialRepository.countByActivoTrue();

        return new IndicadoresArea(AreaIndicador.INVENTARIO, List.of(
                ValorIndicador.conteo(
                        "materiales_bajo_stock_minimo",
                        "Materiales bajo stock minimo",
                        BigDecimal.valueOf(bajoStock)),
                ValorIndicador.conteo(
                        "materiales_activos",
                        "Materiales activos",
                        BigDecimal.valueOf(activos))));
    }
}

package com.dessti.crm.compras.adapter.out.indicadores;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.factura.adapter.out.persistence.FacturaProveedorRepository;
import com.dessti.crm.compras.factura.domain.EstadoFacturaProveedor;
import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.reportesbi.application.indicadores.AreaIndicador;
import com.dessti.crm.reportesbi.application.indicadores.FiltroIndicadores;
import com.dessti.crm.reportesbi.application.indicadores.IndicadorComprasPort;
import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;
import com.dessti.crm.reportesbi.application.indicadores.RangoPeriodo;
import com.dessti.crm.reportesbi.application.indicadores.ValorIndicador;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link IndicadorComprasPort}
 * (Req 22.1, 48.1). Deriva del propio modulo de compras las Ordenes de Compra por estado
 * y las Facturas de Proveedor con discrepancia de la conciliacion de tres vias, agregando
 * unicamente con consultas {@code COUNT} que no modifican dato alguno (Req 22.2, 48.2).
 *
 * <p>Al registrarse como {@link Component} desplaza automaticamente al adaptador por
 * defecto {@code IndicadorComprasVacio}. El aislamiento por tenant (Req 23) lo garantizan
 * el filtro global de Hibernate y la RLS de PostgreSQL activos sobre los repositorios del
 * modulo; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class IndicadorComprasAdapter implements IndicadorComprasPort {

    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturaProveedorRepository facturaProveedorRepository;

    /**
     * Crea el adaptador con los repositorios de solo lectura de compras.
     *
     * @param ordenCompraRepository      repositorio de Ordenes de Compra.
     * @param facturaProveedorRepository repositorio de Facturas de Proveedor.
     */
    public IndicadorComprasAdapter(OrdenCompraRepository ordenCompraRepository,
                                   FacturaProveedorRepository facturaProveedorRepository) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.facturaProveedorRepository = facturaProveedorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresArea agregar(FiltroIndicadores filtro) {
        // Cotas no nulas [EPOCH, 9999): preservan la semantica "sin limite" y evitan el
        // fallo de inferencia de tipo de PostgreSQL sobre columnas timestamp.
        Instant desde = RangoPeriodo.desdeInclusivoOMinimo(filtro.desde());
        Instant hasta = RangoPeriodo.hastaExclusivoOMaximo(filtro.hasta());

        long[] conteoOc = new long[EstadoOrdenCompra.values().length];
        for (Object[] fila : ordenCompraRepository.contarPorEstado(desde, hasta)) {
            EstadoOrdenCompra estado = (EstadoOrdenCompra) fila[0];
            conteoOc[estado.ordinal()] = ((Number) fila[1]).longValue();
        }

        List<ValorIndicador> indicadores = new ArrayList<>();
        indicadores.add(ValorIndicador.conteo(
                "ordenes_compra_abiertas",
                "Ordenes de compra abiertas",
                BigDecimal.valueOf(conteoOc[EstadoOrdenCompra.ABIERTA.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_compra_recibidas_parcial",
                "Ordenes de compra recibidas parcialmente",
                BigDecimal.valueOf(conteoOc[EstadoOrdenCompra.RECIBIDA_PARCIAL.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_compra_recibidas_total",
                "Ordenes de compra recibidas totalmente",
                BigDecimal.valueOf(conteoOc[EstadoOrdenCompra.RECIBIDA_TOTAL.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_compra_cerradas",
                "Ordenes de compra cerradas",
                BigDecimal.valueOf(conteoOc[EstadoOrdenCompra.CERRADA.ordinal()])));
        indicadores.add(ValorIndicador.conteo(
                "ordenes_compra_canceladas",
                "Ordenes de compra canceladas",
                BigDecimal.valueOf(conteoOc[EstadoOrdenCompra.CANCELADA.ordinal()])));

        long facturasDiscrepancia = facturaProveedorRepository
                .contarPorEstado(EstadoFacturaProveedor.DISCREPANCIA, desde, hasta);
        indicadores.add(ValorIndicador.conteo(
                "facturas_proveedor_discrepancia",
                "Facturas de proveedor con discrepancia",
                BigDecimal.valueOf(facturasDiscrepancia)));

        return new IndicadoresArea(AreaIndicador.COMPRAS, indicadores);
    }
}

package com.dessti.crm.presupuestos.adapter.out.real;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.presupuestos.application.RealEjercidoPort;
import com.dessti.crm.presupuestos.domain.CalculoVariacionPresupuesto;
import com.dessti.crm.rhnomina.nomina.adapter.out.persistence.ReciboNominaRepository;

/**
 * Adaptador concreto de <strong>solo lectura</strong> del {@link RealEjercidoPort}
 * (Req 62.2, 62.8) que agrega el ejercicio REAL a partir de las operaciones vivas del
 * CRM, cerrando el hueco que dejaba el placeholder {@code RealEjercidoAdapterPorDefecto}
 * (que devolvia cero):
 *
 * <ul>
 *   <li><strong>Ingresos reales</strong> = facturacion timbrada del periodo
 *       ({@link FacturaRepository#sumarFacturacionTimbrada}). Es el ingreso efectivamente
 *       facturado y timbrado ante el SAT.</li>
 *   <li><strong>Egresos reales</strong> = compras no canceladas del periodo
 *       ({@link OrdenCompraRepository#sumarComprasPeriodo}) mas el costo de nomina del
 *       periodo ({@link ReciboNominaRepository#sumarCostoNomina}). Son los dos egresos
 *       operativos dominantes del negocio.</li>
 * </ul>
 *
 * <h2>Periodo y agregacion (decision documentada)</h2>
 * <p>El {@code periodo} del Presupuesto es un codigo mensual {@code 'AAAA-MM'}. Se
 * convierte al rango de instantes {@code [inicio de mes, inicio del mes siguiente)} en la
 * zona {@code America/Mexico_City} (coherente con la operacion fiscal en Mexico) para
 * acotar las agregaciones por fecha de timbrado/creacion. Si el {@code periodo} no tiene
 * el formato esperado, se devuelve cero de forma segura (no rompe la consulta de
 * variacion).</p>
 *
 * <p>El {@code area} NO segmenta las agregaciones: los origenes (facturacion, compras,
 * nomina) no tienen hoy una dimension de area presupuestal, de modo que el real se agrega
 * a nivel de tenant por periodo. Es la misma consolidacion que aplica el
 * {@code IndicadorPresupuestoAdapter}. Cuando esos modulos incorporen una dimension de
 * area (por ejemplo un centro de costo), este adaptador podra refinarse sin tocar el
 * motor de variacion (Req 62.2).</p>
 *
 * <h2>Sustitucion (Req 62.2, 62.8)</h2>
 * <p>Al registrarse como {@link Component} de tipo {@link RealEjercidoPort}, desplaza
 * automaticamente al {@code RealEjercidoAdapterPorDefecto}
 * ({@code @ConditionalOnMissingBean} en {@code PresupuestosConfig}). El aislamiento por
 * tenant (Req 23) lo garantizan el filtro global de Hibernate y la RLS de PostgreSQL de
 * cada modulo de origen; el {@code tenant_id} nunca viaja en el filtro.</p>
 */
@Component
public class RealEjercidoAgregadoAdapter implements RealEjercidoPort {

    /** Zona de negocio para delimitar el mes (coherente con los PDF fiscales). */
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private final FacturaRepository facturaRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final ReciboNominaRepository reciboNominaRepository;

    /**
     * @param facturaRepository      repositorio de solo lectura de Facturas (ingresos).
     * @param ordenCompraRepository  repositorio de solo lectura de Ordenes de Compra (egresos).
     * @param reciboNominaRepository repositorio de solo lectura de Recibo_Nomina (egresos).
     */
    public RealEjercidoAgregadoAdapter(FacturaRepository facturaRepository,
                                       OrdenCompraRepository ordenCompraRepository,
                                       ReciboNominaRepository reciboNominaRepository) {
        this.facturaRepository = facturaRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.reciboNominaRepository = reciboNominaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal realIngresos(String area, String periodo) {
        Rango rango = rangoDe(periodo);
        if (rango == null) {
            return CalculoVariacionPresupuesto.normalizar(BigDecimal.ZERO);
        }
        BigDecimal facturado = facturaRepository.sumarFacturacionTimbrada(rango.desde(), rango.hasta());
        return CalculoVariacionPresupuesto.normalizar(facturado != null ? facturado : BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal realEgresos(String area, String periodo) {
        Rango rango = rangoDe(periodo);
        if (rango == null) {
            return CalculoVariacionPresupuesto.normalizar(BigDecimal.ZERO);
        }
        BigDecimal compras = ordenCompraRepository.sumarComprasPeriodo(rango.desde(), rango.hasta());
        BigDecimal nomina = reciboNominaRepository.sumarCostoNomina(rango.desde(), rango.hasta());
        BigDecimal total = (compras != null ? compras : BigDecimal.ZERO)
                .add(nomina != null ? nomina : BigDecimal.ZERO);
        return CalculoVariacionPresupuesto.normalizar(total);
    }

    /**
     * Convierte un periodo {@code 'AAAA-MM'} al rango de instantes del mes en la zona de
     * negocio. Devuelve {@code null} si el formato es invalido (degradacion segura).
     */
    private Rango rangoDe(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            return null;
        }
        try {
            YearMonth ym = YearMonth.parse(periodo.trim());
            Instant desde = ym.atDay(1).atStartOfDay(ZONA).toInstant();
            Instant hasta = ym.plusMonths(1).atDay(1).atStartOfDay(ZONA).toInstant();
            return new Rango(desde, hasta);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Rango de instantes [desde, hasta) del mes del periodo. */
    private record Rango(Instant desde, Instant hasta) {
    }
}

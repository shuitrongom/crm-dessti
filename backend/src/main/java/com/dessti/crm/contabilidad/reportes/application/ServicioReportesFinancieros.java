package com.dessti.crm.contabilidad.reportes.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.cxc.adapter.out.persistence.CuentaPorCobrarRepository;
import com.dessti.crm.contabilidad.cxc.application.AntiguedadSaldosDto;
import com.dessti.crm.contabilidad.cxc.domain.CuentaPorCobrar;
import com.dessti.crm.contabilidad.polizas.adapter.out.persistence.PolizaContableRepository;
import com.dessti.crm.contabilidad.polizas.application.PolizaContableDto;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.IngresosPeriodoProjection;
import com.dessti.crm.contabilidad.reportes.adapter.out.persistence.ReportesFiscalesRepository;
import com.dessti.crm.facturacion.factura.domain.EstadoFactura;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion de <strong>solo lectura</strong> que produce los reportes
 * financieros y fiscales del tenant (Req 39): estado de cuenta por Cliente, ingresos
 * por periodo, IVA trasladado y retenido por periodo, antiguedad de saldos (aging) y
 * libro de Polizas_Contables. Todas son <strong>agregaciones que no modifican los
 * datos de origen</strong> (Req 39.2) y se apoyan en los repositorios de solo lectura
 * de facturacion, CxC y polizas.
 *
 * <h2>Filtros (Req 39.3)</h2>
 * <p>Cada reporte admite filtro por rango de fechas ({@code desde}/{@code hasta}) y,
 * cuando aplica, por Cliente. El rango de fechas de la peticion se convierte a
 * instantes UTC {@code [desde 00:00, hasta+1 00:00)} para acotar los campos de tipo
 * instante ({@code fecha_timbrado}, {@code fecha_emision}).</p>
 *
 * <h2>Autorizacion, aislamiento y auditoria</h2>
 * <ul>
 *   <li><strong>403 sin permiso (Req 39.4):</strong> las rutas del controlador
 *       exigen {@code reporte_financiero:{leer,exportar}} via {@code @PreAuthorize};
 *       sin el permiso contable se responde 403.</li>
 *   <li><strong>Multi-tenant (Req 23):</strong> el {@code tenant_id} se deriva del
 *       {@link TenantContext} (nunca de la peticion, Req 23.4).</li>
 *   <li><strong>Auditoria (Req 39.5):</strong> cada consulta y cada exportacion se
 *       audita via {@link AuditoriaPort}. El {@link Clock} inyectado hace determinista
 *       el calculo de antiguedad del aging.</li>
 * </ul>
 */
@Service
public class ServicioReportesFinancieros {

    /** Tipo de recurso de auditoria/RBAC de los reportes financieros. */
    static final String RECURSO = "reporte_financiero";

    /** Escala monetaria coherente con NUMERIC(18,2). */
    private static final int ESCALA_MONETARIA = 2;

    private final ReportesFiscalesRepository reportesFiscalesRepository;
    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;
    private final PolizaContableRepository polizaContableRepository;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioReportesFinancieros(ReportesFiscalesRepository reportesFiscalesRepository,
                                       CuentaPorCobrarRepository cuentaPorCobrarRepository,
                                       PolizaContableRepository polizaContableRepository,
                                       AuditoriaPort auditoria,
                                       Clock clock) {
        this.reportesFiscalesRepository = reportesFiscalesRepository;
        this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
        this.polizaContableRepository = polizaContableRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Estado de cuenta por Cliente: el detalle de sus Cuentas_Por_Cobrar en el
     * periodo, con total y saldo, y los grandes totales (Req 39.1, 39.3). Audita la
     * consulta o exportacion (Req 39.5).
     *
     * @param clienteId Cliente cuyo estado de cuenta se consulta; obligatorio.
     * @param desde     inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta     fin del periodo (inclusivo); {@code null} no filtra.
     * @param exportar  {@code true} si es una exportacion (Req 39.4, 39.5).
     * @return el DTO del estado de cuenta del Cliente.
     * @throws ReglaNegocioException si no se indica el Cliente (422).
     */
    @Transactional(readOnly = true)
    public EstadoCuentaClienteDto estadoDeCuentaPorCliente(UUID clienteId, LocalDate desde,
                                                           LocalDate hasta, boolean exportar) {
        if (clienteId == null) {
            throw new ReglaNegocioException(
                    "El estado de cuenta requiere indicar el Cliente.");
        }
        List<CuentaPorCobrar> cuentas = cuentaPorCobrarRepository.buscarEstadoCuentaCliente(
                clienteId, inicioDe(desde), finExclusivoDe(hasta));
        auditarConsulta("estado_cuenta_cliente", desde, hasta, clienteId, exportar);
        return EstadoCuentaClienteDto.de(clienteId, cuentas, desde, hasta);
    }

    /**
     * Ingresos por periodo: subtotal, IVA, retenciones y total de las Facturas
     * timbradas del periodo, opcionalmente acotados a un Cliente (Req 39.1, 39.3).
     * Audita la consulta o exportacion (Req 39.5).
     *
     * @param desde     inicio del periodo (inclusivo); obligatorio.
     * @param hasta     fin del periodo (inclusivo); obligatorio.
     * @param clienteId Cliente a filtrar; {@code null} incluye a todos.
     * @param exportar  {@code true} si es una exportacion.
     * @return el DTO de ingresos por periodo.
     * @throws ReglaNegocioException si falta el rango de fechas (422).
     */
    @Transactional(readOnly = true)
    public IngresosPeriodoDto ingresosPorPeriodo(LocalDate desde, LocalDate hasta,
                                                 UUID clienteId, boolean exportar) {
        IngresosPeriodoProjection proyeccion = agregarFiscal(desde, hasta, clienteId);
        auditarConsulta("ingresos_periodo", desde, hasta, clienteId, exportar);
        return IngresosPeriodoDto.de(proyeccion, desde, hasta, clienteId);
    }

    /**
     * IVA trasladado y retenido por periodo: base gravable, IVA trasladado y
     * retenciones de las Facturas timbradas del periodo, opcionalmente acotados a un
     * Cliente (Req 39.1, 39.3). Audita la consulta o exportacion (Req 39.5).
     *
     * @param desde     inicio del periodo (inclusivo); obligatorio.
     * @param hasta     fin del periodo (inclusivo); obligatorio.
     * @param clienteId Cliente a filtrar; {@code null} incluye a todos.
     * @param exportar  {@code true} si es una exportacion.
     * @return el DTO de IVA trasladado y retenido por periodo.
     * @throws ReglaNegocioException si falta el rango de fechas (422).
     */
    @Transactional(readOnly = true)
    public IvaPeriodoDto ivaTrasladadoRetenido(LocalDate desde, LocalDate hasta,
                                               UUID clienteId, boolean exportar) {
        IngresosPeriodoProjection proyeccion = agregarFiscal(desde, hasta, clienteId);
        auditarConsulta("iva_periodo", desde, hasta, clienteId, exportar);
        return IvaPeriodoDto.de(proyeccion, desde, hasta, clienteId);
    }

    /**
     * Antiguedad de saldos (aging) de las Cuentas_Por_Cobrar con saldo pendiente del
     * tenant, agrupada por Cliente y clasificada por rango de dias de vencimiento
     * (Req 39.1, 39.3). Solo lectura. Los dias se miden con el {@link Clock}
     * inyectado desde la fecha de vencimiento (o la de emision cuando no hay
     * vencimiento). Audita la consulta o exportacion (Req 39.5).
     *
     * @param clienteId Cliente a filtrar; {@code null} incluye a todos.
     * @param exportar  {@code true} si es una exportacion.
     * @return el DTO de antiguedad de saldos.
     */
    @Transactional(readOnly = true)
    public AntiguedadSaldosDto aging(UUID clienteId, boolean exportar) {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<CuentaPorCobrar> pendientes =
                cuentaPorCobrarRepository.buscarPendientesParaAging(clienteId);

        Map<UUID, BigDecimal[]> acumulado = new LinkedHashMap<>();
        for (CuentaPorCobrar cxc : pendientes) {
            long dias = diasAntiguedad(cxc, hoy);
            int bucket = indiceRango(dias);
            BigDecimal[] rangos = acumulado.computeIfAbsent(
                    cxc.getClienteId(), k -> nuevoAcumulador());
            rangos[bucket] = rangos[bucket].add(cxc.getSaldo());
        }

        List<AntiguedadSaldosDto.RenglonCliente> renglones = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal[]> entrada : acumulado.entrySet()) {
            BigDecimal[] r = entrada.getValue();
            BigDecimal total = r[0].add(r[1]).add(r[2]).add(r[3]);
            renglones.add(new AntiguedadSaldosDto.RenglonCliente(
                    entrada.getKey(), total, r[0], r[1], r[2], r[3]));
        }
        auditarConsulta("aging", null, null, clienteId, exportar);
        return new AntiguedadSaldosDto(renglones);
    }

    /**
     * Libro de Polizas_Contables: listado paginado de las polizas del periodo, con
     * filtro opcional por Cuenta_Contable (Req 39.1, 39.3). Solo lectura. Audita la
     * consulta o exportacion (Req 39.5).
     *
     * @param desde            inicio del periodo (inclusivo); {@code null} no filtra.
     * @param hasta            fin del periodo (inclusivo); {@code null} no filtra.
     * @param cuentaContableId Cuenta_Contable a filtrar; {@code null} no filtra.
     * @param exportar         {@code true} si es una exportacion.
     * @param pageable         parametros de paginacion ya acotados (20/100).
     * @return la pagina de Polizas_Contables como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PolizaContableDto> libroPolizas(LocalDate desde, LocalDate hasta,
                                                UUID cuentaContableId, boolean exportar,
                                                Pageable pageable) {
        Page<PolizaContableDto> pagina = polizaContableRepository
                .buscarConFiltros(desde, hasta, cuentaContableId, pageable)
                .map(PolizaContableDto::de);
        auditarConsulta("libro_polizas", desde, hasta, cuentaContableId, exportar);
        return pagina;
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Agrega los importes fiscales de las Facturas timbradas del periodo, exigiendo
     * el rango de fechas (Req 39.1, 39.3).
     */
    private IngresosPeriodoProjection agregarFiscal(LocalDate desde, LocalDate hasta,
                                                    UUID clienteId) {
        if (desde == null || hasta == null) {
            throw new ReglaNegocioException(
                    "El reporte por periodo requiere el rango de fechas 'desde' y 'hasta'.");
        }
        if (hasta.isBefore(desde)) {
            throw new ReglaNegocioException(
                    "El fin del periodo no puede ser anterior al inicio.");
        }
        return reportesFiscalesRepository.agregarIngresosPeriodo(
                EstadoFactura.TIMBRADA, inicioDe(desde), finExclusivoDe(hasta), clienteId);
    }

    /** Instante UTC del inicio del dia {@code fecha}; {@code null} si no hay filtro. */
    private Instant inicioDe(LocalDate fecha) {
        return (fecha == null) ? null : fecha.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Instante UTC del inicio del dia siguiente a {@code fecha} (limite exclusivo). */
    private Instant finExclusivoDe(LocalDate fecha) {
        return (fecha == null) ? null : fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private BigDecimal[] nuevoAcumulador() {
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return new BigDecimal[] {cero, cero, cero, cero};
    }

    /**
     * Dias de antiguedad de una CxC respecto a {@code hoy}: dias desde su fecha de
     * vencimiento, o desde su fecha de emision si no tiene vencimiento. Nunca
     * negativo.
     */
    private long diasAntiguedad(CuentaPorCobrar cxc, LocalDate hoy) {
        LocalDate referencia = (cxc.getFechaVencimiento() != null)
                ? cxc.getFechaVencimiento()
                : LocalDate.ofInstant(cxc.getFechaEmision(), ZoneOffset.UTC);
        long dias = ChronoUnit.DAYS.between(referencia, hoy);
        return Math.max(0L, dias);
    }

    /** Rango de aging: 0 (0-30), 1 (31-60), 2 (61-90), 3 (&gt;90). */
    private int indiceRango(long dias) {
        if (dias <= 30) {
            return 0;
        }
        if (dias <= 60) {
            return 1;
        }
        if (dias <= 90) {
            return 2;
        }
        return 3;
    }

    private void auditarConsulta(String reporte, LocalDate desde, LocalDate hasta,
                                 UUID filtro, boolean exportar) {
        String accion = exportar ? "exportar" : "consultar";
        String detalle = "reporte financiero '" + reporte + "' [desde=" + desde
                + ", hasta=" + hasta + ", filtro=" + filtro + ", exportar=" + exportar + "]";
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), accion, RECURSO, detalle, null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}

package com.dessti.crm.contabilidad.reportes.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.cxc.application.AntiguedadSaldosDto;
import com.dessti.crm.contabilidad.polizas.application.PolizaContableDto;
import com.dessti.crm.contabilidad.reportes.application.EstadoCuentaClienteDto;
import com.dessti.crm.contabilidad.reportes.application.IngresosPeriodoDto;
import com.dessti.crm.contabilidad.reportes.application.IvaPeriodoDto;
import com.dessti.crm.contabilidad.reportes.application.ServicioReportesFinancieros;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para los
 * <strong>reportes financieros y fiscales</strong> de solo lectura (Req 39, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /contabilidad/reportes/estado-cuenta-cliente} — estado de cuenta
 *       por Cliente ({@code reporte_financiero:leer}).</li>
 *   <li>{@code GET /contabilidad/reportes/ingresos} — ingresos por periodo
 *       ({@code reporte_financiero:leer}).</li>
 *   <li>{@code GET /contabilidad/reportes/iva} — IVA trasladado/retenido por periodo
 *       ({@code reporte_financiero:leer}).</li>
 *   <li>{@code GET /contabilidad/reportes/aging} — antiguedad de saldos
 *       ({@code reporte_financiero:leer}).</li>
 *   <li>{@code GET /contabilidad/reportes/libro-polizas} — libro de polizas paginado
 *       ({@code reporte_financiero:leer}).</li>
 *   <li>Variantes {@code .../exportar} de cada reporte
 *       ({@code reporte_financiero:exportar}).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 39.4)</h2>
 * <p>Cada ruta exige el permiso {@code reporte_financiero:{leer|exportar}} via
 * {@code @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene(...)")}: una peticion sin el permiso
 * contable recibe 403 (Req 39.4). Los permisos {@code reporte_financiero:{leer,
 * exportar}} ya se sembraron en V5 y se asignaron a los roles {@code contabilidad} y
 * {@code gerente}; se reutilizan aqui sin re-sembrarlos.</p>
 */
@RestController
@RequestMapping("/contabilidad/reportes")
public class ReportesFinancierosController {

    private final ServicioReportesFinancieros servicioReportesFinancieros;

    public ReportesFinancierosController(ServicioReportesFinancieros servicioReportesFinancieros) {
        this.servicioReportesFinancieros = servicioReportesFinancieros;
    }

    /**
     * Estado de cuenta por Cliente (Req 39.1, 39.3).
     *
     * @param clienteId Cliente cuyo estado de cuenta se consulta; obligatorio.
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link EstadoCuentaClienteDto}.
     */
    @GetMapping("/estado-cuenta-cliente")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','leer')")
    public ResponseEntity<EstadoCuentaClienteDto> estadoCuentaCliente(
            @RequestParam(name = "clienteId") UUID clienteId,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(servicioReportesFinancieros
                .estadoDeCuentaPorCliente(clienteId, desde, hasta, false));
    }

    /**
     * Exportacion del estado de cuenta por Cliente (Req 39.4, 39.5).
     *
     * @param clienteId Cliente; obligatorio.
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link EstadoCuentaClienteDto} para exportar.
     */
    @GetMapping("/estado-cuenta-cliente/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','exportar')")
    public ResponseEntity<EstadoCuentaClienteDto> exportarEstadoCuentaCliente(
            @RequestParam(name = "clienteId") UUID clienteId,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(servicioReportesFinancieros
                .estadoDeCuentaPorCliente(clienteId, desde, hasta, true));
    }

    /**
     * Ingresos por periodo (Req 39.1, 39.3).
     *
     * @param desde     inicio del periodo (inclusivo); obligatorio.
     * @param hasta     fin del periodo (inclusivo); obligatorio.
     * @param clienteId Cliente a filtrar; opcional.
     * @return 200 OK con el {@link IngresosPeriodoDto}.
     */
    @GetMapping("/ingresos")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','leer')")
    public ResponseEntity<IngresosPeriodoDto> ingresos(
            @RequestParam(name = "desde")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioReportesFinancieros
                .ingresosPorPeriodo(desde, hasta, clienteId, false));
    }

    /**
     * Exportacion de ingresos por periodo (Req 39.4, 39.5).
     *
     * @param desde     inicio del periodo (inclusivo); obligatorio.
     * @param hasta     fin del periodo (inclusivo); obligatorio.
     * @param clienteId Cliente a filtrar; opcional.
     * @return 200 OK con el {@link IngresosPeriodoDto} para exportar.
     */
    @GetMapping("/ingresos/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','exportar')")
    public ResponseEntity<IngresosPeriodoDto> exportarIngresos(
            @RequestParam(name = "desde")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioReportesFinancieros
                .ingresosPorPeriodo(desde, hasta, clienteId, true));
    }

    /**
     * IVA trasladado y retenido por periodo (Req 39.1, 39.3).
     *
     * @param desde     inicio del periodo (inclusivo); obligatorio.
     * @param hasta     fin del periodo (inclusivo); obligatorio.
     * @param clienteId Cliente a filtrar; opcional.
     * @return 200 OK con el {@link IvaPeriodoDto}.
     */
    @GetMapping("/iva")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','leer')")
    public ResponseEntity<IvaPeriodoDto> iva(
            @RequestParam(name = "desde")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioReportesFinancieros
                .ivaTrasladadoRetenido(desde, hasta, clienteId, false));
    }

    /**
     * Exportacion de IVA trasladado y retenido por periodo (Req 39.4, 39.5).
     *
     * @param desde     inicio del periodo (inclusivo); obligatorio.
     * @param hasta     fin del periodo (inclusivo); obligatorio.
     * @param clienteId Cliente a filtrar; opcional.
     * @return 200 OK con el {@link IvaPeriodoDto} para exportar.
     */
    @GetMapping("/iva/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','exportar')")
    public ResponseEntity<IvaPeriodoDto> exportarIva(
            @RequestParam(name = "desde")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioReportesFinancieros
                .ivaTrasladadoRetenido(desde, hasta, clienteId, true));
    }

    /**
     * Antiguedad de saldos (aging) por Cliente (Req 39.1, 39.3).
     *
     * @param clienteId Cliente a filtrar; opcional (si es nulo incluye a todos).
     * @return 200 OK con el {@link AntiguedadSaldosDto}.
     */
    @GetMapping("/aging")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','leer')")
    public ResponseEntity<AntiguedadSaldosDto> aging(
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioReportesFinancieros.aging(clienteId, false));
    }

    /**
     * Exportacion de la antiguedad de saldos (Req 39.4, 39.5).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @return 200 OK con el {@link AntiguedadSaldosDto} para exportar.
     */
    @GetMapping("/aging/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','exportar')")
    public ResponseEntity<AntiguedadSaldosDto> exportarAging(
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioReportesFinancieros.aging(clienteId, true));
    }

    /**
     * Libro de Polizas_Contables paginado (Req 39.1, 39.3).
     *
     * @param desde            inicio del periodo (inclusivo); opcional.
     * @param hasta            fin del periodo (inclusivo); opcional.
     * @param cuentaContableId Cuenta_Contable a filtrar; opcional.
     * @param page             numero de pagina 0-index; opcional.
     * @param size             tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PolizaContableDto}.
     */
    @GetMapping("/libro-polizas")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','leer')")
    public PaginaResponse<PolizaContableDto> libroPolizas(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "cuentaContableId", required = false) UUID cuentaContableId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioReportesFinancieros
                .libroPolizas(desde, hasta, cuentaContableId, false, pageable));
    }

    /**
     * Exportacion del libro de Polizas_Contables paginado (Req 39.4, 39.5).
     *
     * @param desde            inicio del periodo (inclusivo); opcional.
     * @param hasta            fin del periodo (inclusivo); opcional.
     * @param cuentaContableId Cuenta_Contable a filtrar; opcional.
     * @param page             numero de pagina 0-index; opcional.
     * @param size             tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PolizaContableDto} para exportar.
     */
    @GetMapping("/libro-polizas/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('reporte_financiero','exportar')")
    public PaginaResponse<PolizaContableDto> exportarLibroPolizas(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "cuentaContableId", required = false) UUID cuentaContableId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioReportesFinancieros
                .libroPolizas(desde, hasta, cuentaContableId, true, pageable));
    }
}

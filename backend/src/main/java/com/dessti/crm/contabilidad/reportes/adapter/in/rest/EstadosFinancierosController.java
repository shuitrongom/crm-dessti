package com.dessti.crm.contabilidad.reportes.adapter.in.rest;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.contabilidad.reportes.application.BalanceGeneralDto;
import com.dessti.crm.contabilidad.reportes.application.BalanzaComprobacionDto;
import com.dessti.crm.contabilidad.reportes.application.EstadoResultadosDto;
import com.dessti.crm.contabilidad.reportes.application.ServicioEstadosFinancieros;

/**
 * Adaptador de entrada REST del modulo contabilidad-finanzas para los
 * <strong>estados financieros</strong> de solo lectura derivados de las
 * Polizas_Contables de un periodo (Req 47).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /contabilidad/estados-financieros/balance-general} — balance
 *       general ({@code estado_financiero:leer}); cumple {@code activo == pasivo +
 *       capital} (Property 17, Req 47.3).</li>
 *   <li>{@code GET /contabilidad/estados-financieros/estado-resultados} — estado de
 *       resultados ({@code estado_financiero:leer}).</li>
 *   <li>{@code GET /contabilidad/estados-financieros/balanza-comprobacion} — balanza
 *       de comprobacion ({@code estado_financiero:leer}).</li>
 *   <li>Variantes {@code .../exportar} de cada estado
 *       ({@code estado_financiero:exportar}).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 47.5)</h2>
 * <p>Cada ruta exige el permiso {@code estado_financiero:{leer|exportar}} via
 * {@code @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene(...)")}: una peticion sin el permiso
 * contable recibe 403 (Req 47.5). Los permisos {@code estado_financiero:{leer,
 * exportar}} ya se sembraron en V5 y se asignaron a los roles {@code contabilidad} y
 * {@code gerente}; se reutilizan aqui sin re-sembrarlos.</p>
 */
@RestController
@RequestMapping("/contabilidad/estados-financieros")
public class EstadosFinancierosController {

    private final ServicioEstadosFinancieros servicioEstadosFinancieros;

    public EstadosFinancierosController(ServicioEstadosFinancieros servicioEstadosFinancieros) {
        this.servicioEstadosFinancieros = servicioEstadosFinancieros;
    }

    /**
     * Balance general del periodo (Req 47.1, 47.3; Property 17).
     *
     * @param desde inicio del periodo (inclusivo); opcional.
     * @param hasta fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link BalanceGeneralDto}.
     */
    @GetMapping("/balance-general")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('estado_financiero','leer')")
    public ResponseEntity<BalanceGeneralDto> balanceGeneral(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                servicioEstadosFinancieros.balanceGeneral(desde, hasta, false));
    }

    /**
     * Exportacion del balance general del periodo (Req 47.4, 47.5, 47.6).
     *
     * @param desde inicio del periodo (inclusivo); opcional.
     * @param hasta fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link BalanceGeneralDto} para exportar.
     */
    @GetMapping("/balance-general/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('estado_financiero','exportar')")
    public ResponseEntity<BalanceGeneralDto> exportarBalanceGeneral(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                servicioEstadosFinancieros.balanceGeneral(desde, hasta, true));
    }

    /**
     * Estado de resultados del periodo (Req 47.1).
     *
     * @param desde inicio del periodo (inclusivo); opcional.
     * @param hasta fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link EstadoResultadosDto}.
     */
    @GetMapping("/estado-resultados")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('estado_financiero','leer')")
    public ResponseEntity<EstadoResultadosDto> estadoResultados(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                servicioEstadosFinancieros.estadoDeResultados(desde, hasta, false));
    }

    /**
     * Exportacion del estado de resultados del periodo (Req 47.4, 47.5, 47.6).
     *
     * @param desde inicio del periodo (inclusivo); opcional.
     * @param hasta fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link EstadoResultadosDto} para exportar.
     */
    @GetMapping("/estado-resultados/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('estado_financiero','exportar')")
    public ResponseEntity<EstadoResultadosDto> exportarEstadoResultados(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                servicioEstadosFinancieros.estadoDeResultados(desde, hasta, true));
    }

    /**
     * Balanza de comprobacion del periodo (Req 47.1).
     *
     * @param desde inicio del periodo (inclusivo); opcional.
     * @param hasta fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link BalanzaComprobacionDto}.
     */
    @GetMapping("/balanza-comprobacion")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('estado_financiero','leer')")
    public ResponseEntity<BalanzaComprobacionDto> balanzaComprobacion(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                servicioEstadosFinancieros.balanzaDeComprobacion(desde, hasta, false));
    }

    /**
     * Exportacion de la balanza de comprobacion del periodo (Req 47.4, 47.5, 47.6).
     *
     * @param desde inicio del periodo (inclusivo); opcional.
     * @param hasta fin del periodo (inclusivo); opcional.
     * @return 200 OK con el {@link BalanzaComprobacionDto} para exportar.
     */
    @GetMapping("/balanza-comprobacion/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('contabilidad') and @autorizador.tiene('estado_financiero','exportar')")
    public ResponseEntity<BalanzaComprobacionDto> exportarBalanzaComprobacion(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
                servicioEstadosFinancieros.balanzaDeComprobacion(desde, hasta, true));
    }
}

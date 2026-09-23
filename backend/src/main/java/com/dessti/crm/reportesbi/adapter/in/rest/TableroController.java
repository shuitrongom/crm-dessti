package com.dessti.crm.reportesbi.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.reportesbi.application.ServicioTablero;
import com.dessti.crm.reportesbi.application.TableroDto;

/**
 * Adaptador de entrada REST del modulo reportes-bi para el <strong>Tablero de
 * indicadores por area</strong> de solo lectura (Req 22, 12).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /reportes-bi/tablero} — compone el Tablero de indicadores por area
 *       con filtro por fecha/Cliente ({@code tablero:leer}).</li>
 *   <li>{@code GET /reportes-bi/tablero/exportar} — exportacion del Tablero
 *       ({@code reporte:exportar}).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 22.5)</h2>
 * <p>La consulta exige {@code tablero:leer} y la exportacion {@code reporte:exportar}
 * via {@code @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene(...)")}: una peticion sin el permiso
 * recibe 403 (Req 22.5). Ambos permisos ya se sembraron en V5 y se asignaron a los
 * roles {@code gerente}, {@code admin_empresa} y {@code supervisor} (lectura
 * transversal), por lo que V44 no re-siembra permisos para el Tablero.</p>
 *
 * <p><strong>403 fino por area (Req 22.5):</strong> el Tablero se protege con
 * {@code tablero:leer} a nivel de endpoint. Los indicadores de cada area se agregan a
 * traves de puertos desacoplados; el control de acceso fino por area lo aplica cada
 * modulo de area en su propio adaptador de puerto (reutilizando el permiso de lectura de
 * esa area), de modo que un adaptador concreto puede devolver el area en cero cuando el
 * actor carece del permiso especifico sin romper la composicion del Tablero. Se elige la
 * opcion simple y defendible: puerta unica {@code tablero:leer} en el agregador, con la
 * granularidad delegada al adaptador de area.</p>
 */
@RestController
@RequestMapping("/reportes-bi/tablero")
public class TableroController {

    private final ServicioTablero servicioTablero;

    public TableroController(ServicioTablero servicioTablero) {
        this.servicioTablero = servicioTablero;
    }

    /**
     * Compone el Tablero de indicadores por area (Req 22.1, 22.3).
     *
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @param clienteId Cliente a acotar; opcional.
     * @return 200 OK con el {@link TableroDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('tablero','leer')")
    public ResponseEntity<TableroDto> tablero(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioTablero.consultarTablero(desde, hasta, clienteId, false));
    }

    /**
     * Exporta el Tablero de indicadores por area (Req 22.4, 22.6).
     *
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @param clienteId Cliente a acotar; opcional.
     * @return 200 OK con el {@link TableroDto} para exportar.
     */
    @GetMapping("/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('reporte','exportar')")
    public ResponseEntity<TableroDto> exportarTablero(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "clienteId", required = false) UUID clienteId) {
        return ResponseEntity.ok(servicioTablero.consultarTablero(desde, hasta, clienteId, true));
    }
}

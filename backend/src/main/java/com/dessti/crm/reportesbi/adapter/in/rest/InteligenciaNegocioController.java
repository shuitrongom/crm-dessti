package com.dessti.crm.reportesbi.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.reportesbi.application.InteligenciaNegocioDto;
import com.dessti.crm.reportesbi.application.ServicioInteligenciaNegocio;
import com.dessti.crm.reportesbi.application.TableroPersonalizadoDto;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo reportes-bi para la <strong>Inteligencia de
 * Negocio consolidada</strong> (Req 48, 12): analisis consolidado de solo lectura con
 * tendencias/comparativos y gestion de los tableros analiticos personalizados.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /reportes-bi/inteligencia-negocio/consolidado} — analisis
 *       consolidado con filtros por fecha/area/dimension ({@code inteligencia_negocio:leer}).</li>
 *   <li>{@code GET /reportes-bi/inteligencia-negocio/consolidado/exportar} — exportacion
 *       del consolidado ({@code inteligencia_negocio:exportar}).</li>
 *   <li>{@code POST /reportes-bi/inteligencia-negocio/tableros-personalizados} — crear
 *       ({@code inteligencia_negocio:gestionar}); 201 Created.</li>
 *   <li>{@code GET .../tableros-personalizados} — listado paginado ({@code inteligencia_negocio:leer}).</li>
 *   <li>{@code GET .../tableros-personalizados/{id}} — consulta ({@code inteligencia_negocio:leer}); 404 si no accesible.</li>
 *   <li>{@code PUT .../tableros-personalizados/{id}} — actualizar ({@code inteligencia_negocio:gestionar}).</li>
 *   <li>{@code DELETE .../tableros-personalizados/{id}} — eliminar ({@code inteligencia_negocio:gestionar}); 204 No Content.</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 48.6)</h2>
 * <p>Cada ruta exige el permiso {@code inteligencia_negocio:{leer|gestionar|exportar}}
 * via {@code @PreAuthorize}: una peticion sin el permiso analitico recibe 403 (Req 48.6).
 * El recurso {@code inteligencia_negocio} y sus operaciones se sembraron en V44 y se
 * asignaron a los roles {@code gerente} y {@code admin_empresa}.</p>
 *
 * <h2>Aislamiento (Req 48.5) y separacion de contrato</h2>
 * <p>El {@code tenant_id} y el actor se derivan del contexto; se reciben/devuelven DTOs
 * distintos de las entidades JPA. El manejo de errores lo centraliza el manejador global
 * (422 regla de negocio, 404 no encontrado, 400 paginacion invalida).</p>
 */
@RestController
@RequestMapping("/reportes-bi/inteligencia-negocio")
public class InteligenciaNegocioController {

    private final ServicioInteligenciaNegocio servicioInteligenciaNegocio;

    public InteligenciaNegocioController(ServicioInteligenciaNegocio servicioInteligenciaNegocio) {
        this.servicioInteligenciaNegocio = servicioInteligenciaNegocio;
    }

    /**
     * Analisis consolidado de todas las areas (o del area filtrada) con comparativos
     * (Req 48.1, 48.4).
     *
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @param area      area a filtrar; opcional.
     * @param dimension dimension de analisis; opcional.
     * @return 200 OK con el {@link InteligenciaNegocioDto}.
     */
    @GetMapping("/consolidado")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','leer')")
    public ResponseEntity<InteligenciaNegocioDto> consolidado(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "area", required = false) String area,
            @RequestParam(name = "dimension", required = false) String dimension) {
        return ResponseEntity.ok(servicioInteligenciaNegocio
                .consolidado(desde, hasta, area, dimension, false));
    }

    /**
     * Exportacion del analisis consolidado (Req 48.4, 48.7).
     *
     * @param desde     inicio del periodo (inclusivo); opcional.
     * @param hasta     fin del periodo (inclusivo); opcional.
     * @param area      area a filtrar; opcional.
     * @param dimension dimension de analisis; opcional.
     * @return 200 OK con el {@link InteligenciaNegocioDto} para exportar.
     */
    @GetMapping("/consolidado/exportar")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','exportar')")
    public ResponseEntity<InteligenciaNegocioDto> exportarConsolidado(
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "area", required = false) String area,
            @RequestParam(name = "dimension", required = false) String dimension) {
        return ResponseEntity.ok(servicioInteligenciaNegocio
                .consolidado(desde, hasta, area, dimension, true));
    }

    /**
     * Crea un tablero analitico personalizado (Req 48.3). 201 Created con el DTO.
     *
     * @param request definicion del tablero y sus widgets.
     * @return 201 Created con el {@link TableroPersonalizadoDto}.
     */
    @PostMapping("/tableros-personalizados")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','gestionar')")
    public ResponseEntity<TableroPersonalizadoDto> crearTablero(
            @Valid @RequestBody GuardarTableroPersonalizadoRequest request) {
        TableroPersonalizadoDto dto =
                servicioInteligenciaNegocio.crearTablero(request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista los tableros personalizados del tenant de forma paginada (Req 48.3).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link TableroPersonalizadoDto}.
     */
    @GetMapping("/tableros-personalizados")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','leer')")
    public PaginaResponse<TableroPersonalizadoDto> listarTableros(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioInteligenciaNegocio.listarTableros(pageable));
    }

    /**
     * Consulta un tablero personalizado por su identificador (Req 48.3, 23.3).
     *
     * @param id identificador del tablero.
     * @return 200 OK con el {@link TableroPersonalizadoDto}; 404 si no es accesible.
     */
    @GetMapping("/tableros-personalizados/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','leer')")
    public ResponseEntity<TableroPersonalizadoDto> consultarTablero(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioInteligenciaNegocio.consultarTablero(id));
    }

    /**
     * Actualiza un tablero personalizado y reemplaza sus widgets (Req 48.3).
     *
     * @param id      identificador del tablero.
     * @param request nueva definicion del tablero y sus widgets.
     * @return 200 OK con el {@link TableroPersonalizadoDto} actualizado; 404 si no es
     *         accesible.
     */
    @PutMapping("/tableros-personalizados/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','gestionar')")
    public ResponseEntity<TableroPersonalizadoDto> actualizarTablero(
            @PathVariable("id") UUID id,
            @Valid @RequestBody GuardarTableroPersonalizadoRequest request) {
        return ResponseEntity.ok(
                servicioInteligenciaNegocio.actualizarTablero(id, request.aComando()));
    }

    /**
     * Elimina un tablero personalizado y sus widgets (Req 48.3). 204 No Content.
     *
     * @param id identificador del tablero.
     * @return 204 No Content; 404 si no es accesible.
     */
    @DeleteMapping("/tableros-personalizados/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('reportes-bi') and @autorizador.tiene('inteligencia_negocio','gestionar')")
    public ResponseEntity<Void> eliminarTablero(@PathVariable("id") UUID id) {
        servicioInteligenciaNegocio.eliminarTablero(id);
        return ResponseEntity.noContent().build();
    }
}

package com.dessti.crm.calidad.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.calidad.application.ContextoOrganizacionDto;
import com.dessti.crm.calidad.application.DeterminarContextoCommand;
import com.dessti.crm.calidad.application.ServicioContextoOrganizacion;
import com.dessti.crm.calidad.domain.TipoContexto;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del Contexto_Organizacion (Req 70.5, clausulas 4.1/4.2; tarea
 * 54.5). Determinacion y documentacion de cuestiones internas/externas -incluida la
 * pertinencia del cambio climatico- y listado con filtros. La justificacion se conserva
 * aun cuando la conclusion sea "no pertinente".
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/contexto} — determinar
 *       ({@code @autorizador.tiene('contexto_organizacion','crear')}); 201.</li>
 *   <li>{@code GET /calidad/contexto?tipo=&climaPertinente=&page=&size=} — listado
 *       ({@code @autorizador.tiene('contexto_organizacion','listar')}).</li>
 *   <li>{@code GET /calidad/contexto/{id}} — consulta
 *       ({@code @autorizador.tiene('contexto_organizacion','leer')}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/calidad/contexto")
public class ContextoOrganizacionController {

    private final ServicioContextoOrganizacion servicioContexto;

    public ContextoOrganizacionController(ServicioContextoOrganizacion servicioContexto) {
        this.servicioContexto = servicioContexto;
    }

    /**
     * Determina y documenta una cuestion del contexto de la organizacion (Req 70.5). 201
     * con el DTO.
     *
     * @param request datos de la cuestion.
     * @return 201 Created con el {@link ContextoOrganizacionDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('contexto_organizacion','crear')")
    public ResponseEntity<ContextoOrganizacionDto> determinar(
            @Valid @RequestBody DeterminarContextoRequest request) {
        TipoContexto tipo = ParseoCalidad.tipoContextoRequerido(request.tipo());
        DeterminarContextoCommand comando = new DeterminarContextoCommand(
                request.cuestion(), tipo, request.climaPertinente(),
                request.justificacion(), request.parteInteresada(), request.expectativa());
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioContexto.determinar(comando));
    }

    /**
     * Lista las cuestiones de contexto del tenant de forma paginada (20/100) con filtros
     * opcionales (Req 70.5, 12).
     *
     * @param tipo            etiqueta del tipo a filtrar; opcional.
     * @param climaPertinente pertinencia del clima a filtrar; opcional.
     * @param page            numero de pagina 0-index; opcional.
     * @param size            tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link ContextoOrganizacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('contexto_organizacion','listar')")
    public PaginaResponse<ContextoOrganizacionDto> listar(
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "climaPertinente", required = false) Boolean climaPertinente,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        TipoContexto tipoFiltro = ParseoCalidad.tipoContextoOpcional(tipo);
        return PaginaResponse.de(servicioContexto.listar(tipoFiltro, climaPertinente, pageable));
    }

    /**
     * Consulta un Contexto_Organizacion por su identificador (Req 23.3).
     *
     * @param id identificador del Contexto_Organizacion.
     * @return 200 OK con el {@link ContextoOrganizacionDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('contexto_organizacion','leer')")
    public ResponseEntity<ContextoOrganizacionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioContexto.consultar(id));
    }
}

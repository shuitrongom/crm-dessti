package com.dessti.crm.calidad.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.calidad.application.IdentificarRiesgoCommand;
import com.dessti.crm.calidad.application.RiesgoDto;
import com.dessti.crm.calidad.application.ServicioRiesgosOportunidades;
import com.dessti.crm.calidad.domain.EstadoRiesgo;
import com.dessti.crm.calidad.domain.Impacto;
import com.dessti.crm.calidad.domain.NivelRiesgo;
import com.dessti.crm.calidad.domain.Probabilidad;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del Riesgo (Req 70.3, clausula 6.1.2; tarea 54.3). Es un
 * recurso <strong>separado</strong> de la Oportunidad_Calidad (clausula 6.1.3). El nivel
 * derivado es de solo lectura (lo calcula el dominio).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/riesgos} — identificar
 *       ({@code @autorizador.tiene('riesgo','crear')}); 201.</li>
 *   <li>{@code GET /calidad/riesgos?estado=&nivel=&page=&size=} — listado
 *       ({@code @autorizador.tiene('riesgo','listar')}).</li>
 *   <li>{@code GET /calidad/riesgos/{id}} — consulta
 *       ({@code @autorizador.tiene('riesgo','leer')}).</li>
 *   <li>{@code PUT /calidad/riesgos/{id}/estado} — cambiar estado
 *       ({@code @autorizador.tiene('riesgo','cambiar_estado')}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/calidad/riesgos")
public class RiesgosController {

    private final ServicioRiesgosOportunidades servicioRiesgos;

    public RiesgosController(ServicioRiesgosOportunidades servicioRiesgos) {
        this.servicioRiesgos = servicioRiesgos;
    }

    /**
     * Identifica un Riesgo (Req 70.3). 201 con el DTO (incluye el nivel derivado).
     *
     * @param request datos del riesgo.
     * @return 201 Created con el {@link RiesgoDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('riesgo','crear')")
    public ResponseEntity<RiesgoDto> identificar(@Valid @RequestBody IdentificarRiesgoRequest request) {
        Probabilidad probabilidad = ParseoCalidad.probabilidadRequerida(request.probabilidad());
        Impacto impacto = ParseoCalidad.impactoRequerido(request.impacto());
        IdentificarRiesgoCommand comando = new IdentificarRiesgoCommand(
                request.descripcion(), probabilidad, impacto, request.acciones());
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioRiesgos.identificarRiesgo(comando));
    }

    /**
     * Lista los Riesgos del tenant de forma paginada (20/100) con filtros opcionales
     * (Req 70.3, 12).
     *
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param nivel  etiqueta de nivel derivado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link RiesgoDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('riesgo','listar')")
    public PaginaResponse<RiesgoDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "nivel", required = false) String nivel,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        EstadoRiesgo estadoFiltro = ParseoCalidad.estadoRiesgoOpcional(estado);
        NivelRiesgo nivelFiltro = ParseoCalidad.nivelRiesgoOpcional(nivel);
        return PaginaResponse.de(servicioRiesgos.listarRiesgos(estadoFiltro, nivelFiltro, pageable));
    }

    /**
     * Consulta un Riesgo por su identificador (Req 23.3).
     *
     * @param id identificador del Riesgo.
     * @return 200 OK con el {@link RiesgoDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('riesgo','leer')")
    public ResponseEntity<RiesgoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioRiesgos.consultarRiesgo(id));
    }

    /**
     * Cambia el estado de un Riesgo (Req 70.3).
     *
     * @param id      identificador del Riesgo.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link RiesgoDto}.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.tiene('riesgo','cambiar_estado')")
    public ResponseEntity<RiesgoDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        EstadoRiesgo destino = ParseoCalidad.estadoRiesgoRequerido(request.estado());
        return ResponseEntity.ok(servicioRiesgos.cambiarEstadoRiesgo(id, destino));
    }
}

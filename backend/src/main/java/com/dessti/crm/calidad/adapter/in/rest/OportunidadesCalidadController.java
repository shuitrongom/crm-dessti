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

import com.dessti.crm.calidad.application.IdentificarOportunidadCalidadCommand;
import com.dessti.crm.calidad.application.OportunidadCalidadDto;
import com.dessti.crm.calidad.application.ServicioRiesgosOportunidades;
import com.dessti.crm.calidad.domain.EstadoOportunidadCalidad;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la Oportunidad_Calidad (Req 70.3, clausula 6.1.3; tarea
 * 54.3). Es un recurso <strong>separado</strong> del Riesgo (clausula 6.1.2), con su
 * propio ciclo de vida y acciones.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/oportunidades} — identificar
 *       ({@code @autorizador.tiene('oportunidad_calidad','crear')}); 201.</li>
 *   <li>{@code GET /calidad/oportunidades?estado=&page=&size=} — listado
 *       ({@code @autorizador.tiene('oportunidad_calidad','listar')}).</li>
 *   <li>{@code GET /calidad/oportunidades/{id}} — consulta
 *       ({@code @autorizador.tiene('oportunidad_calidad','leer')}).</li>
 *   <li>{@code PUT /calidad/oportunidades/{id}/estado} — cambiar estado
 *       ({@code @autorizador.tiene('oportunidad_calidad','cambiar_estado')}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/calidad/oportunidades")
public class OportunidadesCalidadController {

    private final ServicioRiesgosOportunidades servicioOportunidades;

    public OportunidadesCalidadController(ServicioRiesgosOportunidades servicioOportunidades) {
        this.servicioOportunidades = servicioOportunidades;
    }

    /**
     * Identifica una Oportunidad_Calidad (Req 70.3). 201 con el DTO.
     *
     * @param request datos de la oportunidad.
     * @return 201 Created con el {@link OportunidadCalidadDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('oportunidad_calidad','crear')")
    public ResponseEntity<OportunidadCalidadDto> identificar(
            @Valid @RequestBody IdentificarOportunidadCalidadRequest request) {
        IdentificarOportunidadCalidadCommand comando = new IdentificarOportunidadCalidadCommand(
                request.descripcion(), request.beneficioEsperado(), request.acciones());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicioOportunidades.identificarOportunidad(comando));
    }

    /**
     * Lista las Oportunidades de calidad del tenant de forma paginada (20/100) con filtro
     * opcional (Req 70.3, 12).
     *
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link OportunidadCalidadDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('oportunidad_calidad','listar')")
    public PaginaResponse<OportunidadCalidadDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        EstadoOportunidadCalidad estadoFiltro = ParseoCalidad.estadoOportunidadOpcional(estado);
        return PaginaResponse.de(servicioOportunidades.listarOportunidades(estadoFiltro, pageable));
    }

    /**
     * Consulta una Oportunidad_Calidad por su identificador (Req 23.3).
     *
     * @param id identificador de la Oportunidad_Calidad.
     * @return 200 OK con el {@link OportunidadCalidadDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('oportunidad_calidad','leer')")
    public ResponseEntity<OportunidadCalidadDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOportunidades.consultarOportunidad(id));
    }

    /**
     * Cambia el estado de una Oportunidad_Calidad (Req 70.3).
     *
     * @param id      identificador de la Oportunidad_Calidad.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link OportunidadCalidadDto}.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.tiene('oportunidad_calidad','cambiar_estado')")
    public ResponseEntity<OportunidadCalidadDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        EstadoOportunidadCalidad destino = ParseoCalidad.estadoOportunidadRequerido(request.estado());
        return ResponseEntity.ok(servicioOportunidades.cambiarEstadoOportunidad(id, destino));
    }
}

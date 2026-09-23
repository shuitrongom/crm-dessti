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

import com.dessti.crm.calidad.application.CambioSgcDto;
import com.dessti.crm.calidad.application.ProponerCambioSgcCommand;
import com.dessti.crm.calidad.application.ServicioCambiosSgc;
import com.dessti.crm.calidad.domain.EstadoCambioSgc;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del Cambio_SGC (Req 70.4, clausula 6.3; tarea 54.4). Propuesta,
 * aprobacion (auditada con actor y marca UTC), rechazo, implementacion y listado.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/cambios-sgc} — proponer
 *       ({@code @autorizador.tiene('cambio_sgc','crear')}); 201.</li>
 *   <li>{@code GET /calidad/cambios-sgc?estado=&page=&size=} — listado
 *       ({@code @autorizador.tiene('cambio_sgc','listar')}).</li>
 *   <li>{@code GET /calidad/cambios-sgc/{id}} — consulta
 *       ({@code @autorizador.tiene('cambio_sgc','leer')}).</li>
 *   <li>{@code PUT /calidad/cambios-sgc/{id}/aprobacion} — aprobar
 *       ({@code @autorizador.tiene('cambio_sgc','cambiar_estado')}).</li>
 *   <li>{@code PUT /calidad/cambios-sgc/{id}/rechazo} — rechazar
 *       ({@code @autorizador.tiene('cambio_sgc','cambiar_estado')}).</li>
 *   <li>{@code PUT /calidad/cambios-sgc/{id}/implementacion} — implementar
 *       ({@code @autorizador.tiene('cambio_sgc','cambiar_estado')}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/calidad/cambios-sgc")
public class CambiosSgcController {

    private final ServicioCambiosSgc servicioCambios;

    public CambiosSgcController(ServicioCambiosSgc servicioCambios) {
        this.servicioCambios = servicioCambios;
    }

    /**
     * Propone un Cambio_SGC (Req 70.4). 201 con el DTO.
     *
     * @param request datos del cambio.
     * @return 201 Created con el {@link CambioSgcDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('cambio_sgc','crear')")
    public ResponseEntity<CambioSgcDto> proponer(@Valid @RequestBody ProponerCambioSgcRequest request) {
        ProponerCambioSgcCommand comando = new ProponerCambioSgcCommand(
                request.titulo(), request.proposito(), request.consecuenciasPotenciales(),
                request.recursosNecesarios(), request.responsableId());
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioCambios.proponer(comando));
    }

    /**
     * Lista los Cambios_SGC del tenant de forma paginada (20/100) con filtro opcional
     * (Req 70.4, 12).
     *
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link CambioSgcDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('cambio_sgc','listar')")
    public PaginaResponse<CambioSgcDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        EstadoCambioSgc estadoFiltro = ParseoCalidad.estadoCambioSgcOpcional(estado);
        return PaginaResponse.de(servicioCambios.listar(estadoFiltro, pageable));
    }

    /**
     * Consulta un Cambio_SGC por su identificador (Req 23.3).
     *
     * @param id identificador del Cambio_SGC.
     * @return 200 OK con el {@link CambioSgcDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('cambio_sgc','leer')")
    public ResponseEntity<CambioSgcDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCambios.consultar(id));
    }

    /**
     * Aprueba un Cambio_SGC registrando el actor y la marca temporal UTC (Req 70.4).
     *
     * @param id identificador del Cambio_SGC.
     * @return 200 OK con el {@link CambioSgcDto} aprobado.
     */
    @PutMapping("/{id}/aprobacion")
    @PreAuthorize("@autorizador.tiene('cambio_sgc','cambiar_estado')")
    public ResponseEntity<CambioSgcDto> aprobar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCambios.aprobar(id));
    }

    /**
     * Rechaza un Cambio_SGC (estado final alterno, Req 70.4).
     *
     * @param id identificador del Cambio_SGC.
     * @return 200 OK con el {@link CambioSgcDto} rechazado.
     */
    @PutMapping("/{id}/rechazo")
    @PreAuthorize("@autorizador.tiene('cambio_sgc','cambiar_estado')")
    public ResponseEntity<CambioSgcDto> rechazar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCambios.rechazar(id));
    }

    /**
     * Marca un Cambio_SGC como implementado (Req 70.4).
     *
     * @param id identificador del Cambio_SGC.
     * @return 200 OK con el {@link CambioSgcDto} implementado.
     */
    @PutMapping("/{id}/implementacion")
    @PreAuthorize("@autorizador.tiene('cambio_sgc','cambiar_estado')")
    public ResponseEntity<CambioSgcDto> implementar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioCambios.implementar(id));
    }
}

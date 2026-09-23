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

import com.dessti.crm.calidad.application.NoConformidadDto;
import com.dessti.crm.calidad.application.RegistrarNoConformidadCommand;
import com.dessti.crm.calidad.application.ServicioNoConformidades;
import com.dessti.crm.calidad.domain.EstadoNoConformidad;
import com.dessti.crm.calidad.domain.OrigenNoConformidad;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la No_Conformidad (Req 70.2, 70.9; tarea 54.2). Registro,
 * cambio de estado y listado con filtros.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /calidad/no-conformidades} — registrar
 *       ({@code @autorizador.tiene('no_conformidad','crear')}); 201.</li>
 *   <li>{@code GET /calidad/no-conformidades?origen=&estado=&page=&size=} — listado
 *       ({@code @autorizador.tiene('no_conformidad','listar')}).</li>
 *   <li>{@code GET /calidad/no-conformidades/{id}} — consulta
 *       ({@code @autorizador.tiene('no_conformidad','leer')}); 404 si no accesible.</li>
 *   <li>{@code PUT /calidad/no-conformidades/{id}/estado} — cambiar estado
 *       ({@code @autorizador.tiene('no_conformidad','cambiar_estado')}).</li>
 * </ul>
 * <p>Los permisos se sembraron en V47 (rol {@code calidad}; lectura para {@code gerente} y
 * {@code admin_empresa}).</p>
 */
@RestController
@RequestMapping("/calidad/no-conformidades")
public class NoConformidadesController {

    private final ServicioNoConformidades servicioNoConformidades;

    public NoConformidadesController(ServicioNoConformidades servicioNoConformidades) {
        this.servicioNoConformidades = servicioNoConformidades;
    }

    /**
     * Registra una No_Conformidad (Req 70.2). 201 con el DTO.
     *
     * @param request datos de la No_Conformidad.
     * @return 201 Created con el {@link NoConformidadDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('no_conformidad','crear')")
    public ResponseEntity<NoConformidadDto> registrar(
            @Valid @RequestBody RegistrarNoConformidadRequest request) {
        OrigenNoConformidad origen = ParseoCalidad.origenNoConformidadRequerido(request.origen());
        RegistrarNoConformidadCommand comando = new RegistrarNoConformidadCommand(
                origen, request.descripcion(), request.procesoAfectado());
        NoConformidadDto dto = servicioNoConformidades.registrarNoConformidad(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las No_Conformidades del tenant de forma paginada (20/100) con filtros
     * opcionales (Req 70.2, 12).
     *
     * @param origen etiqueta del origen a filtrar; opcional.
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link NoConformidadDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('no_conformidad','listar')")
    public PaginaResponse<NoConformidadDto> listar(
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        OrigenNoConformidad origenFiltro = ParseoCalidad.origenNoConformidadOpcional(origen);
        EstadoNoConformidad estadoFiltro = ParseoCalidad.estadoNoConformidadOpcional(estado);
        return PaginaResponse.de(
                servicioNoConformidades.listarNoConformidades(origenFiltro, estadoFiltro, pageable));
    }

    /**
     * Consulta una No_Conformidad por su identificador (Req 23.3).
     *
     * @param id identificador de la No_Conformidad.
     * @return 200 OK con el {@link NoConformidadDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('no_conformidad','leer')")
    public ResponseEntity<NoConformidadDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNoConformidades.consultarNoConformidad(id));
    }

    /**
     * Cambia el estado de una No_Conformidad (Req 70.2).
     *
     * @param id      identificador de la No_Conformidad.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link NoConformidadDto}.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.tiene('no_conformidad','cambiar_estado')")
    public ResponseEntity<NoConformidadDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        EstadoNoConformidad destino = ParseoCalidad.estadoNoConformidadRequerido(request.estado());
        return ResponseEntity.ok(servicioNoConformidades.cambiarEstadoNoConformidad(id, destino));
    }
}

package com.dessti.crm.social.adapter.in.rest;

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

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.social.application.PlantillaMensajeDto;
import com.dessti.crm.social.application.ServicioPlantillas;
import com.dessti.crm.social.domain.CanalSocial;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la gestion de las {@link PlantillaMensajeDto
 * Plantillas de Mensaje} (Req 64.7, 12; tarea 40.3). Las plantillas aprobadas
 * habilitan el envio FUERA de la Ventana_Servicio.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /social/plantillas} — crear
 *       ({@code @autorizador.tiene('plantilla_mensaje','crear')}); 201 con el id.
 *       409 si ya existe por canal y nombre.</li>
 *   <li>{@code GET /social/plantillas/{id}} — consulta
 *       ({@code @autorizador.tiene('plantilla_mensaje','leer')}); 200; 404 si no es
 *       accesible.</li>
 *   <li>{@code GET /social/plantillas?canal=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('plantilla_mensaje','listar')}); 200 con
 *       {@link PaginaResponse}.</li>
 * </ul>
 *
 * <p>Los permisos {@code plantilla_mensaje:{crear,leer,listar}} se siembran en la
 * migracion V41 (roles {@code marketing} y {@code ventas}).</p>
 */
@RestController
@RequestMapping("/social/plantillas")
public class PlantillasController {

    private final ServicioPlantillas servicioPlantillas;

    public PlantillasController(ServicioPlantillas servicioPlantillas) {
        this.servicioPlantillas = servicioPlantillas;
    }

    /**
     * Crea una Plantilla_Mensaje (Req 64.7). 201 con el id.
     *
     * @param request cuerpo con canal, nombre, contenido y aprobacion.
     * @return 201 Created con el {@link PlantillaMensajeDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('plantilla_mensaje','crear')")
    public ResponseEntity<PlantillaMensajeDto> crear(
            @Valid @RequestBody CrearPlantillaMensajeRequest request) {
        CanalSocial canal = ParseoSocial.canalRequerido(request.canal());
        PlantillaMensajeDto dto = servicioPlantillas.crear(
                canal, request.nombre(), request.contenido(), request.aprobada());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Plantilla_Mensaje por su identificador (Req 23.3).
     *
     * @param id identificador de la plantilla.
     * @return 200 OK con el {@link PlantillaMensajeDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('plantilla_mensaje','leer')")
    public ResponseEntity<PlantillaMensajeDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPlantillas.consultar(id));
    }

    /**
     * Lista las Plantillas de Mensaje del tenant de forma paginada (20/100) con
     * filtro opcional por canal (Req 64.7).
     *
     * @param canal etiqueta del canal a filtrar; opcional.
     * @param page  numero de pagina 0-index; opcional.
     * @param size  tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PlantillaMensajeDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('plantilla_mensaje','listar')")
    public PaginaResponse<PlantillaMensajeDto> listar(
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPlantillas.listar(ParseoSocial.canalOpcional(canal), pageable));
    }
}

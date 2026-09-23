package com.dessti.crm.social.adapter.in.rest;

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

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.social.application.CrearPublicacionSocialCommand;
import com.dessti.crm.social.application.PublicacionSocialDto;
import com.dessti.crm.social.application.ServicioPublicaciones;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoPublicacion;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la Publicacion_Social (Req 65.1-65.6, 65.10, 65.11;
 * tarea 41.1). Expone la creacion, consulta, listado con filtros, cambio de estado
 * y publicacion via el adaptador con reintentos.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /social/publicaciones} — crear
 *       ({@code @autorizador.tiene('publicacion_social','crear')}); 201 (Req 65.1).</li>
 *   <li>{@code GET /social/publicaciones/{id}} — consultar
 *       ({@code @autorizador.tiene('publicacion_social','leer')}); 404 si no accesible.</li>
 *   <li>{@code GET /social/publicaciones?canal=&estado=&page=&size=} — listado paginado
 *       ({@code @autorizador.tiene('publicacion_social','listar')}) (Req 65.10).</li>
 *   <li>{@code PUT /social/publicaciones/{id}/estado} — cambio de estado manual
 *       ({@code @autorizador.tiene('publicacion_social','cambiar_estado')}) (Req 65.3, 65.4).</li>
 *   <li>{@code POST /social/publicaciones/{id}/publicar} — publicar con reintentos
 *       ({@code @autorizador.tiene('publicacion_social','cambiar_estado')}) (Req 65.5, 65.6).</li>
 * </ul>
 *
 * <p>Los permisos {@code publicacion_social:{crear,leer,listar,cambiar_estado}} se
 * sembraron en V5 y estan asignados al rol {@code marketing}.</p>
 */
@RestController
@RequestMapping("/social/publicaciones")
public class PublicacionesController {

    private final ServicioPublicaciones servicioPublicaciones;

    public PublicacionesController(ServicioPublicaciones servicioPublicaciones) {
        this.servicioPublicaciones = servicioPublicaciones;
    }

    /**
     * Crea una Publicacion_Social en estado {@code borrador} (Req 65.1, 65.2). 201.
     *
     * @param request cuenta de canal, contenido y fecha programada.
     * @return 201 Created con el {@link PublicacionSocialDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('publicacion_social','crear')")
    public ResponseEntity<PublicacionSocialDto> crear(
            @Valid @RequestBody CrearPublicacionSocialRequest request) {
        CrearPublicacionSocialCommand comando = new CrearPublicacionSocialCommand(
                request.cuentaCanalSocialId(), request.contenido(), request.fechaProgramada());
        PublicacionSocialDto dto = servicioPublicaciones.crearPublicacion(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Publicacion_Social por su identificador (Req 23.3).
     *
     * @param id identificador de la Publicacion_Social.
     * @return 200 OK con el {@link PublicacionSocialDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('publicacion_social','leer')")
    public ResponseEntity<PublicacionSocialDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPublicaciones.consultar(id));
    }

    /**
     * Lista las Publicacion_Social del tenant de forma paginada (20/100) con filtros
     * opcionales por Canal_Social y estado (Req 65.10).
     *
     * @param canal  etiqueta del canal a filtrar; opcional.
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link PublicacionSocialDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('publicacion_social','listar')")
    public PaginaResponse<PublicacionSocialDto> listar(
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        CanalSocial canalFiltro = ParseoPublicacion.canalOpcional(canal);
        EstadoPublicacion estadoFiltro = ParseoPublicacion.estadoPublicacionOpcional(estado);
        return PaginaResponse.de(
                servicioPublicaciones.listarPublicaciones(canalFiltro, estadoFiltro, pageable));
    }

    /**
     * Aplica un cambio de estado manual a la Publicacion_Social (Req 65.3, 65.4).
     * Solo {@code programada} es una transicion manual valida.
     *
     * @param id      identificador de la Publicacion_Social.
     * @param request estado destino.
     * @return 200 OK con el {@link PublicacionSocialDto} tras la transicion.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('publicacion_social','cambiar_estado')")
    public ResponseEntity<PublicacionSocialDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoPublicacionRequest request) {
        EstadoPublicacion destino = ParseoPublicacion.estadoPublicacionRequerido(request.estado());
        return ResponseEntity.ok(servicioPublicaciones.cambiarEstado(id, destino));
    }

    /**
     * Publica una Publicacion_Social {@code programada} via el adaptador con politica
     * de reintentos (Req 65.5, 65.6). Devuelve la publicacion en su estado final
     * ({@code publicada} o {@code fallida}).
     *
     * @param id identificador de la Publicacion_Social.
     * @return 200 OK con el {@link PublicacionSocialDto} en su estado final.
     */
    @PostMapping("/{id}/publicar")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('publicacion_social','cambiar_estado')")
    public ResponseEntity<PublicacionSocialDto> publicar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPublicaciones.publicar(id));
    }
}

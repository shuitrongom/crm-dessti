package com.dessti.crm.notificaciones.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.notificaciones.application.NotificacionDto;
import com.dessti.crm.notificaciones.application.ServicioNotificaciones;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

/**
 * Adaptador de entrada REST del modulo notificaciones para la <strong>consulta</strong>
 * de {@link NotificacionDto Notificaciones} (Req 46, 12; tarea 43.1).
 *
 * <p>Las Notificaciones se generan principalmente por <em>eventos</em> (Req 46.1) a
 * traves del puerto de entrada {@code NotificacionPort}, que invocan los modulos
 * productores; por ello este controlador expone solo operaciones de lectura:</p>
 * <ul>
 *   <li>{@code GET /notificaciones/{id}} — consulta
 *       ({@code @autorizador.tiene('notificacion','leer')}); 200 OK; 404 si no es
 *       accesible (Req 23.3).</li>
 *   <li>{@code GET /notificaciones?estado=&evento=&canal=&page=&size=} — listado
 *       paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('notificacion','listar')}); 200 OK con
 *       {@link PaginaResponse}.</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code notificacion:{leer,listar}} se siembran en la migracion V42 y se asignan a
 * los roles {@code admin_empresa} y {@code gerente}.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se devuelven DTOs distintos de las entidades JPA; el {@code tenant_id} y el
 * actor se derivan del contexto. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores} (404 no encontrado, 422 filtro desconocido).</p>
 */
@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final ServicioNotificaciones servicioNotificaciones;

    public NotificacionController(ServicioNotificaciones servicioNotificaciones) {
        this.servicioNotificaciones = servicioNotificaciones;
    }

    /**
     * Consulta una Notificacion por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador de la Notificacion.
     * @return 200 OK con el {@link NotificacionDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('notificacion','leer')")
    public ResponseEntity<NotificacionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioNotificaciones.consultar(id));
    }

    /**
     * Lista las Notificaciones del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por estado, por evento de origen y por canal.
     *
     * @param estado etiqueta de estado a filtrar; opcional.
     * @param evento etiqueta de evento de origen a filtrar; opcional.
     * @param canal  etiqueta de canal a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link NotificacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('notificacion','listar')")
    public PaginaResponse<NotificacionDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "evento", required = false) String evento,
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioNotificaciones.listar(estado, evento, canal, pageable));
    }
}

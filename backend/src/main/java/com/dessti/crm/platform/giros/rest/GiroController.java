package com.dessti.crm.platform.giros.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

import com.dessti.crm.platform.giros.GiroDto;
import com.dessti.crm.platform.giros.application.ServicioGiros;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST para la administracion de plataforma del catalogo de
 * Giros (verticales de negocio) por el {@code super_admin} (Req 1.1, 1.6,
 * tarea 2.6).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /plataforma/giros} — da de alta un Giro (Req 1.1); 201 con el
 *       Giro creado.</li>
 *   <li>{@code GET /plataforma/giros} — lista paginada (20/100) filtrable por
 *       estado {@code activo} (Req 1.6).</li>
 *   <li>{@code POST /plataforma/giros/{id}/activar} — activa un Giro (Req 1.1);
 *       200 con el Giro.</li>
 *   <li>{@code POST /plataforma/giros/{id}/desactivar} — desactiva un Giro
 *       (Req 1.1); 200 con el Giro. Rechazado con 422 si el Giro esta en uso
 *       (Req 1.5).</li>
 *   <li>{@code DELETE /plataforma/giros/{id}} — elimina un Giro; 204. Rechazado
 *       con 422 si el Giro tiene reglas de negocio programadas o esta en uso.</li>
 * </ul>
 *
 * <h2>Autorizacion de plataforma (Req 1.1)</h2>
 * <p>Cada operacion exige el permiso atomico de plataforma correspondiente sobre
 * el recurso {@code giro} ({@code @autorizador.tiene('giro', ...)}). Estos
 * permisos ({@code giro:crear}, {@code giro:listar}, {@code giro:activar},
 * {@code giro:desactivar}, {@code giro:eliminar}) se sembraron en V50 y V56 y se
 * asignaron <strong>unicamente</strong> al rol {@code super_admin}. Ningun rol de empresa
 * los posee, por lo que Spring Security responde 403 (denegacion por defecto) a
 * cualquier otro usuario. El recurso {@code giro} esta clasificado como recurso
 * de plataforma en {@code ClasificadorRecursosPlataforma}.</p>
 *
 * <h2>Estilo reutilizado</h2>
 * <p>Este controlador replica <em>exactamente</em> el patron de
 * {@code platform.empresas.rest.EmpresaController}: {@code @RestController} +
 * {@code @RequestMapping}, DTOs de peticion/respuesta ({@code record}) distintos
 * de la entidad, validacion Bean Validation ({@code @Valid}), paginacion via
 * {@link PageRequestFactory#acotando(Integer, Integer)} y respuesta paginada
 * {@link PaginaResponse} ({@code content/page/size/totalElements/totalPages}).
 * La proyeccion entidad&rarr;DTO la realiza {@code ServicioGiros}, que devuelve
 * ya {@link GiroDto} enriquecidos con la completitud del Giro (Base/Completo)
 * derivada del {@code RegistroVerticales}. Las operaciones {@code activar}/
 * {@code desactivar} operan por {@code id} sin cuerpo, por lo que no usan
 * {@code If-Match}/{@code version}.</p>
 */
@RestController
@RequestMapping("/plataforma/giros")
public class GiroController {

    private final ServicioGiros servicioGiros;

    public GiroController(ServicioGiros servicioGiros) {
        this.servicioGiros = servicioGiros;
    }

    /**
     * Da de alta un Giro (Req 1.1). Una clave duplicada produce 409; una clave o
     * un nombre visible invalidos, 422 (validados por el dominio).
     *
     * @param request cuerpo validado con la clave, el nombre visible y la
     *                descripcion opcional.
     * @return 201 con el Giro creado proyectado a {@link GiroDto}.
     */
    @PostMapping
    @PreAuthorize("@autorizador.tiene('giro','crear')")
    public ResponseEntity<GiroDto> crear(@Valid @RequestBody CrearGiroRequest request) {
        GiroDto dto = servicioGiros.crear(
                request.clave(),
                request.nombreVisible(),
                request.descripcion());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Edita los datos de un Giro (bugfix edicion de Giro): nombre visible y
     * descripcion. La clave canonica es inmutable y no se modifica.
     * @param id      identificador del Giro a editar.
     * @param request nuevos datos editables del Giro.
     * @return 200 con el Giro actualizado (GiroDto); 404/409/422 segun el caso.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('giro','actualizar')")
    public ResponseEntity<GiroDto> actualizar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarGiroRequest request) {
        return ResponseEntity.ok(
                servicioGiros.actualizar(id, request.nombreVisible(), request.descripcion()));
    }

    /**
     * Lista Giros de forma paginada (20 por defecto, 100 maximo) filtrable por
     * estado {@code activo} (Req 1.6). El {@code size} superior al maximo se acota
     * conforme a la politica de paginacion estandar.
     *
     * @param activo filtro opcional por estado; {@code null} lista todos.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return la pagina de Giros proyectada a {@link GiroDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.tiene('giro','listar')")
    public PaginaResponse<GiroDto> listar(
            @RequestParam(name = "activo", required = false) Boolean activo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioGiros.listar(activo, pageable));
    }

    /**
     * Activa un Giro (Req 1.1): queda disponible para asignarse a Empresas. Un
     * Giro inexistente produce 404.
     *
     * @param id identificador del Giro.
     * @return 200 con el Giro activado proyectado a {@link GiroDto}.
     */
    @PostMapping("/{id}/activar")
    @PreAuthorize("@autorizador.tiene('giro','activar')")
    public ResponseEntity<GiroDto> activar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioGiros.activar(id));
    }

    /**
     * Desactiva un Giro (baja logica, Req 1.1). Un Giro inexistente produce 404;
     * un Giro en uso por al menos una Empresa, 422 con el conteo (Req 1.5).
     *
     * @param id identificador del Giro.
     * @return 200 con el Giro desactivado proyectado a {@link GiroDto}.
     */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("@autorizador.tiene('giro','desactivar')")
    public ResponseEntity<GiroDto> desactivar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioGiros.desactivar(id));
    }

    /**
     * Elimina definitivamente un Giro del catalogo (baja fisica). Un Giro
     * inexistente produce 404; un Giro con reglas de negocio programadas (Giro
     * "Completo") o en uso por al menos una Empresa produce 422 con el motivo.
     *
     * @param id identificador del Giro.
     * @return 204 No Content si la eliminacion se aplica.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.tiene('giro','eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable("id") UUID id) {
        servicioGiros.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.dessti.crm.comercial.actividad.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.dessti.crm.comercial.actividad.application.ActividadDto;
import com.dessti.crm.comercial.actividad.application.CrearActividadCommand;
import com.dessti.crm.comercial.actividad.application.EditarActividadCommand;
import com.dessti.crm.comercial.actividad.application.ServicioActividades;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo comercial-crm para la gestion de las
 * {@link ActividadDto Actividades} de seguimiento (V79).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /actividades} — alta ({@code actividad:crear}); 201 Created.
 *       422 si los datos son invalidos; 404 si el Cliente/Oportunidad no existen.</li>
 *   <li>{@code GET /actividades/{id}} — consulta ({@code actividad:leer}); 200 OK;
 *       404 si no es accesible.</li>
 *   <li>{@code PUT /actividades/{id}} — edicion de asunto/descripcion
 *       ({@code actividad:actualizar}); 200 OK.</li>
 *   <li>{@code PUT /actividades/{id}/completar} — completar
 *       ({@code actividad:actualizar}); 200 OK; 409 si esta cancelada.</li>
 *   <li>{@code PUT /actividades/{id}/cancelar} — cancelar
 *       ({@code actividad:actualizar}); 200 OK; 409 si esta completada.</li>
 *   <li>{@code PUT /actividades/{id}/reprogramar} — reprogramar
 *       ({@code actividad:actualizar}); 200 OK; 422 si ya es final.</li>
 *   <li>{@code PUT /actividades/{id}/responsable} — asignar responsable
 *       ({@code actividad:actualizar}); 200 OK.</li>
 *   <li>{@code DELETE /actividades/{id}} — eliminar ({@code actividad:eliminar});
 *       204 No Content.</li>
 *   <li>{@code GET /actividades?clienteId=&oportunidadId=&tipo=&estado=&responsableId=&page=&size=}
 *       — listado paginado (20/100) ordenado por fecha programada descendente
 *       ({@code actividad:listar}); 200 OK con {@link PaginaResponse}.</li>
 * </ul>
 *
 * <h2>Autorizacion</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene('actividad', operacion)} y que el modulo comercial
 * este habilitado. Los permisos {@code actividad:{crear,leer,listar,actualizar,
 * eliminar}} se sembraron en V79.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/actividades")
public class ActividadController {

    /** Campo de ordenamiento del timeline: fecha programada descendente. */
    private static final String CAMPO_ORDEN = "fechaProgramada";

    private final ServicioActividades servicioActividades;

    public ActividadController(ServicioActividades servicioActividades) {
        this.servicioActividades = servicioActividades;
    }

    /**
     * Registra una Actividad de seguimiento. 422 si los datos son invalidos; 404
     * si el Cliente o la Oportunidad no existen en el tenant.
     *
     * @param request datos de la Actividad a registrar.
     * @return 201 Created con el {@link ActividadDto} creado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','crear')")
    public ResponseEntity<ActividadDto> crear(@Valid @RequestBody CrearActividadRequest request) {
        ActividadDto dto = servicioActividades.registrar(new CrearActividadCommand(
                request.clienteId(), request.oportunidadId(), request.tipo(), request.asunto(),
                request.descripcion(), request.fechaProgramada(), request.vencimiento(),
                request.responsableUsuarioId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Actividad por su identificador. 404 si no es accesible.
     *
     * @param id identificador de la Actividad.
     * @return 200 OK con el {@link ActividadDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','leer')")
    public ResponseEntity<ActividadDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioActividades.consultar(id));
    }

    /**
     * Edita el asunto y la descripcion de una Actividad. 404 si no es accesible;
     * 422 si los datos son invalidos.
     *
     * @param id      identificador de la Actividad.
     * @param request nuevos asunto y descripcion.
     * @return 200 OK con el {@link ActividadDto} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','actualizar')")
    public ResponseEntity<ActividadDto> editar(@PathVariable("id") UUID id,
                                               @Valid @RequestBody EditarActividadRequest request) {
        return ResponseEntity.ok(servicioActividades.editar(id,
                new EditarActividadCommand(request.asunto(), request.descripcion())));
    }

    /**
     * Marca una Actividad como completada. 404 si no es accesible; 409 si esta
     * cancelada.
     *
     * @param id identificador de la Actividad.
     * @return 200 OK con el {@link ActividadDto} actualizado.
     */
    @PutMapping("/{id}/completar")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','actualizar')")
    public ResponseEntity<ActividadDto> completar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioActividades.completar(id));
    }

    /**
     * Marca una Actividad como cancelada. 404 si no es accesible; 409 si esta
     * completada.
     *
     * @param id identificador de la Actividad.
     * @return 200 OK con el {@link ActividadDto} actualizado.
     */
    @PutMapping("/{id}/cancelar")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','actualizar')")
    public ResponseEntity<ActividadDto> cancelar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioActividades.cancelar(id));
    }

    /**
     * Reprograma la fecha y el vencimiento de una Actividad no finalizada. 404 si
     * no es accesible; 422 si ya es final o las fechas son invalidas.
     *
     * @param id      identificador de la Actividad.
     * @param request nuevas fecha programada y vencimiento.
     * @return 200 OK con el {@link ActividadDto} actualizado.
     */
    @PutMapping("/{id}/reprogramar")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','actualizar')")
    public ResponseEntity<ActividadDto> reprogramar(@PathVariable("id") UUID id,
                                                    @Valid @RequestBody ReprogramarActividadRequest request) {
        return ResponseEntity.ok(
                servicioActividades.reprogramar(id, request.fechaProgramada(), request.vencimiento()));
    }

    /**
     * Asigna o limpia el Usuario responsable del seguimiento. 404 si no es accesible.
     *
     * @param id      identificador de la Actividad.
     * @param request identificador del Usuario responsable (nulo para desasignar).
     * @return 200 OK con el {@link ActividadDto} actualizado.
     */
    @PutMapping("/{id}/responsable")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','actualizar')")
    public ResponseEntity<ActividadDto> asignarResponsable(@PathVariable("id") UUID id,
                                                           @Valid @RequestBody AsignarResponsableRequest request) {
        return ResponseEntity.ok(servicioActividades.asignarResponsable(id, request.usuarioId()));
    }

    /**
     * Elimina una Actividad del tenant. 404 si no es accesible.
     *
     * @param id identificador de la Actividad.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable("id") UUID id) {
        servicioActividades.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lista las Actividades del tenant de forma paginada (20 por defecto, 100
     * maximo), ordenadas por fecha programada descendente (timeline), con filtros
     * opcionales por Cliente, Oportunidad, tipo, estado y responsable.
     *
     * @param clienteId     Cliente a filtrar; opcional.
     * @param oportunidadId Oportunidad a filtrar; opcional.
     * @param tipo          etiqueta de tipo a filtrar; opcional.
     * @param estado        etiqueta de estado a filtrar; opcional.
     * @param responsableId Usuario responsable a filtrar; opcional.
     * @param page          numero de pagina 0-index; opcional.
     * @param size          tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ActividadDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('comercial') and @autorizador.tiene('actividad','listar')")
    public PaginaResponse<ActividadDto> listar(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "oportunidadId", required = false) UUID oportunidadId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "responsableId", required = false) UUID responsableId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size, Sort.by(Sort.Direction.DESC, CAMPO_ORDEN));
        return PaginaResponse.de(
                servicioActividades.listar(clienteId, oportunidadId, tipo, estado, responsableId, pageable));
    }
}

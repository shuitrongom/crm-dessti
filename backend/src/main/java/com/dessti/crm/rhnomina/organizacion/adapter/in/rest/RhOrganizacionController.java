package com.dessti.crm.rhnomina.organizacion.adapter.in.rest;

import java.util.List;
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
import com.dessti.crm.rhnomina.organizacion.application.AsignacionPuestoDto;
import com.dessti.crm.rhnomina.organizacion.application.AsignarEmpleadoCommand;
import com.dessti.crm.rhnomina.organizacion.application.CrearPuestoCommand;
import com.dessti.crm.rhnomina.organizacion.application.EvaluacionDesempenoDto;
import com.dessti.crm.rhnomina.organizacion.application.OrganigramaNodoDto;
import com.dessti.crm.rhnomina.organizacion.application.PuestoDto;
import com.dessti.crm.rhnomina.organizacion.application.RegistrarEvaluacionCommand;
import com.dessti.crm.rhnomina.organizacion.application.ServicioOrganizacion;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo de <strong>organizacion de personal</strong>
 * de rhnomina para la gestion de {@link PuestoDto Puestos}, el organigrama, las
 * {@link AsignacionPuestoDto asignaciones} y las {@link EvaluacionDesempenoDto
 * Evaluacion_Desempeno} (Req 61; tarea 36.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /rh-nomina/puestos} — crea un Puesto
 *       ({@code @autorizador.tiene('puesto','crear')}); <strong>201 Created</strong>.
 *       422 si los datos o la jerarquia son invalidos, 404 si el superior no existe
 *       (Req 61.1, 61.7).</li>
 *   <li>{@code GET /rh-nomina/puestos/{id}} — consulta un Puesto
 *       ({@code @autorizador.tiene('puesto','leer')}); <strong>200 OK</strong>. 404
 *       si no es accesible.</li>
 *   <li>{@code GET /rh-nomina/puestos?nombre=&activo=&page=&size=} — listado
 *       paginado ({@code @autorizador.tiene('puesto','listar')});
 *       <strong>200 OK</strong> (20/100, Req 61.4).</li>
 *   <li>{@code PUT /rh-nomina/puestos/{id}/superior} — mueve el Puesto en la
 *       jerarquia ({@code @autorizador.tiene('puesto','actualizar')});
 *       <strong>200 OK</strong>. 422 si formaria un ciclo, 404 si el Puesto o el
 *       nuevo superior no son accesibles (Req 61.1, 61.7).</li>
 *   <li>{@code POST /rh-nomina/asignaciones-puesto} — asigna un Empleado a un
 *       Puesto ({@code @autorizador.tiene('puesto','actualizar')});
 *       <strong>201 Created</strong>. 404 si el Empleado o el Puesto no existen
 *       (Req 61.2).</li>
 *   <li>{@code POST /rh-nomina/evaluaciones} — registra una Evaluacion_Desempeno
 *       ({@code @autorizador.tiene('evaluacion_desempeno','crear')});
 *       <strong>201 Created</strong>. 404 si el Empleado no existe, 422 si la
 *       calificacion esta fuera de escala (Req 61.3, 61.8).</li>
 *   <li>{@code GET /rh-nomina/evaluaciones?empleadoId=&periodo=&page=&size=} —
 *       listado paginado ({@code @autorizador.tiene('evaluacion_desempeno','listar')});
 *       <strong>200 OK</strong> (20/100, filtro por Empleado o periodo, Req 61.4).</li>
 *   <li>{@code GET /rh-nomina/organigrama} — organigrama derivado de solo lectura
 *       ({@code @autorizador.tiene('organigrama','leer')}); <strong>200 OK</strong>
 *       con el bosque de nodos (Req 61.1).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 61)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. V5 sembro
 * {@code puesto:{crear,leer,listar}}, {@code organigrama:leer} y
 * {@code evaluacion_desempeno:{crear,leer}} enlazados al rol {@code rh}; V36 agrega
 * {@code puesto:actualizar} (mover en la jerarquia y asignar Empleados) y
 * {@code evaluacion_desempeno:listar}. La asignacion de Empleado a Puesto reutiliza
 * {@code puesto:actualizar} por ser una modificacion de la estructura del Puesto.
 * Sin el permiso, Spring Security responde 403 por denegacion por defecto (Req 3.2).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA. El {@code tenant_id}
 * y el actor se derivan del contexto y nunca se aceptan en la peticion. El manejo
 * de errores lo centraliza el manejador global.</p>
 */
@RestController
@RequestMapping("/rh-nomina")
public class RhOrganizacionController {

    private final ServicioOrganizacion servicioOrganizacion;

    public RhOrganizacionController(ServicioOrganizacion servicioOrganizacion) {
        this.servicioOrganizacion = servicioOrganizacion;
    }

    /**
     * Crea un Puesto y lo situa en la jerarquia (Req 61.1, 61.7). 422 si los datos
     * o la jerarquia son invalidos; 404 si el superior indicado no existe.
     *
     * @param request datos del Puesto.
     * @return 201 Created con el {@link PuestoDto} creado.
     */
    @PostMapping("/puestos")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('puesto','crear')")
    public ResponseEntity<PuestoDto> crearPuesto(@Valid @RequestBody CrearPuestoRequest request) {
        PuestoDto dto = servicioOrganizacion.crearPuesto(new CrearPuestoCommand(
                request.nombre(),
                request.descripcion(),
                request.puestoSuperiorId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Puesto por su identificador (Req 4.3, 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador del Puesto.
     * @return 200 OK con el {@link PuestoDto}.
     */
    @GetMapping("/puestos/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('puesto','leer')")
    public ResponseEntity<PuestoDto> consultarPuesto(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOrganizacion.consultarPuesto(id));
    }

    /**
     * Lista los Puestos del tenant de forma paginada (20 por defecto, 100 maximo)
     * filtrando por nombre o por estado sin distinguir mayusculas (Req 61.4).
     *
     * @param nombre subcadena a buscar en el nombre; opcional.
     * @param activo estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PuestoDto}.
     */
    @GetMapping("/puestos")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('puesto','listar')")
    public PaginaResponse<PuestoDto> listarPuestos(
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "activo", required = false) Boolean activo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioOrganizacion.listarPuestos(nombre, activo, pageable));
    }

    /**
     * Mueve un Puesto en la jerarquia cambiando su superior directo (Req 61.1,
     * 61.7). 422 si formaria un ciclo; 404 si el Puesto o el nuevo superior no son
     * accesibles.
     *
     * @param id      identificador del Puesto a mover.
     * @param request nuevo superior directo ({@code null} para dejarlo raiz).
     * @return 200 OK con el {@link PuestoDto} actualizado.
     */
    @PutMapping("/puestos/{id}/superior")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('puesto','actualizar')")
    public ResponseEntity<PuestoDto> moverPuesto(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MoverPuestoRequest request) {
        return ResponseEntity.ok(servicioOrganizacion.moverPuesto(id, request.nuevoSuperiorId()));
    }

    /**
     * Asigna un Empleado a un Puesto (Req 61.2). 404 si el Empleado o el Puesto no
     * existen; 422 si los datos son invalidos.
     *
     * @param request datos de la asignacion.
     * @return 201 Created con el {@link AsignacionPuestoDto} creado.
     */
    @PostMapping("/asignaciones-puesto")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('puesto','actualizar')")
    public ResponseEntity<AsignacionPuestoDto> asignarEmpleado(
            @Valid @RequestBody AsignarEmpleadoRequest request) {
        AsignacionPuestoDto dto = servicioOrganizacion.asignarEmpleado(new AsignarEmpleadoCommand(
                request.empleadoId(),
                request.puestoId(),
                request.fechaInicio()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Registra una Evaluacion_Desempeno de un Empleado en un periodo (Req 61.3,
     * 61.8), conservando el historial. 404 si el Empleado no existe; 422 si la
     * calificacion esta fuera de escala.
     *
     * @param request datos de la evaluacion.
     * @return 201 Created con el {@link EvaluacionDesempenoDto} creado.
     */
    @PostMapping("/evaluaciones")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('evaluacion_desempeno','crear')")
    public ResponseEntity<EvaluacionDesempenoDto> registrarEvaluacion(
            @Valid @RequestBody RegistrarEvaluacionRequest request) {
        EvaluacionDesempenoDto dto = servicioOrganizacion.registrarEvaluacion(
                new RegistrarEvaluacionCommand(
                        request.empleadoId(),
                        request.periodo(),
                        request.calificacion(),
                        request.comentarios()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista las Evaluacion_Desempeno del tenant de forma paginada (20/100)
     * filtrando por Empleado o por periodo (Req 61.4).
     *
     * @param empleadoId Empleado a filtrar; opcional.
     * @param periodo    periodo a filtrar; opcional.
     * @param page       numero de pagina 0-index; opcional.
     * @param size       tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link EvaluacionDesempenoDto}.
     */
    @GetMapping("/evaluaciones")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('evaluacion_desempeno','listar')")
    public PaginaResponse<EvaluacionDesempenoDto> listarEvaluaciones(
            @RequestParam(name = "empleadoId", required = false) UUID empleadoId,
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioOrganizacion.listarEvaluaciones(empleadoId, periodo, pageable));
    }

    /**
     * Devuelve el organigrama derivado del tenant como un bosque de nodos de solo
     * lectura (Req 61.1).
     *
     * @return 200 OK con la lista de nodos raiz del organigrama.
     */
    @GetMapping("/organigrama")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('organigrama','leer')")
    public ResponseEntity<List<OrganigramaNodoDto>> consultarOrganigrama() {
        return ResponseEntity.ok(servicioOrganizacion.consultarOrganigrama());
    }
}

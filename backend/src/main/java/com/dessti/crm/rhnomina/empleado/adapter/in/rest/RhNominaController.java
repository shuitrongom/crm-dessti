package com.dessti.crm.rhnomina.empleado.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.rhnomina.empleado.application.AltaEmpleadoCommand;
import com.dessti.crm.rhnomina.empleado.application.ContratoLaboralDto;
import com.dessti.crm.rhnomina.empleado.application.EmpleadoDto;
import com.dessti.crm.rhnomina.empleado.application.IncidenciaDto;
import com.dessti.crm.rhnomina.empleado.application.RegistrarIncidenciaCommand;
import com.dessti.crm.rhnomina.empleado.application.ServicioEmpleados;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo base de RH/nomina para la gestion de
 * {@link EmpleadoDto Empleados}, sus {@link ContratoLaboralDto Contratos} e
 * {@link IncidenciaDto Incidencias} (Req 40; tarea 34.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /rh-nomina/empleados} — alta de Empleado + Contrato_Laboral
 *       ({@code @autorizador.tiene('empleado','crear')}); <strong>201 Created</strong>
 *       con el {@link EmpleadoDto}. 422 si los datos (incluidos RFC/CURP/NSS) son
 *       invalidos, 409 si el RFC ya existe entre activos (Req 40.1, 40.2).</li>
 *   <li>{@code GET /rh-nomina/empleados/{id}} — consulta un Empleado
 *       ({@code @autorizador.tiene('empleado','leer')}); <strong>200 OK</strong>.
 *       404 si no existe/otro tenant (Req 4.3, 23.3).</li>
 *   <li>{@code GET /rh-nomina/empleados?nombre=&activo=&page=&size=} — listado
 *       paginado ({@code @autorizador.tiene('empleado','listar')});
 *       <strong>200 OK</strong> con {@link PaginaResponse}. Tamano por defecto 20,
 *       maximo 100; filtro por nombre o por estado sin distinguir mayusculas
 *       (Req 40.5, 40.6).</li>
 *   <li>{@code DELETE /rh-nomina/empleados/{id}} — baja logica del Empleado
 *       ({@code @autorizador.tiene('empleado','eliminar')}); <strong>200 OK</strong>
 *       con el {@link EmpleadoDto} dado de baja (se conserva el historico). 404 si
 *       no existe/ya inactivo/otro tenant (Req 40.4).</li>
 *   <li>{@code POST /rh-nomina/empleados/{id}/incidencias} — registra una
 *       Incidencia ({@code @autorizador.tiene('incidencia','crear')});
 *       <strong>201 Created</strong> con el {@link IncidenciaDto}. 404 si el
 *       Empleado no existe/otro tenant, 422 si los datos son invalidos (Req 40.3).</li>
 *   <li>{@code GET /rh-nomina/empleados/{id}/incidencias} — historico de
 *       Incidencia ({@code @autorizador.tiene('incidencia','listar')});
 *       <strong>200 OK</strong> con {@link PaginaResponse} (Req 40.3, 40.4).</li>
 *   <li>{@code GET /rh-nomina/empleados/{id}/contratos} — historico de
 *       Contrato_Laboral ({@code @autorizador.tiene('contrato_laboral','listar')});
 *       <strong>200 OK</strong> con {@link PaginaResponse} (Req 40.4).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 40)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code empleado:{crear,leer,listar,actualizar}}, {@code contrato_laboral:{crear,
 * leer,listar}} e {@code incidencia:{crear,leer,listar}} se sembraron en V5 y se
 * asignaron al rol {@code rh}; el permiso {@code empleado:eliminar} (baja logica)
 * se agrega en V32 y se enlaza al rol {@code rh}, replicando lo que V11 hizo para
 * {@code cliente:eliminar}. Sin el permiso, Spring Security responde 403 por
 * denegacion por defecto (Req 3.2).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA. Los cuerpos de
 * entrada ({@code *Request}) se traducen a comandos de aplicacion en el
 * controlador; el {@code tenant_id} y el actor se derivan del contexto y nunca se
 * aceptan en la peticion. El manejo de errores lo centraliza el manejador global:
 * el controlador se limita a dejar propagar las excepciones de dominio.</p>
 */
@RestController
@RequestMapping("/rh-nomina")
public class RhNominaController {

    private final ServicioEmpleados servicioEmpleados;

    public RhNominaController(ServicioEmpleados servicioEmpleados) {
        this.servicioEmpleados = servicioEmpleados;
    }

    /**
     * Da de alta un Empleado junto con su primer Contrato_Laboral (Req 40.1,
     * 40.2). 422 si los datos son invalidos; 409 si ya existe un Empleado activo
     * con el mismo RFC.
     *
     * @param request datos del Empleado y de su Contrato_Laboral.
     * @return 201 Created con el {@link EmpleadoDto} creado.
     */
    @PostMapping("/empleados")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('empleado','crear')")
    public ResponseEntity<EmpleadoDto> altaEmpleado(@Valid @RequestBody AltaEmpleadoRequest request) {
        EmpleadoDto dto = servicioEmpleados.altaEmpleado(new AltaEmpleadoCommand(
                request.nombre(),
                request.rfc(),
                request.curp(),
                request.nss(),
                request.fechaIngreso(),
                request.tipoContrato(),
                request.salarioDiario(),
                request.periodicidad(),
                request.fechaInicio()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Empleado por su identificador (Req 4.3, 23.3). 404 si no existe,
     * esta inactivo o pertenece a otro tenant.
     *
     * @param id identificador del Empleado.
     * @return 200 OK con el {@link EmpleadoDto}.
     */
    @GetMapping("/empleados/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('empleado','leer')")
    public ResponseEntity<EmpleadoDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioEmpleados.consultarEmpleado(id));
    }

    /**
     * Lista los Empleados del tenant de forma paginada (20 por defecto, 100
     * maximo) filtrando por nombre o por estado sin distinguir mayusculas
     * (Req 40.5, 40.6). Un {@code nombre} nulo/blanco no filtra por nombre; un
     * {@code activo} nulo no filtra por estado; el {@code size} superior al maximo
     * se acota a 100.
     *
     * @param nombre subcadena a buscar en el nombre; opcional.
     * @param activo estado a filtrar; opcional.
     * @param page   numero de pagina 0-index; opcional.
     * @param size   tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link EmpleadoDto}.
     */
    @GetMapping("/empleados")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('empleado','listar')")
    public PaginaResponse<EmpleadoDto> listar(
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "activo", required = false) Boolean activo,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioEmpleados.listarEmpleados(nombre, activo, pageable));
    }

    /**
     * Realiza la baja logica de un Empleado activo (Req 40.4): lo marca inactivo
     * conservando su historico de Contrato_Laboral e Incidencia. Se responde 200
     * OK con el {@link EmpleadoDto} dado de baja; 404 si no existe, ya esta
     * inactivo o pertenece a otro tenant.
     *
     * @param id identificador del Empleado.
     * @return 200 OK con el {@link EmpleadoDto} dado de baja.
     */
    @DeleteMapping("/empleados/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('empleado','eliminar')")
    public ResponseEntity<EmpleadoDto> darDeBaja(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioEmpleados.darDeBaja(id));
    }

    /**
     * Registra una Incidencia para un Empleado activo en un Periodo_Nomina
     * (Req 40.3). El identificador del Empleado se toma de la ruta; el cuerpo
     * aporta los datos de la Incidencia. 404 si el Empleado no existe/otro tenant,
     * 422 si los datos son invalidos.
     *
     * @param id      identificador del Empleado.
     * @param request datos de la Incidencia.
     * @return 201 Created con el {@link IncidenciaDto} creado.
     */
    @PostMapping("/empleados/{id}/incidencias")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('incidencia','crear')")
    public ResponseEntity<IncidenciaDto> registrarIncidencia(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RegistrarIncidenciaRequest request) {
        IncidenciaDto dto = servicioEmpleados.registrarIncidencia(new RegistrarIncidenciaCommand(
                id,
                request.periodoNomina(),
                request.tipo(),
                request.cantidad(),
                request.descripcion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lista el historico de Incidencia de un Empleado accesible del tenant de
     * forma paginada (Req 40.3, 40.4). 404 si el Empleado no es accesible.
     *
     * @param id   identificador del Empleado.
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link IncidenciaDto}.
     */
    @GetMapping("/empleados/{id}/incidencias")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('incidencia','listar')")
    public PaginaResponse<IncidenciaDto> listarIncidencias(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioEmpleados.listarIncidencias(id, pageable));
    }

    /**
     * Lista el historico de Contrato_Laboral de un Empleado accesible del tenant
     * de forma paginada (Req 40.4). 404 si el Empleado no es accesible.
     *
     * @param id   identificador del Empleado.
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ContratoLaboralDto}.
     */
    @GetMapping("/empleados/{id}/contratos")
    @PreAuthorize("@autorizador.moduloHabilitado('rh-nomina') and @autorizador.tiene('contrato_laboral','listar')")
    public PaginaResponse<ContratoLaboralDto> listarContratos(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioEmpleados.listarContratos(id, pageable));
    }
}

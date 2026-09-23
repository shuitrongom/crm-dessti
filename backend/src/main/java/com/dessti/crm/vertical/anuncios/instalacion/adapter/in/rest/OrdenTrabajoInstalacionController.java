package com.dessti.crm.vertical.anuncios.instalacion.adapter.in.rest;

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

import com.dessti.crm.vertical.anuncios.instalacion.application.EvidenciaInstalacionDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.OrdenTrabajoInstalacionDetalleDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.OrdenTrabajoInstalacionDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.PendienteInstalacionDto;
import com.dessti.crm.vertical.anuncios.instalacion.application.ServicioOrdenesTrabajoInstalacion;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del submodulo de instalacion para la gestion de las
 * {@link OrdenTrabajoInstalacionDto Ordenes de Trabajo de Instalacion} (Req 19, 12;
 * tarea 22.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /ordenes-trabajo-instalacion} — programar a partir de una
 *       Orden_Fabricacion terminada
 *       ({@code @autorizador.tiene('orden_trabajo_instalacion','crear')}); 201
 *       Created con el id (Req 19.1). 404 si la OF no existe; 422 si no esta
 *       terminada (Req 19.2) o el Sitio no cumple los requisitos (Req 19.3).</li>
 *   <li>{@code GET /ordenes-trabajo-instalacion/{id}} — consulta de detalle
 *       enriquecida con los pendientes y las evidencias vinculados
 *       ({@code @autorizador.tiene('orden_trabajo_instalacion','leer')}); 200 OK con
 *       {@link OrdenTrabajoInstalacionDetalleDto} (listas vacias cuando no haya);
 *       404 si no es accesible (Req 8.1, 8.2, 8.3, 23.3).</li>
 *   <li>{@code GET /ordenes-trabajo-instalacion/{id}/pendientes} — lista de
 *       pendientes (cada uno con {@code resuelto})
 *       ({@code @autorizador.tiene('orden_trabajo_instalacion','leer')}); 200 OK con
 *       {@code List<PendienteInstalacionDto>} (incluye {@code []}); 404 si la OTI no
 *       es accesible (Req 8.2, 8.6).</li>
 *   <li>{@code GET /ordenes-trabajo-instalacion/{id}/evidencias} — lista de
 *       evidencias fotograficas
 *       ({@code @autorizador.tiene('orden_trabajo_instalacion','leer')}); 200 OK con
 *       {@code List<EvidenciaInstalacionDto>} (incluye {@code []}); 404 si la OTI no
 *       es accesible (Req 8.3, 8.6).</li>
 *   <li>{@code POST /ordenes-trabajo-instalacion/{id}/avance} — registro de avance:
 *       pendientes, evidencias y resoluciones (Req 19.4). Es una mutacion del
 *       agregado OTI, por lo que exige
 *       {@code @autorizador.tiene('orden_trabajo_instalacion','cambiar_estado')}
 *       (ver DECISION de permisos abajo); 200 OK.</li>
 *   <li>{@code PUT /ordenes-trabajo-instalacion/{id}/estado} — cambio de estado
 *       ({@code @autorizador.tiene('orden_trabajo_instalacion','cambiar_estado')});
 *       200 OK; 409 si la transicion es invalida (Req 19.5); 422 si se intenta
 *       completar con pendientes sin resolver (Req 19.6).</li>
 *   <li>{@code GET /ordenes-trabajo-instalacion?estado=&cuadrillaId=&clienteId=&page=&size=}
 *       — listado paginado (20/100) con filtros
 *       ({@code @autorizador.tiene('orden_trabajo_instalacion','listar')}); 200 OK
 *       con {@link PaginaResponse} (Req 19.7).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code orden_trabajo_instalacion:{crear,leer,listar,cambiar_estado}} ya se
 * sembraron en V5 y se asignaron a los roles {@code instalacion}/{@code supervisor},
 * por lo que la migracion V24 no siembra permisos adicionales.</p>
 *
 * <p><strong>DECISION (registro de avance):</strong> V5 no define una operacion
 * {@code actualizar} para {@code orden_trabajo_instalacion}. El registro de avance
 * (agregar pendientes/evidencias y resolver pendientes) muta el agregado OTI e
 * influye directamente en la guarda de cierre del Req 19.6; por coherencia se
 * protege con {@code cambiar_estado} (la operacion que gobierna la evolucion del
 * agregado), en lugar de {@code crear} (reservado a la programacion inicial).</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el
 * {@code tenant_id} y el actor se derivan del contexto. El manejo de errores lo
 * centraliza {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion
 * invalida, 404 no encontrado).</p>
 */
@RestController
@RequestMapping("/ordenes-trabajo-instalacion")
public class OrdenTrabajoInstalacionController {

    private final ServicioOrdenesTrabajoInstalacion servicioOrdenesTrabajoInstalacion;

    public OrdenTrabajoInstalacionController(
            ServicioOrdenesTrabajoInstalacion servicioOrdenesTrabajoInstalacion) {
        this.servicioOrdenesTrabajoInstalacion = servicioOrdenesTrabajoInstalacion;
    }

    /**
     * Programa una Orden_Trabajo_Instalacion a partir de una Orden_Fabricacion
     * terminada, aplicando las precondiciones (Req 19.1, 19.2, 19.3). Devuelve 201
     * con el id.
     *
     * @param request cuerpo con la OF, el Sitio, la Cuadrilla y la fecha programada.
     * @return 201 Created con el {@link OrdenTrabajoInstalacionDto} programado.
     */
    @PostMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','crear')")
    public ResponseEntity<OrdenTrabajoInstalacionDto> programar(
            @Valid @RequestBody ProgramarOrdenTrabajoInstalacionRequest request) {
        OrdenTrabajoInstalacionDto dto =
                servicioOrdenesTrabajoInstalacion.programar(request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta una Orden_Trabajo_Instalacion por su identificador, enriquecida con
     * sus pendientes y evidencias (Req 8.1, 8.2, 8.3, 23.3). Las listas van vacias
     * cuando no hay elementos. 404 si no es accesible (Req 8.6).
     *
     * @param id identificador de la Orden_Trabajo_Instalacion.
     * @return 200 OK con el {@link OrdenTrabajoInstalacionDetalleDto} (info +
     *         pendientes + evidencias).
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','leer')")
    public ResponseEntity<OrdenTrabajoInstalacionDetalleDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOrdenesTrabajoInstalacion.consultarDetalle(id));
    }

    /**
     * Lista los pendientes de la Lista_Pendientes de una Orden_Trabajo_Instalacion
     * accesible (Req 8.2), cada uno con su bandera {@code resuelto}. Devuelve una
     * lista vacia cuando no hay pendientes; 404 + auditoria de acceso cruzado si la
     * OTI no es accesible (Req 8.6).
     *
     * @param id identificador de la Orden_Trabajo_Instalacion.
     * @return 200 OK con la lista de {@link PendienteInstalacionDto} (incluye {@code []}).
     */
    @GetMapping("/{id}/pendientes")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','leer')")
    public ResponseEntity<List<PendienteInstalacionDto>> pendientes(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOrdenesTrabajoInstalacion.pendientesDe(id));
    }

    /**
     * Lista las evidencias fotograficas adjuntas a una Orden_Trabajo_Instalacion
     * accesible (Req 8.3). Devuelve una lista vacia cuando no hay evidencias; 404 +
     * auditoria de acceso cruzado si la OTI no es accesible (Req 8.6).
     *
     * @param id identificador de la Orden_Trabajo_Instalacion.
     * @return 200 OK con la lista de {@link EvidenciaInstalacionDto} (incluye {@code []}).
     */
    @GetMapping("/{id}/evidencias")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','leer')")
    public ResponseEntity<List<EvidenciaInstalacionDto>> evidencias(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioOrdenesTrabajoInstalacion.evidenciasDe(id));
    }

    /**
     * Registra el avance de una Orden_Trabajo_Instalacion (Req 19.4): pendientes,
     * evidencias y resoluciones. 200 OK con el DTO (su estado no cambia).
     *
     * @param id      identificador de la Orden_Trabajo_Instalacion.
     * @param request pendientes nuevos, evidencias y pendientes a resolver.
     * @return 200 OK con el {@link OrdenTrabajoInstalacionDto}.
     */
    @PostMapping("/{id}/avance")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','cambiar_estado')")
    public ResponseEntity<OrdenTrabajoInstalacionDto> registrarAvance(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RegistrarAvanceRequest request) {
        return ResponseEntity.ok(
                servicioOrdenesTrabajoInstalacion.registrarAvance(id, request.aComando()));
    }

    /**
     * Cambia el estado de una Orden_Trabajo_Instalacion segun la maquina de estados
     * (Req 19.5). 409 si la transicion es invalida; 422 si se intenta completar con
     * pendientes sin resolver (Req 19.6); 404 si no es accesible.
     *
     * @param id      identificador de la Orden_Trabajo_Instalacion.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link OrdenTrabajoInstalacionDto} en su nuevo estado.
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','cambiar_estado')")
    public ResponseEntity<OrdenTrabajoInstalacionDto> cambiarEstado(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoOrdenTrabajoInstalacionRequest request) {
        return ResponseEntity.ok(
                servicioOrdenesTrabajoInstalacion.cambiarEstado(id, request.estado()));
    }

    /**
     * Lista las Ordenes de Trabajo de Instalacion del tenant de forma paginada (20
     * por defecto, 100 maximo) con filtros opcionales por estado, Cuadrilla y
     * Cliente (Req 19.7).
     *
     * @param estado      etiqueta de estado a filtrar; opcional.
     * @param cuadrillaId Cuadrilla a filtrar; opcional.
     * @param clienteId   Cliente a filtrar; opcional.
     * @param page        numero de pagina 0-index; opcional.
     * @param size        tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link OrdenTrabajoInstalacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('operacion') and @autorizador.giroCorresponde('operacion') and @autorizador.tiene('orden_trabajo_instalacion','listar')")
    public PaginaResponse<OrdenTrabajoInstalacionDto> listar(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "cuadrillaId", required = false) UUID cuadrillaId,
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioOrdenesTrabajoInstalacion.listar(estado, cuadrillaId, clienteId, pageable));
    }
}

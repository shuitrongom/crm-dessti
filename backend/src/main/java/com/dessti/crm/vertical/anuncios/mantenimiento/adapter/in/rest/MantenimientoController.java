package com.dessti.crm.vertical.anuncios.mantenimiento.adapter.in.rest;

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

import com.dessti.crm.vertical.anuncios.mantenimiento.application.AsignarTicketCommand;
import com.dessti.crm.vertical.anuncios.mantenimiento.application.ContratoMantenimientoDto;
import com.dessti.crm.vertical.anuncios.mantenimiento.application.CrearContratoCommand;
import com.dessti.crm.vertical.anuncios.mantenimiento.application.GenerarTicketCommand;
import com.dessti.crm.vertical.anuncios.mantenimiento.application.ServicioMantenimiento;
import com.dessti.crm.vertical.anuncios.mantenimiento.application.TicketServicioDto;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST del modulo mantenimiento para la gestion de los
 * {@link ContratoMantenimientoDto Contratos_Mantenimiento} y los
 * {@link TicketServicioDto Tickets_Servicio} con SLA (Req 20; tarea 25.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code POST /mantenimiento/contratos} — registrar contrato
 *       ({@code @autorizador.tiene('contrato_mantenimiento','crear')}); 201 Created
 *       con el id (Req 20.1).</li>
 *   <li>{@code GET /mantenimiento/contratos/{id}} — consulta
 *       ({@code @autorizador.tiene('contrato_mantenimiento','leer')}); 200 OK; 404
 *       si no es accesible (Req 23.3).</li>
 *   <li>{@code GET /mantenimiento/contratos?clienteId=&page=&size=} — listado
 *       paginado (20/100) con filtro por Cliente
 *       ({@code @autorizador.tiene('contrato_mantenimiento','listar')}); 200 OK
 *       (Req 20.7).</li>
 *   <li>{@code POST /mantenimiento/tickets} — generar ticket
 *       ({@code @autorizador.tiene('ticket_servicio','crear')}); 201 Created; estado
 *       inicial {@code abierto} (Req 20.2). 404 si el contrato indicado no existe.</li>
 *   <li>{@code GET /mantenimiento/tickets/{id}} — consulta
 *       ({@code @autorizador.tiene('ticket_servicio','leer')}); 200 OK; 404 si no es
 *       accesible.</li>
 *   <li>{@code GET /mantenimiento/tickets?estado=&clienteId=&slaVencido=&page=&size=}
 *       — listado paginado (20/100) con filtros por estado, Cliente y vencimiento del
 *       SLA ({@code @autorizador.tiene('ticket_servicio','listar')}); 200 OK
 *       (Req 20.7).</li>
 *   <li>{@code PUT /mantenimiento/tickets/{id}/asignacion} — asignar a tecnico o
 *       Cuadrilla ({@code @autorizador.tiene('ticket_servicio','cambiar_estado')});
 *       200 OK (Req 20.3). 409 si el ticket no esta en {@code abierto}.</li>
 *   <li>{@code PUT /mantenimiento/tickets/{id}/estado} — cambio de estado
 *       ({@code @autorizador.tiene('ticket_servicio','cambiar_estado')}); 200 OK; 409
 *       si la transicion es invalida (Req 20.4, 20.5); al pasar a {@code resuelto}
 *       registra el cumplimiento del SLA (Req 20.6).</li>
 * </ul>
 *
 * <h2>Autorizacion (Req 3, 27.7)</h2>
 * <p>Cada endpoint exige el permiso atomico correspondiente via
 * {@code @autorizador.tiene(recurso, operacion)}. Los permisos
 * {@code contrato_mantenimiento:{crear,leer,listar,actualizar}} y
 * {@code ticket_servicio:{crear,leer,listar,cambiar_estado}} ya se sembraron en V5 y
 * se asignaron al rol {@code mantenimiento} (Req 27.7), por lo que la migracion V27
 * no requiere sembrar permisos adicionales.</p>
 *
 * <h2>Separacion de contrato (Req 12.2, 23.4)</h2>
 * <p>Se reciben y devuelven DTOs distintos de las entidades JPA; el {@code tenant_id}
 * y el actor se derivan del contexto. El manejo de errores lo centraliza
 * {@code ManejadorGlobalErrores} (422 regla de negocio, 409 transicion invalida, 404
 * no encontrado).</p>
 */
@RestController
@RequestMapping("/mantenimiento")
public class MantenimientoController {

    private final ServicioMantenimiento servicioMantenimiento;

    public MantenimientoController(ServicioMantenimiento servicioMantenimiento) {
        this.servicioMantenimiento = servicioMantenimiento;
    }

    // ==================================================================
    // Contrato_Mantenimiento (Req 20.1)
    // ==================================================================

    /**
     * Registra un Contrato_Mantenimiento asociado a un Cliente (Req 20.1). Devuelve
     * 201 con el id.
     *
     * @param request cuerpo con los datos del contrato.
     * @return 201 Created con el {@link ContratoMantenimientoDto} creado.
     */
    @PostMapping("/contratos")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('contrato_mantenimiento','crear')")
    public ResponseEntity<ContratoMantenimientoDto> crearContrato(
            @Valid @RequestBody CrearContratoRequest request) {
        ContratoMantenimientoDto dto = servicioMantenimiento.crearContrato(
                new CrearContratoCommand(request.clienteId(), request.tipo(),
                        request.slaRespuestaHoras(), request.slaResolucionHoras()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Contrato_Mantenimiento por su identificador (Req 23.3). 404 si no
     * es accesible.
     *
     * @param id identificador del contrato.
     * @return 200 OK con el {@link ContratoMantenimientoDto}.
     */
    @GetMapping("/contratos/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('contrato_mantenimiento','leer')")
    public ResponseEntity<ContratoMantenimientoDto> consultarContrato(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioMantenimiento.consultarContrato(id));
    }

    /**
     * Lista los Contratos_Mantenimiento del tenant de forma paginada (20 por
     * defecto, 100 maximo) con filtro opcional por Cliente (Req 20.7).
     *
     * @param clienteId Cliente a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ContratoMantenimientoDto}.
     */
    @GetMapping("/contratos")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('contrato_mantenimiento','listar')")
    public PaginaResponse<ContratoMantenimientoDto> listarContratos(
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioMantenimiento.listarContratos(clienteId, pageable));
    }

    // ==================================================================
    // Ticket_Servicio (Req 20.2–20.7)
    // ==================================================================

    /**
     * Genera un Ticket_Servicio en estado {@code abierto} (Req 20.2). Devuelve 201
     * con el id.
     *
     * @param request cuerpo con los datos del ticket.
     * @return 201 Created con el {@link TicketServicioDto} generado.
     */
    @PostMapping("/tickets")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('ticket_servicio','crear')")
    public ResponseEntity<TicketServicioDto> generarTicket(
            @Valid @RequestBody GenerarTicketRequest request) {
        TicketServicioDto dto = servicioMantenimiento.generarTicket(
                new GenerarTicketCommand(request.contratoMantenimientoId(),
                        request.clienteId(), request.origen()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Consulta un Ticket_Servicio por su identificador (Req 23.3). 404 si no es
     * accesible.
     *
     * @param id identificador del ticket.
     * @return 200 OK con el {@link TicketServicioDto}.
     */
    @GetMapping("/tickets/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('ticket_servicio','leer')")
    public ResponseEntity<TicketServicioDto> consultarTicket(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioMantenimiento.consultarTicket(id));
    }

    /**
     * Lista los Tickets_Servicio del tenant de forma paginada (20 por defecto, 100
     * maximo) con filtros opcionales por estado, por Cliente y por vencimiento del
     * SLA (Req 20.7).
     *
     * @param estado     etiqueta de estado a filtrar; opcional.
     * @param clienteId  Cliente a filtrar; opcional.
     * @param slaVencido {@code true} filtra los tickets con el SLA vencido; opcional.
     * @param page       numero de pagina 0-index; opcional.
     * @param size       tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link TicketServicioDto}.
     */
    @GetMapping("/tickets")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('ticket_servicio','listar')")
    public PaginaResponse<TicketServicioDto> listarTickets(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "slaVencido", required = false) Boolean slaVencido,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(
                servicioMantenimiento.listarTickets(estado, clienteId, slaVencido, pageable));
    }

    /**
     * Asigna un Ticket_Servicio a un tecnico o a una Cuadrilla (Req 20.3). 409 si el
     * ticket no esta en {@code abierto}; 404 si no es accesible.
     *
     * @param id      identificador del ticket.
     * @param request datos de la asignacion.
     * @return 200 OK con el {@link TicketServicioDto} ya asignado.
     */
    @PutMapping("/tickets/{id}/asignacion")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('ticket_servicio','cambiar_estado')")
    public ResponseEntity<TicketServicioDto> asignarTicket(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AsignarTicketRequest request) {
        return ResponseEntity.ok(servicioMantenimiento.asignarTicket(id,
                new AsignarTicketCommand(request.asignadoTipo(), request.asignadoId())));
    }

    /**
     * Cambia el estado de un Ticket_Servicio segun la maquina de estados (Req 20.4,
     * 20.5). 409 si la transicion es invalida; 404 si no es accesible; 422 si la
     * etiqueta es desconocida. Al pasar a {@code resuelto} registra el cumplimiento
     * del SLA (Req 20.6).
     *
     * @param id      identificador del ticket.
     * @param request etiqueta del estado destino.
     * @return 200 OK con el {@link TicketServicioDto} en su nuevo estado.
     */
    @PutMapping("/tickets/{id}/estado")
    @PreAuthorize("@autorizador.moduloHabilitado('mantenimiento') and @autorizador.giroCorresponde('mantenimiento') and @autorizador.tiene('ticket_servicio','cambiar_estado')")
    public ResponseEntity<TicketServicioDto> cambiarEstadoTicket(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CambiarEstadoTicketRequest request) {
        return ResponseEntity.ok(servicioMantenimiento.cambiarEstadoTicket(id, request.estado()));
    }
}

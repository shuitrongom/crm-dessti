package com.dessti.crm.portalcliente.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.comercial.cotizacion.application.CotizacionDto;
import com.dessti.crm.facturacion.factura.application.FacturaDto;
import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.portalcliente.application.ProyectoResumen;
import com.dessti.crm.portalcliente.application.PruebaDisenoResumen;
import com.dessti.crm.portalcliente.application.ResultadoRechazoPruebaResumen;
import com.dessti.crm.portalcliente.application.ServicioPortalCliente;
import com.dessti.crm.portalcliente.application.TicketServicioResumen;

/**
 * Adaptador de entrada REST del <strong>Portal del Cliente</strong> (rol externo
 * {@code cliente_portal}, Req 45; tarea 45.1).
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /portal/cotizaciones} — mis Cotizaciones (Req 45.1).</li>
 *   <li>{@code GET /portal/pruebas-diseno} — mis Prueba_Diseno (Req 45.1).</li>
 *   <li>{@code POST /portal/pruebas-diseno/{id}/aprobacion} — aprobar una prueba
 *       propia (Req 45.2); 404 si no es propia (Req 45.3); 409 si ya esta decidida
 *       (Req 15.4).</li>
 *   <li>{@code POST /portal/pruebas-diseno/{id}/rechazo} — rechazar una prueba
 *       propia y generar la siguiente version (Req 45.2, 15.3); 404 si no es propia;
 *       409 si ya esta decidida.</li>
 *   <li>{@code GET /portal/proyectos} — avance de mis Proyectos, listado
 *       (Req 45.1).</li>
 *   <li>{@code GET /portal/proyectos/{id}} — avance consolidado de un Proyecto
 *       propio, con Sitios y avance por fase (Req 45.1); 404 si no es propio.</li>
 *   <li>{@code GET /portal/tickets} — mis Ticket_Servicio (Req 45.1).</li>
 *   <li>{@code GET /portal/facturas} — mis Facturas (Req 45.1).</li>
 * </ul>
 *
 * <h2>Autorizacion y alcance por Cliente (Req 45.1, 45.3)</h2>
 * <p>Todos los endpoints exigen el rol externo {@code cliente_portal} via
 * {@code @PreAuthorize("@autorizador.moduloHabilitado('portal-cliente') and hasRole('cliente_portal')")}. Ademas, el servicio acota
 * <strong>cada</strong> consulta al Cliente del usuario del Portal, resuelto del
 * contexto de seguridad; el controlador <em>nunca</em> acepta un {@code clienteId}
 * en la peticion, de modo que un Cliente no puede consultar datos de otros
 * Clientes ni de operaciones internas. El aislamiento por {@code tenant_id} lo
 * refuerzan el filtro global y la RLS (Req 45.4, 23).</p>
 *
 * <h2>Semantica de 404 (Req 45.3)</h2>
 * <p>Aprobar/rechazar una Prueba_Diseno que no pertenezca al Cliente, o consultar
 * un Proyecto ajeno, responde 404 (no 403) para no revelar la existencia de
 * recursos de otros Clientes.</p>
 *
 * <h2>DTOs seguros para el Cliente (Req 12.2)</h2>
 * <p>Se reutilizan los DTOs de salida de cada modulo, que ya son proyecciones
 * publicas del recurso propio del Cliente (su Cotizacion, su Prueba_Diseno, el
 * avance de sus Proyectos/Sitios, sus Ticket_Servicio y sus Facturas). No exponen
 * entidades JPA ni datos internos de la Empresa distintos de la relacion comercial
 * del propio Cliente (por ejemplo, no se exponen listados globales ni recursos de
 * costos/margenes internos: el Portal solo enruta a la informacion del Cliente).</p>
 *
 * <h2>Paginacion (Req 45.6)</h2>
 * <p>Los listados usan {@link PageRequestFactory#acotando(Integer, Integer)}: 20
 * por defecto, 100 maximo, y devuelven {@link PaginaResponse}.</p>
 */
@RestController
@RequestMapping("/portal")
@PreAuthorize("@autorizador.moduloHabilitado('portal-cliente') and hasRole('cliente_portal')")
public class PortalClienteController {

    private final ServicioPortalCliente servicioPortalCliente;

    public PortalClienteController(ServicioPortalCliente servicioPortalCliente) {
        this.servicioPortalCliente = servicioPortalCliente;
    }

    /**
     * Lista de forma paginada (20/100) las Cotizaciones del Cliente autenticado
     * (Req 45.1).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link CotizacionDto}.
     */
    @GetMapping("/cotizaciones")
    public PaginaResponse<CotizacionDto> misCotizaciones(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPortalCliente.misCotizaciones(pageable));
    }

    /**
     * Lista de forma paginada (20/100) las Prueba_Diseno del Cliente autenticado,
     * mas reciente primero (Req 45.1).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link PruebaDisenoResumen}.
     */
    @GetMapping("/pruebas-diseno")
    public PaginaResponse<PruebaDisenoResumen> misPruebasDiseno(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPortalCliente.misPruebasDiseno(pageable));
    }

    /**
     * Aprueba una Prueba_Diseno propia del Cliente autenticado (Req 45.2). 404 si la
     * prueba no es del Cliente (Req 45.3); 409 si ya esta decidida (Req 15.4).
     *
     * @param id identificador de la Prueba_Diseno.
     * @return 200 OK con el {@link PruebaDisenoResumen} aprobado.
     */
    @PostMapping("/pruebas-diseno/{id}/aprobacion")
    public ResponseEntity<PruebaDisenoResumen> aprobar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPortalCliente.aprobarMiPruebaDiseno(id));
    }

    /**
     * Rechaza una Prueba_Diseno propia del Cliente autenticado y genera la siguiente
     * version pendiente (Req 45.2, 15.3). 404 si la prueba no es del Cliente
     * (Req 45.3); 409 si ya esta decidida (Req 15.4).
     *
     * @param id identificador de la Prueba_Diseno a rechazar.
     * @return 200 OK con la version rechazada y la nueva version pendiente.
     */
    @PostMapping("/pruebas-diseno/{id}/rechazo")
    public ResponseEntity<ResultadoRechazoPruebaResumen> rechazar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPortalCliente.rechazarMiPruebaDiseno(id));
    }

    /**
     * Lista de forma paginada (20/100) los Proyectos del Cliente autenticado
     * (Req 45.1). Devuelve la proyeccion de resumen; el detalle con avance por fase
     * de cada Sitio se obtiene por Proyecto en {@link #avanceDeProyecto(UUID)}.
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link ProyectoResumen} de resumen.
     */
    @GetMapping("/proyectos")
    public PaginaResponse<ProyectoResumen> misProyectos(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPortalCliente.avanceMisProyectos(pageable));
    }

    /**
     * Consulta el avance consolidado de un Proyecto propio del Cliente autenticado,
     * con sus Sitios y el avance por fase (Req 45.1). 404 si el Proyecto no es del
     * Cliente (Req 45.3).
     *
     * @param id identificador del Proyecto.
     * @return 200 OK con el {@link ProyectoResumen} detallado.
     */
    @GetMapping("/proyectos/{id}")
    public ResponseEntity<ProyectoResumen> avanceDeProyecto(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioPortalCliente.avanceDeMiProyecto(id));
    }

    /**
     * Lista de forma paginada (20/100) los Ticket_Servicio del Cliente autenticado
     * (Req 45.1).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link TicketServicioResumen}.
     */
    @GetMapping("/tickets")
    public PaginaResponse<TicketServicioResumen> misTickets(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPortalCliente.misTickets(pageable));
    }

    /**
     * Lista de forma paginada (20/100) las Facturas del Cliente autenticado
     * (Req 45.1).
     *
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (por defecto 20, maximo 100).
     * @return 200 OK con la pagina de {@link FacturaDto}.
     */
    @GetMapping("/facturas")
    public PaginaResponse<FacturaDto> misFacturas(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioPortalCliente.misFacturas(pageable));
    }
}

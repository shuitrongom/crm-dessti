package com.dessti.crm.portalcliente.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.application.CotizacionConsulta;
import com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort;
import com.dessti.crm.comercial.cotizacion.application.CotizacionDto;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.facturacion.factura.application.FacturaDto;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion del <strong>Portal del Cliente</strong> (rol externo
 * {@code cliente_portal}, Req 45). Da a un Cliente acceso de <em>solo su propia
 * informacion</em> y una unica accion de escritura acotada: aprobar/rechazar sus
 * propias Prueba_Diseno.
 *
 * <h2>Alcance restringido por Cliente (Req 45.1, 45.3)</h2>
 * <p>Cada operacion resuelve el Cliente del usuario del Portal via
 * {@link ClientePortalActualPort#clienteIdActual()} y filtra <strong>siempre</strong>
 * por ese identificador. El Portal <em>nunca</em> acepta un {@code clienteId} de la
 * peticion. Asi, un Cliente ve unicamente sus Cotizaciones, sus Prueba_Diseno, el
 * avance de sus Proyectos/Sitios, sus Ticket_Servicio y sus Facturas, y no puede
 * acceder a datos de otros Clientes ni a operaciones internas de la Empresa.</p>
 *
 * <h2>Consumo del vertical por puertos (Req 10.5, 4.5)</h2>
 * <p>El Portal es <strong>Nucleo</strong> y, con la extraccion del vertical de
 * anuncios, deja de depender de las clases concretas de los flujos del vertical
 * (Prueba_Diseno de {@code vertical.anuncios.pruebadiseno}, Ticket_Servicio de
 * {@code vertical.anuncios.mantenimiento} y Proyecto/Sitio de {@code operacion.proyecto}). En su
 * lugar consume tres puertos que <em>el propio Portal define</em> —
 * {@link ResumenPruebasDisenoPort}, {@link ResumenTicketsPort} y
 * {@link ResumenProyectosPort}— y que el vertical implementa, invirtiendo la
 * dependencia. La Cotizacion (Nucleo comercial) se consulta por el puerto del
 * Nucleo {@link CotizacionConsultaPort}, y la Factura (Nucleo facturacion) por su
 * repositorio del Nucleo, ambos transversales a todo Giro.</p>
 *
 * <h2>Guarda de propiedad y 404 (Req 45.2, 45.3)</h2>
 * <p>Al aprobar o rechazar una Prueba_Diseno, se verifica que la prueba pertenezca
 * a una Cotizacion del Cliente actual. Si no pertenece (o no existe/otro tenant) se
 * responde <strong>404</strong> (no 403), para no revelar la existencia de recursos
 * de otros Clientes. Superada la guarda, se delega en el puerto del vertical (que
 * reaplica la maquina de estados: 409 si la prueba ya esta decidida, Req 15.4).</p>
 *
 * <h2>Aislamiento multi-tenant (Req 45.4) y auditoria (Req 45.5)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la peticion,
 * Req 23.4). Cada accion del Cliente en el Portal se registra via
 * {@link AuditoriaPort} con el actor, la accion, el recurso y la marca temporal UTC
 * (Req 45.5), incluidas las consultas y los intentos de acceso a recursos ajenos.</p>
 *
 * <h2>Paginacion (Req 45.6)</h2>
 * <p>Los listados son paginados; el tamano lo acota la capa REST (20 por defecto,
 * 100 maximo) via {@code PageRequestFactory}.</p>
 */
@Service
public class ServicioPortalCliente {

    /** Recurso de auditoria/RBAC del Portal del Cliente (Req 45.5). */
    static final String RECURSO_PORTAL = "portal_cliente";

    private final ClientePortalActualPort clientePortalActual;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionConsultaPort cotizacionConsulta;
    private final DecisionCotizacionPort decisionCotizacion;
    private final ResumenPruebasDisenoPort resumenPruebasDiseno;
    private final ResumenProyectosPort resumenProyectos;
    private final ResumenTicketsPort resumenTickets;
    private final RegistroQuejaPortalPort registroQuejaPortal;
    private final PerfilClientePortalPort perfilClientePortal;
    private final FacturaRepository facturaRepository;
    private final AuditoriaPort auditoria;

    public ServicioPortalCliente(ClientePortalActualPort clientePortalActual,
                                 CotizacionRepository cotizacionRepository,
                                 CotizacionConsultaPort cotizacionConsulta,
                                 DecisionCotizacionPort decisionCotizacion,
                                 ResumenPruebasDisenoPort resumenPruebasDiseno,
                                 ResumenProyectosPort resumenProyectos,
                                 ResumenTicketsPort resumenTickets,
                                 RegistroQuejaPortalPort registroQuejaPortal,
                                 PerfilClientePortalPort perfilClientePortal,
                                 FacturaRepository facturaRepository,
                                 AuditoriaPort auditoria) {
        this.clientePortalActual = clientePortalActual;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionConsulta = cotizacionConsulta;
        this.decisionCotizacion = decisionCotizacion;
        this.resumenPruebasDiseno = resumenPruebasDiseno;
        this.resumenProyectos = resumenProyectos;
        this.resumenTickets = resumenTickets;
        this.registroQuejaPortal = registroQuejaPortal;
        this.perfilClientePortal = perfilClientePortal;
        this.facturaRepository = facturaRepository;
        this.auditoria = auditoria;
    }

    /**
     * Lista de forma paginada las Cotizaciones del Cliente actual (Req 45.1, 45.6).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Cotizaciones del Cliente como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<CotizacionDto> misCotizaciones(Pageable pageable) {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "cotizaciones");
        return cotizacionRepository.buscarConFiltros(clienteId, null, null, pageable)
                .map(CotizacionDto::de);
    }

    /**
     * Aprueba una Cotizacion <strong>propia</strong> del Cliente actual (Req 45.2,
     * 6.6). Verifica la propiedad (404 si no es del Cliente, Req 45.3); superada la
     * guarda, delega en el puerto de decision (que reaplica la maquina de estados:
     * 409 si la Cotizacion no esta {@code enviada}). Audita (Req 45.5).
     *
     * @param cotizacionId identificador de la Cotizacion a aprobar.
     * @return el DTO de la Cotizacion aprobada.
     * @throws RecursoNoEncontradoException si la Cotizacion no es del Cliente actual (404).
     */
    @Transactional
    public CotizacionDto aprobarMiCotizacion(UUID cotizacionId) {
        UUID clienteId = clienteActual();
        exigirCotizacionDelCliente(cotizacionId, clienteId);
        CotizacionDto dto = decisionCotizacion.aprobar(cotizacionId);
        auditarAccion(clienteId, "aprobar_cotizacion",
                "aprobada Cotizacion propia [cotizacion=" + cotizacionId + "]");
        return dto;
    }

    /**
     * Rechaza una Cotizacion <strong>propia</strong> del Cliente actual (Req 45.2,
     * 6.6). Misma guarda de propiedad y semantica que {@link #aprobarMiCotizacion}.
     *
     * @param cotizacionId identificador de la Cotizacion a rechazar.
     * @return el DTO de la Cotizacion rechazada.
     * @throws RecursoNoEncontradoException si la Cotizacion no es del Cliente actual (404).
     */
    @Transactional
    public CotizacionDto rechazarMiCotizacion(UUID cotizacionId) {
        UUID clienteId = clienteActual();
        exigirCotizacionDelCliente(cotizacionId, clienteId);
        CotizacionDto dto = decisionCotizacion.rechazar(cotizacionId);
        auditarAccion(clienteId, "rechazar_cotizacion",
                "rechazada Cotizacion propia [cotizacion=" + cotizacionId + "]");
        return dto;
    }

    /**
     * Lista de forma paginada las Prueba_Diseno del Cliente actual (las de sus
     * Cotizaciones), mas reciente primero (Req 45.1, 45.6).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Prueba_Diseno del Cliente como resumenes.
     */
    @Transactional(readOnly = true)
    public Page<PruebaDisenoResumen> misPruebasDiseno(Pageable pageable) {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "pruebas_diseno");
        return resumenPruebasDiseno.listarPorCliente(clienteId, pageable);
    }

    /**
     * Aprueba una Prueba_Diseno <strong>propia</strong> del Cliente actual (Req
     * 45.2). Verifica la propiedad (la prueba debe pertenecer a una Cotizacion del
     * Cliente actual); si no, 404 (Req 45.3). Superada la guarda, delega en el
     * puerto del vertical y audita (Req 45.5).
     *
     * @param pruebaId identificador de la Prueba_Diseno.
     * @return el resumen de la Prueba_Diseno aprobada.
     * @throws RecursoNoEncontradoException si la prueba no es del Cliente actual (404).
     */
    @Transactional
    public PruebaDisenoResumen aprobarMiPruebaDiseno(UUID pruebaId) {
        UUID clienteId = clienteActual();
        exigirPruebaDelCliente(pruebaId, clienteId);
        PruebaDisenoResumen resumen = resumenPruebasDiseno.aprobar(pruebaId);
        auditarAccion(clienteId, "aprobar_prueba_diseno",
                "aprobada Prueba_Diseno propia [prueba=" + pruebaId + "]");
        return resumen;
    }

    /**
     * Rechaza una Prueba_Diseno <strong>propia</strong> del Cliente actual y genera
     * automaticamente la siguiente version pendiente (Req 45.2; Req 15.3). Verifica
     * la propiedad (404 si no es del Cliente, Req 45.3), delega en el puerto del
     * vertical y audita (Req 45.5).
     *
     * @param pruebaId identificador de la Prueba_Diseno a rechazar.
     * @return el resultado con la version rechazada y la nueva version pendiente.
     * @throws RecursoNoEncontradoException si la prueba no es del Cliente actual (404).
     */
    @Transactional
    public ResultadoRechazoPruebaResumen rechazarMiPruebaDiseno(UUID pruebaId) {
        UUID clienteId = clienteActual();
        exigirPruebaDelCliente(pruebaId, clienteId);
        ResultadoRechazoPruebaResumen resultado = resumenPruebasDiseno.rechazar(pruebaId);
        auditarAccion(clienteId, "rechazar_prueba_diseno",
                "rechazada Prueba_Diseno propia [prueba=" + pruebaId + "]");
        return resultado;
    }

    /**
     * Lista de forma paginada los Proyectos del Cliente actual con el avance de sus
     * Sitios (Req 45.1, 45.6). Reutiliza {@link ResumenProyectosPort#listarPorCliente},
     * que devuelve la proyeccion de resumen del listado; el detalle consolidado por
     * Proyecto (avance por fase de cada Sitio) se obtiene con
     * {@link #avanceDeMiProyecto(UUID)}.
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Proyectos del Cliente como resumenes.
     */
    @Transactional(readOnly = true)
    public Page<ProyectoResumen> avanceMisProyectos(Pageable pageable) {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "proyectos");
        return resumenProyectos.listarPorCliente(clienteId, pageable);
    }

    /**
     * Consulta el avance consolidado de un Proyecto <strong>propio</strong> del
     * Cliente actual, incluyendo sus Sitios con el avance por fase (Req 45.1,
     * 45.3). Verifica que el Proyecto pertenezca al Cliente actual; si no, 404.
     *
     * @param proyectoId identificador del Proyecto.
     * @return el resumen detallado del Proyecto (estado consolidado + Sitios).
     * @throws RecursoNoEncontradoException si el Proyecto no es del Cliente actual (404).
     */
    @Transactional(readOnly = true)
    public ProyectoResumen avanceDeMiProyecto(UUID proyectoId) {
        UUID clienteId = clienteActual();
        ProyectoResumen proyecto = resumenProyectos.consultar(proyectoId);
        if (!clienteId.equals(proyecto.clienteId())) {
            auditarAccesoCruzado(clienteId, "proyecto", proyectoId);
            throw new RecursoNoEncontradoException("No se encontro el Proyecto solicitado.");
        }
        auditarAccion(clienteId, "consultar_avance_proyecto",
                "consultado avance de Proyecto propio [proyecto=" + proyectoId + "]");
        return proyecto;
    }

    /**
     * Lista de forma paginada los Ticket_Servicio del Cliente actual (Req 45.1,
     * 45.6).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ticket_Servicio del Cliente como resumenes.
     */
    @Transactional(readOnly = true)
    public Page<TicketServicioResumen> misTickets(Pageable pageable) {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "tickets");
        return resumenTickets.listarPorCliente(clienteId, pageable);
    }

    /**
     * Lista de forma paginada las Queja_Cliente del Cliente actual (Req 45.1, 45.6).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de quejas del Cliente como resumenes.
     */
    @Transactional(readOnly = true)
    public Page<QuejaPortalResumen> misQuejas(Pageable pageable) {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "quejas");
        return registroQuejaPortal.listarPorCliente(clienteId, pageable);
    }

    /**
     * Registra una Queja_Cliente con origen {@code PORTAL} para el Cliente actual
     * (Req 45.2, 70.1, 70.8). El {@code clienteId} lo resuelve el Portal (nunca la
     * peticion); delega el alta en el modulo de calidad y audita (Req 45.5).
     *
     * @param descripcion descripcion de la queja; obligatoria (la valida el dominio).
     * @return el resumen de la queja registrada, en estado {@code registrada}.
     */
    @Transactional
    public QuejaPortalResumen registrarMiQueja(String descripcion) {
        UUID clienteId = clienteActual();
        QuejaPortalResumen resumen = registroQuejaPortal.registrarDesdePortal(clienteId, descripcion);
        auditarAccion(clienteId, "registrar_queja",
                "registrada Queja_Cliente desde el Portal [queja=" + resumen.id() + "]");
        return resumen;
    }

    /**
     * Lista de forma paginada las Facturas del Cliente actual (Req 45.1, 45.6).
     *
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Facturas del Cliente como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<FacturaDto> misFacturas(Pageable pageable) {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "facturas");
        return facturaRepository.buscarConFiltros(clienteId, null, pageable)
                .map(FacturaDto::de);
    }

    /**
     * Consulta el perfil del Cliente actual (Req 45.1): sus datos de identificacion,
     * contacto y direccion. Si el Cliente no es accesible en el tenant, responde 404.
     *
     * @return el resumen del perfil del Cliente actual.
     * @throws RecursoNoEncontradoException si el Cliente no es accesible (404).
     */
    @Transactional(readOnly = true)
    public PerfilClienteResumen miPerfil() {
        UUID clienteId = clienteActual();
        auditarConsulta(clienteId, "perfil");
        return perfilClientePortal.buscarPerfil(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el perfil del Cliente."));
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Verifica que la Prueba_Diseno pertenezca a una Cotizacion del Cliente actual
     * (guarda de propiedad, Req 45.2, 45.3). Cualquier caso en el que la prueba no
     * exista (o pertenezca a otro tenant), su Cotizacion no exista, o la Cotizacion
     * no sea del Cliente actual, se traduce a <strong>404</strong> y se audita el
     * intento de acceso cruzado, sin revelar la existencia de recursos ajenos.
     */
    private void exigirPruebaDelCliente(UUID pruebaId, UUID clienteId) {
        if (pruebaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Prueba_Diseno solicitada.");
        }
        UUID cotizacionId = resumenPruebasDiseno.cotizacionDePrueba(pruebaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(clienteId, "prueba_diseno", pruebaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Prueba_Diseno solicitada.");
                });
        CotizacionConsulta cotizacion = cotizacionConsulta.buscar(cotizacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(clienteId, "prueba_diseno", pruebaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Prueba_Diseno solicitada.");
                });
        if (!clienteId.equals(cotizacion.clienteId())) {
            auditarAccesoCruzado(clienteId, "prueba_diseno", pruebaId);
            throw new RecursoNoEncontradoException(
                    "No se encontro la Prueba_Diseno solicitada.");
        }
    }

    /**
     * Verifica que la Cotizacion pertenezca al Cliente actual (guarda de propiedad,
     * Req 45.2, 45.3). Si no existe (u otro tenant) o no es del Cliente, se traduce a
     * <strong>404</strong> y se audita el intento de acceso cruzado, sin revelar la
     * existencia de recursos ajenos.
     */
    private void exigirCotizacionDelCliente(UUID cotizacionId, UUID clienteId) {
        if (cotizacionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cotizacion solicitada.");
        }
        CotizacionConsulta cotizacion = cotizacionConsulta.buscar(cotizacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(clienteId, "cotizacion", cotizacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cotizacion solicitada.");
                });
        if (!clienteId.equals(cotizacion.clienteId())) {
            auditarAccesoCruzado(clienteId, "cotizacion", cotizacionId);
            throw new RecursoNoEncontradoException("No se encontro la Cotizacion solicitada.");
        }
    }

    private UUID clienteActual() {
        return clientePortalActual.clienteIdActual();
    }

    private void auditarConsulta(UUID clienteId, String recursoConsultado) {
        auditarAccion(clienteId, "consultar",
                "consulta del Portal del recurso '" + recursoConsultado + "' del Cliente propio");
    }

    private void auditarAccion(UUID clienteId, String accion, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), accion, RECURSO_PORTAL,
                detalle + " [cliente=" + clienteId + "]", null, null));
    }

    private void auditarAccesoCruzado(UUID clienteId, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actorActual(), "acceso_denegado", RECURSO_PORTAL,
                "intento de acceso a " + recurso + " ajeno al Cliente del Portal [recurso="
                        + recursoId + ", cliente=" + clienteId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}

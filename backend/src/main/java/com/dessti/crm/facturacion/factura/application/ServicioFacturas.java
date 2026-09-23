package com.dessti.crm.facturacion.factura.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.cxc.application.CuentaPorCobrarPort;
import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.facturacion.application.ResultadoCancelacion;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.application.SolicitudCancelacion;
import com.dessti.crm.facturacion.application.SolicitudTimbrado;
import com.dessti.crm.facturacion.factura.adapter.out.persistence.FacturaRepository;
import com.dessti.crm.facturacion.factura.domain.DatosFiscalesReceptor;
import com.dessti.crm.facturacion.factura.domain.EstadoFactura;
import com.dessti.crm.facturacion.factura.domain.Factura;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link Factura}
 * (CFDI) (Req 34, 35). Replica el patron establecido por
 * {@code ServicioCotizaciones}/{@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>emitir (Req 34.1, 34.2, 34.3):</strong> emite una Factura en
 *       {@code borrador} a partir de una Cotizacion {@code aprobada} (via
 *       {@link CotizacionAprobadaPort}) o de una Orden_Fabricacion (via
 *       {@link OrdenFabricacionParaFacturaPort}); valida los datos fiscales del
 *       receptor, calcula IVA/retenciones/total half-up (Property 4) y audita.</li>
 *   <li><strong>timbrar (Req 35.1, 35.2):</strong> solicita el Timbrado al
 *       {@link PacPort}; en exito registra el Folio_Fiscal + sello y transita a
 *       {@code timbrada}; en rechazo conserva {@code borrador} y devuelve el
 *       motivo (auditando ambos desenlaces).</li>
 *   <li><strong>cancelar (Req 35.4, 35.5):</strong> inicia la cancelacion
 *       ({@code timbrada -> cancelacion_en_proceso}) con motivo SAT, solicita la
 *       cancelacion al PAC y, en exito, confirma
 *       ({@code cancelacion_en_proceso -> cancelada}); audita.</li>
 *   <li><strong>consultar / listar (Req 34.4, 23.3):</strong> 404 + auditoria del
 *       intento si no es accesible; listado paginado (20/100) con filtros por
 *       Cliente y estado.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 34.5, 35.7)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se audita via
 * {@link AuditoriaPort}; los cambios de estado registran el estado anterior y el
 * nuevo (Req 35.7).</p>
 */
@Service
public class ServicioFacturas {

    /** Tipo de recurso de auditoria/RBAC de la Factura. */
    static final String RECURSO_FACTURA = "factura";

    private final FacturaRepository facturaRepository;
    private final CotizacionAprobadaPort cotizacionAprobada;
    private final OrdenFabricacionParaFacturaPort ordenFabricacionParaFactura;
    private final PacPort pac;
    private final AuditoriaPort auditoria;
    private final CuentaPorCobrarPort cuentaPorCobrar;

    public ServicioFacturas(FacturaRepository facturaRepository,
                            CotizacionAprobadaPort cotizacionAprobada,
                            OrdenFabricacionParaFacturaPort ordenFabricacionParaFactura,
                            PacPort pac,
                            AuditoriaPort auditoria,
                            CuentaPorCobrarPort cuentaPorCobrar) {
        this.facturaRepository = facturaRepository;
        this.cotizacionAprobada = cotizacionAprobada;
        this.ordenFabricacionParaFactura = ordenFabricacionParaFactura;
        this.pac = pac;
        this.auditoria = auditoria;
        this.cuentaPorCobrar = cuentaPorCobrar;
    }

    /**
     * Emite una Factura en {@code borrador} a partir de una Cotizacion aprobada o
     * de una Orden_Fabricacion (Req 34.1). Valida el origen y los datos fiscales del
     * receptor (Req 34.3), calcula los importes fiscales (Req 34.2) y audita.
     *
     * @param comando datos de la Factura a emitir.
     * @return el DTO de la Factura emitida (en {@code borrador}).
     * @throws RecursoNoEncontradoException si el origen no existe en el tenant (404).
     * @throws ReglaNegocioException si el origen es ambiguo/ausente, la Cotizacion no
     *         esta aprobada, o los datos fiscales son invalidos (422).
     */
    @Transactional
    public FacturaDto emitir(EmitirFacturaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Factura son obligatorios.");
        }
        boolean tieneCotizacion = comando.cotizacionId() != null;
        boolean tieneOrden = comando.ordenFabricacionId() != null;
        if (tieneCotizacion == tieneOrden) {
            throw new ReglaNegocioException(
                    "La Factura debe originarse en exactamente una Cotizacion aprobada o una "
                            + "Orden_Fabricacion.");
        }
        DatosFiscalesReceptor datos = DatosFiscalesReceptor.validar(
                comando.receptorRfc(), comando.receptorNombre(), comando.receptorCp(),
                comando.receptorRegimenFiscal(), comando.usoCfdi());
        BigDecimal tasaRetencion =
                (comando.tasaRetencion() == null) ? BigDecimal.ZERO : comando.tasaRetencion();

        Factura factura;
        if (tieneCotizacion) {
            factura = emitirDesdeCotizacion(comando.cotizacionId(), datos, tasaRetencion, actor);
        } else {
            factura = emitirDesdeOrden(comando.ordenFabricacionId(), datos, tasaRetencion, actor);
        }

        Factura guardada = facturaRepository.save(factura);
        auditar(actor, "crear", guardada.getId(),
                "emitida factura CFDI en estado '" + guardada.getEstado().valorBd()
                        + "' [total=" + guardada.getTotal().toPlainString() + "]", null, null);
        return FacturaDto.de(guardada);
    }

    private Factura emitirDesdeCotizacion(UUID cotizacionId, DatosFiscalesReceptor datos,
                                          BigDecimal tasaRetencion, String actor) {
        CotizacionParaFactura cotizacion = cotizacionAprobada.buscarParaFactura(cotizacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, "cotizacion", cotizacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cotizacion indicada para la Factura.");
                });
        if (!cotizacion.estaAprobada()) {
            throw new ReglaNegocioException("se requiere una Cotizacion aprobada");
        }
        return Factura.emitirDesdeCotizacion(
                cotizacionId, cotizacion.clienteId(), datos, cotizacion.total(), tasaRetencion, actor);
    }

    private Factura emitirDesdeOrden(UUID ordenFabricacionId, DatosFiscalesReceptor datos,
                                     BigDecimal tasaRetencion, String actor) {
        OrdenFabricacionParaFactura orden =
                ordenFabricacionParaFactura.buscarParaFactura(ordenFabricacionId)
                        .orElseGet(() -> {
                            auditarAccesoCruzado(actor, "orden_fabricacion", ordenFabricacionId);
                            throw new RecursoNoEncontradoException(
                                    "No se encontro la Orden_Fabricacion indicada para la Factura.");
                        });
        return Factura.emitirDesdeOrdenFabricacion(
                ordenFabricacionId, orden.clienteId(), datos, orden.total(), tasaRetencion, actor);
    }

    /**
     * Solicita el Timbrado de una Factura en {@code borrador} al PAC (Req 35.1,
     * 35.2). En exito registra el Folio_Fiscal + sello y transita a
     * {@code timbrada}; en rechazo conserva {@code borrador} y lanza 422 con el
     * motivo del PAC. Ambos desenlaces se auditan.
     *
     * @param facturaId identificador de la Factura a timbrar.
     * @return el DTO de la Factura {@code timbrada}.
     * @throws RecursoNoEncontradoException si la Factura no es accesible (404).
     * @throws ReglaNegocioException si la Factura no esta en {@code borrador} o el
     *         PAC rechaza el Timbrado (422, Req 35.2).
     *
     * <p>Tras el Timbrado exitoso se registra la Cuenta_Por_Cobrar de la Factura por
     * el saldo = total, via {@link CuentaPorCobrarPort} (Req 36.1). El registro es
     * idempotente: si ya existiera una CxC para la Factura, no se duplica.</p>
     */
    @Transactional
    public FacturaDto timbrar(UUID facturaId) {
        String actor = actorActual();
        Factura factura = cargar(facturaId, actor);
        if (factura.getEstado() != EstadoFactura.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo se puede timbrar una Factura en estado 'borrador'.");
        }
        SolicitudTimbrado solicitud = new SolicitudTimbrado(
                TenantContext.require(), factura.getId(),
                factura.getReceptorRfc(), factura.getReceptorNombre(), factura.getReceptorCp(),
                factura.getReceptorRegimenFiscal(), factura.getUsoCfdi(),
                factura.getSubtotal(), factura.getIva(), factura.getRetenciones(), factura.getTotal());
        ResultadoTimbrado resultado = pac.timbrar(solicitud);

        if (!resultado.exito()) {
            // Req 35.2: el PAC rechaza -> se conserva 'borrador' y se informa el motivo.
            auditar(actor, "timbrado_rechazado", factura.getId(),
                    "el PAC rechazo el timbrado: " + resultado.mensajeError(), null, null);
            throw new ReglaNegocioException(
                    "El PAC rechazo el timbrado: " + resultado.mensajeError());
        }

        factura.timbrar(resultado.folioFiscal(), resultado.selloSat(),
                resultado.fechaTimbrado(), actor);
        Factura guardada = facturaRepository.save(factura);
        auditar(actor, "timbrar", guardada.getId(),
                "factura timbrada [folio_fiscal=" + guardada.getFolioFiscal() + "]",
                EstadoFactura.BORRADOR.valorBd(), EstadoFactura.TIMBRADA.valorBd());
        // Req 36.1: al quedar timbrada, se registra su Cuenta_Por_Cobrar por el
        // saldo = total (idempotente en la implementacion de CxC).
        cuentaPorCobrar.registrarPorFacturaTimbrada(
                guardada.getId(), guardada.getClienteId(), guardada.getTotal());
        return FacturaDto.de(guardada);
    }

    /**
     * Solicita al PAC la cancelacion de una Factura timbrada con un motivo del
     * catalogo del SAT (Req 35.4, 35.5). Transita {@code timbrada ->
     * cancelacion_en_proceso}; si el PAC acepta, confirma
     * {@code cancelacion_en_proceso -> cancelada}. El Folio_Fiscal se conserva como
     * historico inmutable (Req 35.6). Audita el ciclo.
     *
     * @param facturaId identificador de la Factura a cancelar.
     * @param motivoSat clave del motivo de cancelacion del SAT; obligatoria (Req 35.4).
     * @return el DTO de la Factura {@code cancelada} (o en
     *         {@code cancelacion_en_proceso} si el PAC no acepto).
     * @throws RecursoNoEncontradoException si la Factura no es accesible (404).
     * @throws ReglaNegocioException si falta el motivo o el PAC rechaza la
     *         cancelacion (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         Factura no esta {@code timbrada} (409, Req 35.7).
     */
    @Transactional
    public FacturaDto cancelar(UUID facturaId, String motivoSat) {
        String actor = actorActual();
        if (motivoSat == null || motivoSat.isBlank()) {
            throw new ReglaNegocioException(
                    "Se requiere un motivo de cancelacion conforme a los catalogos del SAT.");
        }
        Factura factura = cargar(facturaId, actor);
        EstadoFactura anterior = factura.getEstado();
        factura.iniciarCancelacion(motivoSat, actor);
        auditar(actor, "iniciar_cancelacion", factura.getId(),
                "solicitud de cancelacion al PAC [motivo_sat=" + motivoSat + "]",
                anterior.valorBd(), EstadoFactura.CANCELACION_EN_PROCESO.valorBd());

        ResultadoCancelacion resultado = pac.cancelar(
                new SolicitudCancelacion(factura.getFolioFiscal(), motivoSat.strip()));
        if (!resultado.exito()) {
            // El PAC no acepto: se conserva 'cancelacion_en_proceso' y se informa.
            facturaRepository.save(factura);
            auditar(actor, "cancelacion_rechazada", factura.getId(),
                    "el PAC rechazo la cancelacion: " + resultado.mensajeError(), null, null);
            throw new ReglaNegocioException(
                    "El PAC rechazo la cancelacion: " + resultado.mensajeError());
        }

        factura.confirmarCancelacion(actor);
        Factura guardada = facturaRepository.save(factura);
        auditar(actor, "cancelar", guardada.getId(),
                "factura cancelada [acuse=" + resultado.acuse() + "]",
                EstadoFactura.CANCELACION_EN_PROCESO.valorBd(), EstadoFactura.CANCELADA.valorBd());
        return FacturaDto.de(guardada);
    }

    /**
     * Consulta puntual de una Factura del tenant (Req 23.3).
     *
     * @param facturaId identificador de la Factura.
     * @return el DTO de la Factura.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public FacturaDto consultar(UUID facturaId) {
        String actor = actorActual();
        return FacturaDto.de(cargar(facturaId, actor));
    }

    /**
     * Listado paginado de Facturas del tenant con filtros opcionales por Cliente y
     * por estado (Req 34.4). Un filtro nulo no restringe.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Facturas como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<FacturaDto> listar(UUID clienteId, String estado, Pageable pageable) {
        EstadoFactura filtro = (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return facturaRepository.buscarConFiltros(clienteId, filtro, pageable).map(FacturaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Factura cargar(UUID facturaId, String actor) {
        if (facturaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Factura solicitada.");
        }
        return facturaRepository.findById(facturaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_FACTURA, facturaId);
                    throw new RecursoNoEncontradoException("No se encontro la Factura solicitada.");
                });
    }

    private EstadoFactura interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Factura es obligatorio.");
        }
        try {
            return EstadoFactura.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Factura desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID facturaId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_FACTURA,
                detalle + " [id=" + facturaId + "]", valorAnterior, valorNuevo));
    }

    private void auditarAccesoCruzado(String actor, String recurso, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", recurso,
                "intento de acceso a " + recurso + " no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}

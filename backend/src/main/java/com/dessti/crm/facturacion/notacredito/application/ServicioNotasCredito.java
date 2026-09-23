package com.dessti.crm.facturacion.notacredito.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.contabilidad.cxc.application.CuentaPorCobrarPort;
import com.dessti.crm.facturacion.application.PacPort;
import com.dessti.crm.facturacion.application.ResultadoTimbrado;
import com.dessti.crm.facturacion.application.SolicitudTimbrado;
import com.dessti.crm.facturacion.notacredito.adapter.out.persistence.NotaCreditoRepository;
import com.dessti.crm.facturacion.notacredito.domain.EstadoNotaCredito;
import com.dessti.crm.facturacion.notacredito.domain.NotaCredito;
import com.dessti.crm.facturacion.notacredito.domain.NotaCreditoValidaciones;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link NotaCredito}
 * (CFDI de egreso) (Req 37). Replica el patron de {@code ServicioFacturas}.
 *
 * <h2>Operaciones</h2>
 * <ul>
 *   <li><strong>emitir (Req 37.1, 37.2):</strong> valida que la Factura
 *       referenciada exista y este {@code timbrada} (via
 *       {@link FacturaReferenciadaPort}), calcula el saldo disponible
 *       ({@code total - Σ notas previas no canceladas}) y acota el monto por ese
 *       saldo (Property 14); crea la Nota de Credito en {@code borrador} y audita.</li>
 *   <li><strong>timbrar (Req 37.1):</strong> solicita el Timbrado del CFDI de
 *       egreso al {@link PacPort}; en exito registra Folio_Fiscal + sello y transita
 *       a {@code timbrada}; en rechazo conserva {@code borrador} y devuelve el
 *       motivo (auditando ambos).</li>
 *   <li><strong>consultar / listar (Req 23.3):</strong> 404 + auditoria del intento
 *       si no es accesible; listado paginado (20/100) con filtros por Factura y
 *       estado.</li>
 * </ul>
 *
 * <h2>Integracion con CxC (bloque 29, Req 37.1)</h2>
 * <p>La disminucion efectiva de la Cuenta_Por_Cobrar asociada (Req 37.1) se
 * completa en este bloque 29 (modulo contabilidad-finanzas): al emitir la Nota de
 * Credito, {@link #emitir} invoca {@link CuentaPorCobrarPort#disminuirPorNotaCredito}
 * para reducir el saldo de la CxC de la Factura por el monto de la nota. El tope del
 * monto sigue calculandose aqui como {@code total - Σ notas previas}; la CxC acota
 * ademas la disminucion a {@code [0, saldo]} de forma defensiva.</p>
 */
@Service
public class ServicioNotasCredito {

    /** Tipo de recurso de auditoria/RBAC de la Nota de Credito. */
    static final String RECURSO_NOTA_CREDITO = "nota_credito";

    private final NotaCreditoRepository notaCreditoRepository;
    private final FacturaReferenciadaPort facturaReferenciada;
    private final PacPort pac;
    private final AuditoriaPort auditoria;
    private final CuentaPorCobrarPort cuentaPorCobrar;

    public ServicioNotasCredito(NotaCreditoRepository notaCreditoRepository,
                                FacturaReferenciadaPort facturaReferenciada,
                                PacPort pac,
                                AuditoriaPort auditoria,
                                CuentaPorCobrarPort cuentaPorCobrar) {
        this.notaCreditoRepository = notaCreditoRepository;
        this.facturaReferenciada = facturaReferenciada;
        this.pac = pac;
        this.auditoria = auditoria;
        this.cuentaPorCobrar = cuentaPorCobrar;
    }

    /**
     * Emite una Nota de Credito en {@code borrador} referenciando una Factura
     * timbrada, con el monto acotado por el saldo disponible de la Factura
     * (Req 37.1, 37.2; Property 14).
     *
     * @param comando datos de la Nota de Credito a emitir.
     * @return el DTO de la Nota de Credito emitida (en {@code borrador}).
     * @throws RecursoNoEncontradoException si la Factura no es accesible (404).
     * @throws ReglaNegocioException si la Factura no esta timbrada, el monto no es
     *         positivo o excede el saldo disponible (422, Req 37.2).
     */
    @Transactional
    public NotaCreditoDto emitir(EmitirNotaCreditoCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.facturaId() == null) {
            throw new ReglaNegocioException(
                    "La Nota de Credito debe referenciar una Factura timbrada.");
        }
        FacturaReferenciada factura = facturaReferenciada.buscar(comando.facturaId())
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, "factura", comando.facturaId());
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Factura referenciada para la Nota de Credito.");
                });
        if (!factura.estaTimbrada()) {
            throw new ReglaNegocioException(
                    "La Nota de Credito solo puede referenciar una Factura en estado 'timbrada'.");
        }

        BigDecimal notasPrevias = notaCreditoRepository.sumarMontoPorFactura(comando.facturaId());
        BigDecimal montoValidado = NotaCreditoValidaciones.validarMontoContraSaldo(
                comando.monto(), factura.total(), notasPrevias);

        NotaCredito nota = NotaCredito.emitir(
                comando.facturaId(), factura.clienteId(), montoValidado, actor);
        NotaCredito guardada = notaCreditoRepository.save(nota);
        auditar(actor, "crear", guardada.getId(),
                "emitida nota de credito en estado '" + guardada.getEstado().valorBd()
                        + "' [factura=" + comando.facturaId() + ", monto="
                        + guardada.getMonto().toPlainString() + "]", null, null);
        // Req 37.1: la emision de la Nota de Credito disminuye el saldo de la
        // Cuenta_Por_Cobrar de la Factura por el monto (acotado a [0, saldo] por CxC).
        cuentaPorCobrar.disminuirPorNotaCredito(comando.facturaId(), guardada.getMonto());
        return NotaCreditoDto.de(guardada);
    }

    /**
     * Solicita el Timbrado del CFDI de egreso de una Nota de Credito en
     * {@code borrador} al PAC (Req 37.1). En exito registra Folio_Fiscal + sello y
     * transita a {@code timbrada}; en rechazo conserva {@code borrador} y lanza 422
     * con el motivo del PAC. Ambos desenlaces se auditan.
     *
     * @param notaId identificador de la Nota de Credito a timbrar.
     * @return el DTO de la Nota de Credito {@code timbrada}.
     * @throws RecursoNoEncontradoException si la Nota no es accesible (404).
     * @throws ReglaNegocioException si la Nota no esta en {@code borrador} o el PAC
     *         rechaza el Timbrado (422).
     */
    @Transactional
    public NotaCreditoDto timbrar(UUID notaId) {
        String actor = actorActual();
        NotaCredito nota = cargar(notaId, actor);
        if (nota.getEstado() != EstadoNotaCredito.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo se puede timbrar una Nota de Credito en estado 'borrador'.");
        }
        FacturaReferenciada factura = facturaReferenciada.buscar(nota.getFacturaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Factura referenciada para la Nota de Credito."));
        SolicitudTimbrado solicitud = new SolicitudTimbrado(
                TenantContext.require(), nota.getId(),
                factura.estado(), "Nota de Credito", "00000", "601", "G02",
                nota.getMonto(), BigDecimal.ZERO, BigDecimal.ZERO, nota.getMonto());
        ResultadoTimbrado resultado = pac.timbrar(solicitud);

        if (!resultado.exito()) {
            auditar(actor, "timbrado_rechazado", nota.getId(),
                    "el PAC rechazo el timbrado de la nota de credito: " + resultado.mensajeError(),
                    null, null);
            throw new ReglaNegocioException(
                    "El PAC rechazo el timbrado: " + resultado.mensajeError());
        }

        nota.timbrar(resultado.folioFiscal(), resultado.selloSat(), resultado.fechaTimbrado(), actor);
        NotaCredito guardada = notaCreditoRepository.save(nota);
        auditar(actor, "timbrar", guardada.getId(),
                "nota de credito timbrada [folio_fiscal=" + guardada.getFolioFiscal() + "]",
                EstadoNotaCredito.BORRADOR.valorBd(), EstadoNotaCredito.TIMBRADA.valorBd());
        return NotaCreditoDto.de(guardada);
    }

    /**
     * Consulta puntual de una Nota de Credito del tenant (Req 23.3).
     *
     * @param notaId identificador de la Nota de Credito.
     * @return el DTO de la Nota de Credito.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public NotaCreditoDto consultar(UUID notaId) {
        String actor = actorActual();
        return NotaCreditoDto.de(cargar(notaId, actor));
    }

    /**
     * Listado paginado de Notas de Credito del tenant con filtros opcionales por
     * Factura y por estado.
     *
     * @param facturaId Factura a filtrar; {@code null} no filtra.
     * @param estado    etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Notas de Credito como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<NotaCreditoDto> listar(UUID facturaId, String estado, Pageable pageable) {
        EstadoNotaCredito filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return notaCreditoRepository.buscarConFiltros(facturaId, filtro, pageable)
                .map(NotaCreditoDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private NotaCredito cargar(UUID notaId, String actor) {
        if (notaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Nota de Credito solicitada.");
        }
        return notaCreditoRepository.findById(notaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_NOTA_CREDITO, notaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Nota de Credito solicitada.");
                });
    }

    private EstadoNotaCredito interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Nota de Credito es obligatorio.");
        }
        try {
            return EstadoNotaCredito.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Nota de Credito desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID notaId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_NOTA_CREDITO,
                detalle + " [id=" + notaId + "]", valorAnterior, valorNuevo));
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

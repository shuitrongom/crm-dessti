package com.dessti.crm.compras.factura.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.factura.adapter.out.persistence.FacturaProveedorRepository;
import com.dessti.crm.compras.factura.domain.ConciliacionTresVias;
import com.dessti.crm.compras.factura.domain.ConciliacionTresVias.RenglonConciliacion;
import com.dessti.crm.compras.factura.domain.EstadoFacturaProveedor;
import com.dessti.crm.compras.factura.domain.FacturaProveedor;
import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.PartidaOrdenCompra;
import com.dessti.crm.compras.recepcion.adapter.out.persistence.RecepcionMercanciaRepository;
import com.dessti.crm.contabilidad.cxp.application.CuentaPorPagarPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna las Facturas de Proveedor y la
 * Conciliacion_Tres_Vias (Req 33). Replica el patron de
 * {@code ServicioOrdenesCompra} / {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 33)</h2>
 * <ul>
 *   <li><strong>registrarFactura (Req 33.1, 33.2):</strong> verifica que la
 *       Orden_Compra exista (404), deriva de ella el Proveedor (denormalizado,
 *       Req 33.8), valida folio y monto y persiste la factura en estado
 *       {@code registrada}; audita.</li>
 *   <li><strong>autorizarPago (Req 33.3–33.5, 33.7):</strong> ejecuta la
 *       Conciliacion_Tres_Vias (funcion pura {@link ConciliacionTresVias},
 *       Property 12) por cada partida (cantidad facturada &lt;= recibida acumulada;
 *       precio dentro de la tolerancia configurable). Si hay discrepancia, marca la
 *       factura {@code discrepancia} y NO autoriza (Req 33.4); si concilia, marca
 *       {@code conciliada} (Req 33.5). La autorizacion efectiva del pago (paso a
 *       {@code pagada}) solo se permite desde {@code conciliada} (Req 33.7).</li>
 *   <li><strong>cambiarEstado (Req 33.6):</strong> aplica la maquina de estados
 *       pura (409 si la transicion es invalida); util para autorizar el pago
 *       ({@code conciliada -> pagada}).</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria si no es accesible.</li>
 *   <li><strong>listar (Req 33.8):</strong> listado paginado (20/100) con filtros
 *       por Proveedor, Orden_Compra y estado.</li>
 * </ul>
 *
 * <h2>Integracion con Cuentas_Por_Pagar (Req 42.1)</h2>
 * <p>Cuando {@link #conciliar(UUID)} deja la Factura_Proveedor en estado
 * {@code conciliada}, se registra su Cuenta_Por_Pagar por el saldo (= monto) via el
 * puerto {@link CuentaPorPagarPort} (idempotente). Es la unica adicion no invasiva a
 * este servicio para cerrar la integracion con CxP del bloque 30; el resto del
 * comportamiento se preserva.</p>
 *
 * <h2>Modelado de la conciliacion</h2>
 * <p>La factura persiste el monto total (Req 33.2), no el detalle por partida. La
 * conciliacion contrasta, por cada Partida_Orden_Compra: la cantidad y el precio de
 * la Orden_Compra (lo facturado esperado) contra la cantidad recibida acumulada en
 * las Recepcion_Mercancia (Req 32) y contra el propio precio de la Orden_Compra
 * dentro de la tolerancia configurable. Asi la conciliacion exige recepcion
 * completa por partida antes de habilitar el pago, coherente con el Req 33.3. La
 * decision atomica reside en la funcion pura {@link ConciliacionTresVias} (Property
 * 12), que nunca concilia fuera de tolerancia.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 33.9)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada cambio de estado se audita con el estado anterior y el
 * nuevo (Req 33.9).</p>
 */
@Service
public class ServicioFacturasProveedor {

    /** Tipo de recurso de auditoria/RBAC de la Factura_Proveedor. */
    static final String RECURSO_FACTURA = "factura_proveedor";

    private final FacturaProveedorRepository facturaRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final RecepcionMercanciaRepository recepcionRepository;
    private final ConciliacionProperties propiedades;
    private final AuditoriaPort auditoria;
    private final CuentaPorPagarPort cuentaPorPagar;

    public ServicioFacturasProveedor(FacturaProveedorRepository facturaRepository,
                                     OrdenCompraRepository ordenCompraRepository,
                                     RecepcionMercanciaRepository recepcionRepository,
                                     ConciliacionProperties propiedades,
                                     AuditoriaPort auditoria,
                                     CuentaPorPagarPort cuentaPorPagar) {
        this.facturaRepository = facturaRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.recepcionRepository = recepcionRepository;
        this.propiedades = propiedades;
        this.auditoria = auditoria;
        this.cuentaPorPagar = cuentaPorPagar;
    }

    /**
     * Registra una Factura_Proveedor en estado {@code registrada} asociada a una
     * Orden_Compra existente (Req 33.1, 33.2).
     *
     * @param comando datos de la factura a registrar.
     * @return el DTO de la factura creada.
     * @throws RecursoNoEncontradoException si la Orden_Compra no es accesible en el
     *         tenant (404, Req 23.3).
     * @throws ReglaNegocioException si faltan datos o el monto es negativo (422,
     *         Req 33.2).
     */
    @Transactional
    public FacturaProveedorDto registrarFactura(RegistrarFacturaProveedorCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.ordenCompraId() == null) {
            throw new ReglaNegocioException(
                    "La Factura_Proveedor debe asociarse a una Orden_Compra existente.");
        }
        OrdenCompra orden = cargarOrden(comando.ordenCompraId(), actor);
        FacturaProveedor factura = FacturaProveedor.registrar(
                orden.getId(), orden.getProveedorId(),
                comando.folioProveedor(), comando.monto(), actor);
        FacturaProveedor guardada = facturaRepository.save(factura);
        auditar(actor, "crear", guardada.getId(),
                "registrada factura_proveedor en estado '" + guardada.getEstado().valorBd()
                        + "' [orden_compra=" + orden.getId() + ", proveedor="
                        + guardada.getProveedorId() + ", folio=" + guardada.getFolioProveedor() + "]",
                null, guardada.getEstado().valorBd());
        return FacturaProveedorDto.de(guardada);
    }

    /**
     * Ejecuta la Conciliacion_Tres_Vias de una Factura_Proveedor y actualiza su
     * estado (Req 33.3, 33.4, 33.5). Debe partir del estado {@code registrada}:
     *
     * <ul>
     *   <li>Si TODAS las partidas concilian (cantidad y precio dentro de tolerancia,
     *       Property 12), marca la factura {@code conciliada} y la habilita para pago
     *       (Req 33.5).</li>
     *   <li>Si hay discrepancia (cantidad facturada &gt; recibida o precio fuera de
     *       tolerancia), marca la factura {@code discrepancia} y NO autoriza el pago
     *       (Req 33.4).</li>
     * </ul>
     *
     * @param facturaId identificador de la factura a conciliar.
     * @return el DTO de la factura con su nuevo estado ({@code conciliada} o
     *         {@code discrepancia}).
     * @throws RecursoNoEncontradoException si la factura o su Orden_Compra no son
     *         accesibles (404).
     * @throws ReglaNegocioException si la factura no esta en estado {@code registrada}
     *         (422); una factura ya conciliada/pagada/en discrepancia no se
     *         re-concilia.
     */
    @Transactional
    public FacturaProveedorDto conciliar(UUID facturaId) {
        String actor = actorActual();
        FacturaProveedor factura = cargar(facturaId, actor);
        if (factura.getEstado() != EstadoFacturaProveedor.REGISTRADA) {
            throw new ReglaNegocioException(
                    "solo se puede conciliar una Factura_Proveedor en estado 'registrada'.");
        }
        OrdenCompra orden = cargarOrden(factura.getOrdenCompraId(), actor);
        List<RenglonConciliacion> renglones = construirRenglones(orden);

        boolean concilia = ConciliacionTresVias.esConciliable(renglones, propiedades.toleranciaPrecio());
        EstadoFacturaProveedor anterior = factura.getEstado();
        EstadoFacturaProveedor destino = concilia
                ? EstadoFacturaProveedor.CONCILIADA
                : EstadoFacturaProveedor.DISCREPANCIA;
        factura.cambiarEstado(destino, actor);
        FacturaProveedor guardada = facturaRepository.save(factura);
        auditar(actor, "cambiar_estado", guardada.getId(),
                (concilia ? "conciliacion satisfactoria" : "discrepancia detectada")
                        + " en la Conciliacion_Tres_Vias [orden_compra=" + orden.getId()
                        + ", tolerancia=" + propiedades.toleranciaPrecio().toPlainString() + "]",
                anterior.valorBd(), destino.valorBd());
        if (concilia) {
            // Req 42.1: al quedar conciliada, registrar su Cuenta_Por_Pagar por el
            // saldo (= monto). El puerto es idempotente (no duplica ante reintentos).
            cuentaPorPagar.registrarPorFacturaProveedorConciliada(
                    guardada.getId(), guardada.getProveedorId(), guardada.getMonto());
        }
        return FacturaProveedorDto.de(guardada);
    }

    /**
     * Autoriza el pago de una Factura_Proveedor (Req 33.7): solo se permite desde el
     * estado {@code conciliada}, llevando la factura a {@code pagada}. Si el estado
     * no es {@code conciliada}, se rechaza conservando el estado actual.
     *
     * @param facturaId identificador de la factura.
     * @return el DTO de la factura en estado {@code pagada}.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si la factura no esta {@code conciliada} (422,
     *         Req 33.7).
     */
    @Transactional
    public FacturaProveedorDto autorizarPago(UUID facturaId) {
        String actor = actorActual();
        FacturaProveedor factura = cargar(facturaId, actor);
        if (factura.getEstado() != EstadoFacturaProveedor.CONCILIADA) {
            throw new ReglaNegocioException(
                    "se requiere una Factura_Proveedor conciliada para autorizar el pago.");
        }
        EstadoFacturaProveedor anterior = factura.getEstado();
        factura.cambiarEstado(EstadoFacturaProveedor.PAGADA, actor);
        FacturaProveedor guardada = facturaRepository.save(factura);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "pago autorizado de la Factura_Proveedor",
                anterior.valorBd(), EstadoFacturaProveedor.PAGADA.valorBd());
        return FacturaProveedorDto.de(guardada);
    }

    /**
     * Consulta puntual de una Factura_Proveedor del tenant (Req 23.3).
     *
     * @param facturaId identificador de la factura.
     * @return el DTO de la factura.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public FacturaProveedorDto consultar(UUID facturaId) {
        String actor = actorActual();
        return FacturaProveedorDto.de(cargar(facturaId, actor));
    }

    /**
     * Listado paginado de Facturas de Proveedor del tenant con filtros opcionales
     * por Proveedor, Orden_Compra y estado (Req 33.8). Un filtro nulo no restringe.
     *
     * @param proveedorId   Proveedor a filtrar; {@code null} no filtra.
     * @param ordenCompraId Orden_Compra a filtrar; {@code null} no filtra.
     * @param estado        etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de facturas como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<FacturaProveedorDto> listar(UUID proveedorId, UUID ordenCompraId,
                                            String estado, Pageable pageable) {
        EstadoFacturaProveedor filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return facturaRepository.buscarConFiltros(proveedorId, ordenCompraId, filtro, pageable)
                .map(FacturaProveedorDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Construye los renglones de conciliacion a partir de las partidas de la
     * Orden_Compra y del recibido acumulado por partida (Req 33.3). Para cada
     * partida: cantidad facturada esperada = cantidad ordenada; precio facturado
     * esperado = precio de la Orden_Compra; cantidad recibida = acumulado de las
     * recepciones.
     */
    private List<RenglonConciliacion> construirRenglones(OrdenCompra orden) {
        Map<UUID, BigDecimal> recibido = recibidoPorPartida(orden.getId());
        List<RenglonConciliacion> renglones = new ArrayList<>();
        for (PartidaOrdenCompra partida : orden.getPartidas()) {
            BigDecimal ordenada = BigDecimal.valueOf(partida.getCantidad());
            BigDecimal recibida = recibido.getOrDefault(partida.getId(), BigDecimal.ZERO);
            renglones.add(new RenglonConciliacion(
                    ordenada, recibida, partida.getPrecioUnitario(), partida.getPrecioUnitario()));
        }
        if (renglones.isEmpty()) {
            throw new ReglaNegocioException(
                    "La Orden_Compra no tiene partidas para conciliar la Factura_Proveedor.");
        }
        return renglones;
    }

    private Map<UUID, BigDecimal> recibidoPorPartida(UUID ordenCompraId) {
        Map<UUID, BigDecimal> acumulado = new HashMap<>();
        for (RecepcionMercanciaRepository.RecibidoPorPartida fila
                : recepcionRepository.sumarRecibidoPorPartida(ordenCompraId)) {
            BigDecimal recibida = (fila.getRecibida() == null) ? BigDecimal.ZERO : fila.getRecibida();
            acumulado.put(fila.getPartidaOrdenCompraId(), recibida);
        }
        return acumulado;
    }

    private OrdenCompra cargarOrden(UUID ordenId, String actor) {
        return ordenCompraRepository.findById(ordenId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, "orden_compra", ordenId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Orden_Compra indicada para la Factura_Proveedor.");
                });
    }

    private FacturaProveedor cargar(UUID facturaId, String actor) {
        if (facturaId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Factura_Proveedor solicitada.");
        }
        return facturaRepository.findById(facturaId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_FACTURA, facturaId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Factura_Proveedor solicitada.");
                });
    }

    private EstadoFacturaProveedor interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado a filtrar es obligatorio.");
        }
        try {
            return EstadoFacturaProveedor.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Factura_Proveedor desconocido: " + etiqueta);
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

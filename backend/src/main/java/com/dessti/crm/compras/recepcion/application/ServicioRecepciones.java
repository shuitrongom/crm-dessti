package com.dessti.crm.compras.recepcion.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.ordencompra.adapter.out.persistence.OrdenCompraRepository;
import com.dessti.crm.compras.ordencompra.domain.EstadoOrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.OrdenCompra;
import com.dessti.crm.compras.ordencompra.domain.PartidaOrdenCompra;
import com.dessti.crm.compras.recepcion.adapter.out.persistence.RecepcionMercanciaRepository;
import com.dessti.crm.compras.recepcion.domain.PartidaRecepcion;
import com.dessti.crm.compras.recepcion.domain.RecepcionMercancia;
import com.dessti.crm.compras.recepcion.domain.ReglasRecepcion;
import com.dessti.crm.compras.recepcion.domain.ReglasRecepcion.AvancePartida;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterial;
import com.dessti.crm.operacion.inventario.application.RecepcionMaterialPort;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

import java.time.Clock;
import java.time.Instant;

/**
 * Servicio de aplicacion que gobierna la Recepcion_Mercancia y su integracion con
 * el inventario (Req 32). Replica el patron de {@code ServicioOrdenesCompra} /
 * {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Operaciones (Req 32)</h2>
 * <ul>
 *   <li><strong>registrarRecepcion (Req 32.1–32.6, 32.8):</strong> carga la
 *       Orden_Compra (404 si no accesible); rechaza si su estado no admite
 *       recepciones (422, Req 32.2); valida por partida que el acumulado no exceda
 *       lo ordenado (422, Property 10, Req 32.3); persiste la recepcion y sus
 *       renglones; genera un Movimiento_Inventario {@code entrada} por Material via
 *       {@link RecepcionMaterialPort} (Req 32.4); deriva el nuevo estado de la
 *       Orden_Compra (Property 11, Req 32.5, 32.6) y lo aplica con la maquina de
 *       estados de la Orden_Compra; audita el alta y el cambio de estado.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 *   <li><strong>listar (Req 32.7):</strong> listado paginado (20/100) con filtro
 *       por Orden_Compra.</li>
 * </ul>
 *
 * <h2>Atomicidad</h2>
 * <p>Todo ocurre dentro de una unica transaccion: la persistencia de la recepcion,
 * las entradas de inventario y el cambio de estado de la Orden_Compra. Si el tope
 * acumulado se excede (Req 32.3) la recepcion se rechaza antes de mutar inventario,
 * conservando las existencias sin cambios.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 32.8)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} con el actor derivado del contexto.</p>
 */
@Service
public class ServicioRecepciones {

    /** Tipo de recurso de auditoria/RBAC de la Recepcion_Mercancia. */
    static final String RECURSO_RECEPCION = "recepcion_mercancia";

    /** Tipo de recurso de auditoria/RBAC de la Orden_Compra. */
    static final String RECURSO_ORDEN_COMPRA = "orden_compra";

    /** Estados de Orden_Compra que admiten recepciones (Req 32.2). */
    private static final Set<EstadoOrdenCompra> ESTADOS_ADMITEN_RECEPCION =
            EnumSet.of(EstadoOrdenCompra.ABIERTA, EstadoOrdenCompra.RECIBIDA_PARCIAL);

    private final RecepcionMercanciaRepository recepcionRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final RecepcionMaterialPort recepcionMaterial;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioRecepciones(RecepcionMercanciaRepository recepcionRepository,
                               OrdenCompraRepository ordenCompraRepository,
                               RecepcionMaterialPort recepcionMaterial,
                               AuditoriaPort auditoria,
                               Clock clock) {
        this.recepcionRepository = recepcionRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.recepcionMaterial = recepcionMaterial;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Registra una Recepcion_Mercancia contra una Orden_Compra y actualiza el
     * inventario y el estado de la Orden_Compra (Req 32.1–32.6, 32.8).
     *
     * @param comando datos de la recepcion a registrar.
     * @return el DTO de la recepcion creada.
     * @throws RecursoNoEncontradoException si la Orden_Compra no es accesible en el
     *         tenant (404, Req 23.3).
     * @throws ReglaNegocioException si la Orden_Compra no admite recepciones
     *         (Req 32.2), si un renglon no corresponde a la Orden, o si el acumulado
     *         recibido excede lo ordenado (Property 10, Req 32.3) — 422.
     */
    @Transactional
    public RecepcionMercanciaDto registrarRecepcion(RegistrarRecepcionCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.ordenCompraId() == null) {
            throw new ReglaNegocioException(
                    "La Recepcion_Mercancia debe asociarse a una Orden_Compra existente.");
        }
        List<RegistrarPartidaRecepcionCommand> renglones =
                (comando.partidas() == null) ? List.of() : comando.partidas();
        if (renglones.isEmpty()) {
            throw new ReglaNegocioException(
                    "La Recepcion_Mercancia debe incluir al menos un renglon recibido.");
        }

        OrdenCompra orden = cargarOrden(comando.ordenCompraId(), actor);

        // Req 32.2: la Orden_Compra debe estar en un estado que admita recepciones.
        if (!ESTADOS_ADMITEN_RECEPCION.contains(orden.getEstado())) {
            throw new ReglaNegocioException(
                    "la Orden_Compra no admite recepciones en su estado actual ('"
                            + orden.getEstado().valorBd() + "').");
        }

        // Indexar las partidas de la Orden_Compra por id para validar y derivar el
        // Material de cada renglon recibido.
        Map<UUID, PartidaOrdenCompra> partidasOrden = new HashMap<>();
        for (PartidaOrdenCompra partida : orden.getPartidas()) {
            partidasOrden.put(partida.getId(), partida);
        }

        // Acumulado previamente recibido por Partida_Orden_Compra (Req 32.3).
        Map<UUID, BigDecimal> recibidoPrevio = recibidoPrevioPorPartida(orden.getId());

        // Acumular la cantidad recibida NUEVA por partida (un renglon por partida se
        // suma; se permite mas de un renglon por partida en una misma recepcion).
        Map<UUID, BigDecimal> recibidoNuevo = new HashMap<>();
        List<PartidaRecepcion> partidasRecepcion = new ArrayList<>();
        List<ConsumoMaterial> entradasInventario = new ArrayList<>();

        for (RegistrarPartidaRecepcionCommand renglon : renglones) {
            if (renglon == null || renglon.partidaOrdenCompraId() == null) {
                throw new ReglaNegocioException(
                        "Cada renglon de recepcion debe referir una Partida_Orden_Compra.");
            }
            PartidaOrdenCompra partidaOrden = partidasOrden.get(renglon.partidaOrdenCompraId());
            if (partidaOrden == null) {
                throw new ReglaNegocioException(
                        "El renglon de recepcion no corresponde a una partida de la Orden_Compra indicada.");
            }
            BigDecimal cantidad = renglon.cantidadRecibida();
            if (cantidad == null || cantidad.signum() <= 0) {
                throw new ReglaNegocioException(
                        "La cantidad recibida del renglon debe ser estrictamente positiva.");
            }
            recibidoNuevo.merge(renglon.partidaOrdenCompraId(), cantidad, BigDecimal::add);

            PartidaRecepcion partida = PartidaRecepcion.crear(
                    partidaOrden.getId(), partidaOrden.getMaterialId(), cantidad, actor);
            partidasRecepcion.add(partida);
            entradasInventario.add(new ConsumoMaterial(partidaOrden.getMaterialId(), cantidad));
        }

        // Property 10 (Req 32.3): por cada partida, previa + nueva <= ordenada.
        for (Map.Entry<UUID, BigDecimal> entrada : recibidoNuevo.entrySet()) {
            PartidaOrdenCompra partidaOrden = partidasOrden.get(entrada.getKey());
            BigDecimal ordenada = BigDecimal.valueOf(partidaOrden.getCantidad());
            BigDecimal previa = recibidoPrevio.getOrDefault(entrada.getKey(), BigDecimal.ZERO);
            ReglasRecepcion.validarNoExcederOrdenado(ordenada, previa, entrada.getValue());
        }

        // Persistir la recepcion y sus renglones.
        RecepcionMercancia recepcion = RecepcionMercancia.crear(
                orden.getId(), Instant.now(clock), partidasRecepcion, actor);
        RecepcionMercancia guardada = recepcionRepository.save(recepcion);

        // Req 32.4: generar Movimiento_Inventario 'entrada' por Material e
        // incrementar existencias (via el puerto del inventario).
        recepcionMaterial.recibirDeOrdenCompra(guardada.getId(), entradasInventario);

        auditar(actor, "crear", RECURSO_RECEPCION, guardada.getId(),
                "registrada recepcion_mercancia con " + guardada.getPartidas().size()
                        + " renglon(es) [orden_compra=" + orden.getId() + "]",
                null, null);

        // Property 11 (Req 32.5, 32.6): derivar y aplicar el nuevo estado de la OC.
        derivarYAplicarEstadoOrden(orden, recibidoPrevio, recibidoNuevo, actor);

        return RecepcionMercanciaDto.de(guardada);
    }

    /**
     * Consulta puntual de una Recepcion_Mercancia del tenant (Req 23.3).
     *
     * @param recepcionId identificador de la recepcion.
     * @return el DTO de la recepcion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public RecepcionMercanciaDto consultar(UUID recepcionId) {
        String actor = actorActual();
        return RecepcionMercanciaDto.de(cargar(recepcionId, actor));
    }

    /**
     * Listado paginado de Recepciones de Mercancia del tenant con filtro opcional
     * por Orden_Compra (Req 32.7). Un filtro nulo no restringe.
     *
     * @param ordenCompraId Orden_Compra a filtrar; {@code null} no filtra.
     * @param pageable      parametros de paginacion ya acotados (20/100).
     * @return la pagina de recepciones como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<RecepcionMercanciaDto> listar(UUID ordenCompraId, Pageable pageable) {
        return recepcionRepository.buscarPorOrdenCompra(ordenCompraId, pageable)
                .map(RecepcionMercanciaDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Deriva el nuevo estado de la Orden_Compra a partir del acumulado recibido tras
     * esta recepcion (Property 11) y lo aplica con la maquina de estados de la
     * Orden_Compra, transitando por estados intermedios validos cuando corresponde
     * (por ejemplo {@code abierta -> recibida_parcial -> recibida_total}).
     */
    private void derivarYAplicarEstadoOrden(OrdenCompra orden, Map<UUID, BigDecimal> recibidoPrevio,
                                            Map<UUID, BigDecimal> recibidoNuevo, String actor) {
        List<AvancePartida> avances = new ArrayList<>();
        for (PartidaOrdenCompra partida : orden.getPartidas()) {
            BigDecimal ordenada = BigDecimal.valueOf(partida.getCantidad());
            BigDecimal acumulado = recibidoPrevio.getOrDefault(partida.getId(), BigDecimal.ZERO)
                    .add(recibidoNuevo.getOrDefault(partida.getId(), BigDecimal.ZERO));
            avances.add(new AvancePartida(ordenada, acumulado));
        }
        EstadoOrdenCompra destino = ReglasRecepcion.derivarEstadoOrdenCompra(avances);
        aplicarEstadoOrden(orden, destino, actor);
    }

    /**
     * Aplica el estado destino a la Orden_Compra transitando por estados intermedios
     * validos segun su maquina de estados (Req 31.6). Si el destino coincide con el
     * estado actual no hace nada. {@code abierta} nunca es un destino de recepcion
     * (siempre hay al menos un renglon recibido).
     */
    private void aplicarEstadoOrden(OrdenCompra orden, EstadoOrdenCompra destino, String actor) {
        if (orden.getEstado() == destino) {
            return;
        }
        // abierta -> recibida_parcial (paso obligado antes de recibida_total).
        if (orden.getEstado() == EstadoOrdenCompra.ABIERTA
                && (destino == EstadoOrdenCompra.RECIBIDA_PARCIAL
                    || destino == EstadoOrdenCompra.RECIBIDA_TOTAL)) {
            cambiarEstadoOrden(orden, EstadoOrdenCompra.RECIBIDA_PARCIAL, actor);
        }
        // recibida_parcial -> recibida_total.
        if (destino == EstadoOrdenCompra.RECIBIDA_TOTAL
                && orden.getEstado() == EstadoOrdenCompra.RECIBIDA_PARCIAL) {
            cambiarEstadoOrden(orden, EstadoOrdenCompra.RECIBIDA_TOTAL, actor);
        }
    }

    private void cambiarEstadoOrden(OrdenCompra orden, EstadoOrdenCompra destino, String actor) {
        EstadoOrdenCompra anterior = orden.getEstado();
        orden.cambiarEstado(destino, actor);
        ordenCompraRepository.save(orden);
        auditar(actor, "cambiar_estado", RECURSO_ORDEN_COMPRA, orden.getId(),
                "estado de orden_compra derivado por recepcion '" + anterior.valorBd()
                        + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
    }

    private Map<UUID, BigDecimal> recibidoPrevioPorPartida(UUID ordenCompraId) {
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
                    auditarAccesoCruzado(actor, RECURSO_ORDEN_COMPRA, ordenId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Orden_Compra indicada para la recepcion.");
                });
    }

    private RecepcionMercancia cargar(UUID recepcionId, String actor) {
        if (recepcionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Recepcion_Mercancia solicitada.");
        }
        return recepcionRepository.findById(recepcionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_RECEPCION, recepcionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Recepcion_Mercancia solicitada.");
                });
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle, String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]", valorAnterior, valorNuevo));
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

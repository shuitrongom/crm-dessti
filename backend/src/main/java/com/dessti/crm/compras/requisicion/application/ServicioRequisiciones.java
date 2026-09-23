package com.dessti.crm.compras.requisicion.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.compras.ordencompra.application.CrearOrdenCompraCommand;
import com.dessti.crm.compras.ordencompra.application.ServicioOrdenesCompra;
import com.dessti.crm.compras.requisicion.adapter.out.persistence.RequisicionCompraRepository;
import com.dessti.crm.compras.requisicion.domain.EstadoRequisicionCompra;
import com.dessti.crm.compras.requisicion.domain.PartidaRequisicion;
import com.dessti.crm.compras.requisicion.domain.RequisicionCompra;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las
 * {@link RequisicionCompra} (Req 30). Replica el patron establecido por
 * {@code ServicioCotizaciones}/{@code ServicioOrdenesFabricacion} (maquina de
 * estados + generacion desde estado final).
 *
 * <h2>Operaciones (Req 30)</h2>
 * <ul>
 *   <li><strong>crearRequisicion (Req 30.1):</strong> exige al menos una partida
 *       (Material + cantidad 1..999999), verifica que cada Material exista y este
 *       activo (via {@link MaterialExistentePort}; 404 si no), fija estado inicial
 *       {@code borrador}, persiste y audita.</li>
 *   <li><strong>cambiarEstado (Req 30.2):</strong> aplica la maquina de estados pura
 *       (409 si la transicion es invalida) y audita el estado anterior y el nuevo.</li>
 *   <li><strong>generarOrdenCompra (Req 30.3, 30.4):</strong> genera una
 *       Orden_Compra <em>si y solo si</em> la requisicion esta {@code aprobada}
 *       (422 en caso contrario, mensaje "se requiere una Requisicion_Compra
 *       aprobada"); crea la Orden vinculada a la requisicion (via
 *       {@link ServicioOrdenesCompra}), la enlaza de vuelta y devuelve su id.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 *   <li><strong>listar (Req 30.5):</strong> listado paginado (20/100) con filtro por
 *       estado.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 30.6)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} con el actor derivado del contexto; el cambio de estado
 * incluye el estado anterior y el nuevo (Req 30.6).</p>
 */
@Service
public class ServicioRequisiciones {

    /** Tipo de recurso de auditoria/RBAC de la Requisicion_Compra. */
    static final String RECURSO_REQUISICION = "requisicion_compra";

    private final RequisicionCompraRepository requisicionRepository;
    private final MaterialExistentePort materialExistente;
    private final ServicioOrdenesCompra servicioOrdenesCompra;
    private final AuditoriaPort auditoria;

    public ServicioRequisiciones(RequisicionCompraRepository requisicionRepository,
                                 MaterialExistentePort materialExistente,
                                 ServicioOrdenesCompra servicioOrdenesCompra,
                                 AuditoriaPort auditoria) {
        this.requisicionRepository = requisicionRepository;
        this.materialExistente = materialExistente;
        this.servicioOrdenesCompra = servicioOrdenesCompra;
        this.auditoria = auditoria;
    }

    /**
     * Da de alta una Requisicion_Compra con al menos una partida (Material +
     * cantidad), en estado inicial {@code borrador} (Req 30.1).
     *
     * @param comando datos de la Requisicion_Compra a crear.
     * @return el DTO de la Requisicion_Compra creada.
     * @throws RecursoNoEncontradoException si algun Material no existe/activo en el
     *         tenant (404, Req 30.1, 23.3).
     * @throws ReglaNegocioException si faltan partidas o una es invalida (422,
     *         Req 30.1).
     */
    @Transactional
    public RequisicionCompraDto crearRequisicion(CrearRequisicionCompraCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Requisicion_Compra son obligatorios.");
        }
        List<CrearPartidaRequisicionCommand> comandosPartida =
                (comando.partidas() == null) ? List.of() : comando.partidas();
        if (comandosPartida.isEmpty()) {
            throw new ReglaNegocioException(
                    "La Requisicion_Compra debe tener al menos una Partida_Requisicion.");
        }
        List<PartidaRequisicion> partidas = new ArrayList<>();
        for (CrearPartidaRequisicionCommand pc : comandosPartida) {
            partidas.add(construirPartida(pc, actor));
        }
        RequisicionCompra requisicion = RequisicionCompra.crear(partidas, actor);
        RequisicionCompra guardada = requisicionRepository.save(requisicion);
        auditar(actor, "crear", guardada.getId(),
                "creada requisicion_compra en estado '" + guardada.getEstado().valorBd()
                        + "' con " + guardada.getPartidas().size() + " partida(s)", null, null);
        return RequisicionCompraDto.de(guardada);
    }

    /**
     * Cambia el estado de una Requisicion_Compra aplicando la maquina de estados
     * pura (Req 30.2) y auditando el estado anterior y el nuevo (Req 30.6).
     *
     * @param requisicionId identificador de la Requisicion_Compra.
     * @param nuevoEstado   etiqueta del estado destino; obligatoria (Req 30.2).
     * @return el DTO de la Requisicion_Compra con su nuevo estado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si el estado es nulo/desconocido (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 30.2).
     */
    @Transactional
    public RequisicionCompraDto cambiarEstado(UUID requisicionId, String nuevoEstado) {
        String actor = actorActual();
        EstadoRequisicionCompra destino = interpretarEstado(nuevoEstado);
        RequisicionCompra requisicion = cargar(requisicionId, actor);
        EstadoRequisicionCompra anterior = requisicion.getEstado();
        requisicion.cambiarEstado(destino, actor);
        RequisicionCompra guardada = requisicionRepository.save(requisicion);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "cambio de estado '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
        return RequisicionCompraDto.de(guardada);
    }

    /**
     * Genera una Orden_Compra a partir de una Requisicion_Compra aprobada (Req 30.3,
     * 30.4). Precondicion: la requisicion debe estar en estado {@code aprobada}; en
     * caso contrario se rechaza con 422 y no se crea ninguna Orden. La Orden se crea
     * con el Proveedor y las partidas (con precio) del comando, vinculada a la
     * requisicion, y esta se enlaza de vuelta a la Orden generada.
     *
     * @param requisicionId identificador de la Requisicion_Compra aprobada.
     * @param comando       Proveedor y partidas (con precio) de la Orden a generar.
     * @return el DTO de la Requisicion_Compra con la Orden_Compra generada enlazada
     *         (su {@code ordenCompraId}).
     * @throws RecursoNoEncontradoException si la requisicion no es accesible, o el
     *         Proveedor/Material no existe (404).
     * @throws ReglaNegocioException si la requisicion no esta aprobada (422, mensaje
     *         "se requiere una Requisicion_Compra aprobada") o los datos de la Orden
     *         son invalidos (422).
     */
    @Transactional
    public RequisicionCompraDto generarOrdenCompra(UUID requisicionId, GenerarOrdenCompraCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Orden_Compra a generar son obligatorios.");
        }
        RequisicionCompra requisicion = cargar(requisicionId, actor);
        if (!requisicion.estaAprobada()) {
            throw new ReglaNegocioException("se requiere una Requisicion_Compra aprobada");
        }
        CrearOrdenCompraCommand ordenComando = new CrearOrdenCompraCommand(
                comando.proveedorId(), requisicion.getId(), comando.partidas());
        UUID ordenCompraId = servicioOrdenesCompra.crearDesdeRequisicion(
                requisicion.getId(), ordenComando, actor);
        requisicion.vincularOrdenCompra(ordenCompraId, actor);
        RequisicionCompra guardada = requisicionRepository.save(requisicion);
        auditar(actor, "generar_orden_compra", guardada.getId(),
                "generada orden_compra desde requisicion aprobada [orden_compra=" + ordenCompraId + "]",
                null, null);
        return RequisicionCompraDto.de(guardada);
    }

    /**
     * Consulta puntual de una Requisicion_Compra del tenant (Req 23.3).
     *
     * @param requisicionId identificador de la Requisicion_Compra.
     * @return el DTO de la Requisicion_Compra.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public RequisicionCompraDto consultarRequisicion(UUID requisicionId) {
        String actor = actorActual();
        return RequisicionCompraDto.de(cargar(requisicionId, actor));
    }

    /**
     * Listado paginado de Requisiciones de Compra del tenant con filtro opcional por
     * estado (Req 30.5). Un filtro nulo no restringe.
     *
     * @param estado   etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Requisiciones de Compra como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<RequisicionCompraDto> listarRequisiciones(String estado, Pageable pageable) {
        EstadoRequisicionCompra filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return requisicionRepository.buscarConFiltros(filtro, pageable)
                .map(RequisicionCompraDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private PartidaRequisicion construirPartida(CrearPartidaRequisicionCommand comando, String actor) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la partida son obligatorios.");
        }
        if (comando.materialId() == null) {
            throw new ReglaNegocioException(
                    "La partida de la Requisicion_Compra debe referir un Material.");
        }
        if (!materialExistente.existeMaterialActivo(comando.materialId())) {
            auditarAccesoCruzado(actor, "material", comando.materialId());
            throw new RecursoNoEncontradoException(
                    "No se encontro el Material indicado para la Requisicion_Compra.");
        }
        return PartidaRequisicion.crear(comando.materialId(), comando.cantidad(), actor);
    }

    private RequisicionCompra cargar(UUID requisicionId, String actor) {
        if (requisicionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Requisicion_Compra solicitada.");
        }
        return requisicionRepository.findById(requisicionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_REQUISICION, requisicionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Requisicion_Compra solicitada.");
                });
    }

    private EstadoRequisicionCompra interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        try {
            return EstadoRequisicionCompra.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Requisicion_Compra desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID requisicionId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_REQUISICION,
                detalle + " [id=" + requisicionId + "]", valorAnterior, valorNuevo));
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

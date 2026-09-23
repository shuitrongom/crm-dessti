package com.dessti.crm.compras.ordencompra.application;

import java.util.ArrayList;
import java.util.List;
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
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link OrdenCompra}
 * (Req 31). Replica el patron establecido por {@code ServicioCotizaciones}
 * (partidas + total half-up + maquina de estados).
 *
 * <h2>Operaciones (Req 31)</h2>
 * <ul>
 *   <li><strong>crearOrdenCompra (Req 31.1, 31.2, 31.3, 31.4):</strong> verifica
 *       que el Proveedor exista y este activo en el tenant (via
 *       {@link ProveedorExistentePort}; 404 si no) y que cada Material de las
 *       partidas exista y este activo (via {@link MaterialExistentePort}; 404 si
 *       no), exige entre 1 y 500 partidas, calcula los subtotales y el total
 *       half-up, fija estado inicial {@code abierta}, persiste y audita.</li>
 *   <li><strong>cambiarEstado (Req 31.5, 31.6, 31.8):</strong> aplica la maquina de
 *       estados pura (409 si la transicion es invalida) y audita el estado anterior
 *       y el nuevo.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no es
 *       accesible.</li>
 *   <li><strong>listar (Req 31.7):</strong> listado paginado (20/100) con filtros
 *       por Proveedor y estado.</li>
 *   <li><strong>crearDesdeRequisicion (Req 30.3):</strong> crea la Orden vinculada
 *       a la Requisicion_Compra de origen; la usa el submodulo de Requisiciones
 *       tras verificar que la requisicion este aprobada.</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 31.8)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} con el actor derivado del contexto; el cambio de estado
 * incluye el estado anterior y el nuevo (Req 31.8).</p>
 */
@Service
public class ServicioOrdenesCompra {

    /** Tipo de recurso de auditoria/RBAC de la Orden_Compra. */
    static final String RECURSO_ORDEN_COMPRA = "orden_compra";

    private final OrdenCompraRepository ordenCompraRepository;
    private final ProveedorExistentePort proveedorExistente;
    private final MaterialExistentePort materialExistente;
    private final AuditoriaPort auditoria;

    public ServicioOrdenesCompra(OrdenCompraRepository ordenCompraRepository,
                                 ProveedorExistentePort proveedorExistente,
                                 MaterialExistentePort materialExistente,
                                 AuditoriaPort auditoria) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.proveedorExistente = proveedorExistente;
        this.materialExistente = materialExistente;
        this.auditoria = auditoria;
    }

    /**
     * Da de alta una Orden_Compra asociada a un Proveedor existente con entre 1 y
     * 500 partidas, en estado inicial {@code abierta} (Req 31.1, 31.4). Calcula los
     * subtotales y el total half-up (Req 31.2, 31.3).
     *
     * @param comando datos de la Orden_Compra a crear.
     * @return el DTO de la Orden_Compra creada.
     * @throws RecursoNoEncontradoException si el Proveedor o algun Material no
     *         existe/activo en el tenant (404, Req 31.1, 23.3).
     * @throws ReglaNegocioException si faltan datos, el numero de partidas esta
     *         fuera de [1, 500], o una partida es invalida (422, Req 31.1, 31.2).
     */
    @Transactional
    public OrdenCompraDto crearOrdenCompra(CrearOrdenCompraCommand comando) {
        String actor = actorActual();
        OrdenCompra orden = construirOrden(comando, null, actor);
        OrdenCompra guardada = ordenCompraRepository.save(orden);
        auditar(actor, "crear", guardada.getId(),
                "creada orden_compra en estado '" + guardada.getEstado().valorBd()
                        + "' con " + guardada.getPartidas().size() + " partida(s), total "
                        + guardada.getTotal().toPlainString() + " [proveedor="
                        + guardada.getProveedorId() + "]", null, null);
        return OrdenCompraDto.de(guardada);
    }

    /**
     * Crea una Orden_Compra vinculada a una Requisicion_Compra de origen (Req 30.3)
     * y devuelve su identificador. La usa el submodulo de Requisiciones tras
     * verificar que la requisicion este {@code aprobada}; la precondicion de estado
     * (422 si no aprobada) la aplica el llamador. Aqui se validan el Proveedor y los
     * Materiales de las partidas (404 si no existen/activos) y los rangos de las
     * partidas (422), igual que en el alta directa.
     *
     * @param requisicionCompraId Requisicion_Compra de origen (aprobada, verificada
     *                            por el llamador); obligatoria.
     * @param comando             datos de la Orden_Compra (Proveedor y partidas).
     * @param actor               identificador de quien genera.
     * @return el identificador de la Orden_Compra creada.
     * @throws RecursoNoEncontradoException si el Proveedor o algun Material no
     *         existe/activo en el tenant (404).
     * @throws ReglaNegocioException si faltan datos o una partida es invalida (422).
     */
    @Transactional
    public UUID crearDesdeRequisicion(UUID requisicionCompraId, CrearOrdenCompraCommand comando,
                                      String actor) {
        String actorEfectivo = (actor == null || actor.isBlank()) ? actorActual() : actor;
        if (requisicionCompraId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Compra generada debe vincularse a una Requisicion_Compra existente.");
        }
        OrdenCompra orden = construirOrden(comando, requisicionCompraId, actorEfectivo);
        OrdenCompra guardada = ordenCompraRepository.save(orden);
        auditar(actorEfectivo, "generar", guardada.getId(),
                "generada orden_compra en estado '" + guardada.getEstado().valorBd()
                        + "' desde requisicion [requisicion=" + requisicionCompraId
                        + ", proveedor=" + guardada.getProveedorId() + "]",
                null, guardada.getEstado().valorBd());
        return guardada.getId();
    }

    /**
     * Cambia el estado de una Orden_Compra aplicando la maquina de estados pura
     * (Req 31.5, 31.6) y auditando el estado anterior y el nuevo (Req 31.8).
     *
     * @param ordenId     identificador de la Orden_Compra.
     * @param nuevoEstado etiqueta del estado destino; obligatoria (Req 31.5).
     * @return el DTO de la Orden_Compra con su nuevo estado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si el estado es nulo/desconocido (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 31.6).
     */
    @Transactional
    public OrdenCompraDto cambiarEstado(UUID ordenId, String nuevoEstado) {
        String actor = actorActual();
        EstadoOrdenCompra destino = interpretarEstado(nuevoEstado);
        OrdenCompra orden = cargar(ordenId, actor);
        EstadoOrdenCompra anterior = orden.getEstado();
        orden.cambiarEstado(destino, actor);
        OrdenCompra guardada = ordenCompraRepository.save(orden);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "cambio de estado '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
        return OrdenCompraDto.de(guardada);
    }

    /**
     * Consulta puntual de una Orden_Compra del tenant (Req 23.3).
     *
     * @param ordenId identificador de la Orden_Compra.
     * @return el DTO de la Orden_Compra.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public OrdenCompraDto consultarOrdenCompra(UUID ordenId) {
        String actor = actorActual();
        return OrdenCompraDto.de(cargar(ordenId, actor));
    }

    /**
     * Listado paginado de Ordenes de Compra del tenant con filtros opcionales por
     * Proveedor y por estado (Req 31.7). Un filtro nulo no restringe.
     *
     * @param proveedorId Proveedor a filtrar; {@code null} no filtra.
     * @param estado      etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param pageable    parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ordenes de Compra como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<OrdenCompraDto> listarOrdenesCompra(UUID proveedorId, String estado, Pageable pageable) {
        EstadoOrdenCompra filtro = (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return ordenCompraRepository.buscarConFiltros(proveedorId, filtro, pageable)
                .map(OrdenCompraDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Construye (sin persistir) una Orden_Compra validando el Proveedor y los
     * Materiales de las partidas y calculando el total (Req 31.1, 31.2, 31.3).
     */
    private OrdenCompra construirOrden(CrearOrdenCompraCommand comando, UUID requisicionCompraId,
                                       String actor) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Orden_Compra son obligatorios.");
        }
        if (comando.proveedorId() == null) {
            throw new ReglaNegocioException("La Orden_Compra debe asociarse a un Proveedor existente.");
        }
        if (!proveedorExistente.existeProveedorActivo(comando.proveedorId())) {
            auditarAccesoCruzado(actor, "proveedor", comando.proveedorId());
            throw new RecursoNoEncontradoException(
                    "No se encontro el Proveedor indicado para la Orden_Compra.");
        }
        List<CrearPartidaOrdenCompraCommand> comandosPartida =
                (comando.partidas() == null) ? List.of() : comando.partidas();
        if (comandosPartida.isEmpty()) {
            throw new ReglaNegocioException(
                    "La Orden_Compra debe tener al menos una Partida_Orden_Compra.");
        }
        List<PartidaOrdenCompra> partidas = new ArrayList<>();
        for (CrearPartidaOrdenCompraCommand pc : comandosPartida) {
            partidas.add(construirPartida(pc, actor));
        }
        return OrdenCompra.crear(comando.proveedorId(), requisicionCompraId, partidas, actor);
    }

    private PartidaOrdenCompra construirPartida(CrearPartidaOrdenCompraCommand comando, String actor) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la partida son obligatorios.");
        }
        if (comando.materialId() == null) {
            throw new ReglaNegocioException("La partida de la Orden_Compra debe referir un Material.");
        }
        if (!materialExistente.existeMaterialActivo(comando.materialId())) {
            auditarAccesoCruzado(actor, "material", comando.materialId());
            throw new RecursoNoEncontradoException(
                    "No se encontro el Material indicado para la Orden_Compra.");
        }
        return PartidaOrdenCompra.crear(
                comando.materialId(), comando.cantidad(), comando.precioUnitario(), actor);
    }

    private OrdenCompra cargar(UUID ordenId, String actor) {
        if (ordenId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Orden_Compra solicitada.");
        }
        return ordenCompraRepository.findById(ordenId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_ORDEN_COMPRA, ordenId);
                    throw new RecursoNoEncontradoException("No se encontro la Orden_Compra solicitada.");
                });
    }

    private EstadoOrdenCompra interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        try {
            return EstadoOrdenCompra.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Orden_Compra desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID ordenId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_ORDEN_COMPRA,
                detalle + " [id=" + ordenId + "]", valorAnterior, valorNuevo));
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

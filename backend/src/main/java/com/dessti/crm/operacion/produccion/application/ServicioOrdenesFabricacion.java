package com.dessti.crm.operacion.produccion.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.ConflictoUnicidadException;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterial;
import com.dessti.crm.operacion.inventario.application.ConsumoMaterialPort;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.OrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.adapter.out.persistence.PartidaOrdenFabricacionRepository;
import com.dessti.crm.operacion.produccion.application.CrearOrdenDirectaCommand.PartidaInicial;
import com.dessti.crm.operacion.produccion.domain.EstadoOrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.OrdenFabricacion;
import com.dessti.crm.operacion.produccion.domain.PartidaOrdenFabricacion;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las
 * {@link OrdenFabricacion} (Req 7). Replica el patron establecido por
 * {@code ServicioCotizaciones}/{@code ServicioPruebasDiseno}.
 *
 * <h2>Operaciones (Req 7, 15.5)</h2>
 * <ul>
 *   <li><strong>generar (Req 7.1–7.4, 15.5; Property 7):</strong> genera una
 *       Orden_Fabricacion a partir de una Cotizacion <em>si y solo si</em> se
 *       cumplen las TRES precondiciones (ver mas abajo); crea la OF en estado
 *       {@code pendiente} vinculada a la Cotizacion y a su Cliente, y audita.</li>
 *   <li><strong>cambiarEstado (Req 7.5, 7.6, 7.10):</strong> aplica la maquina de
 *       estados pura (409 si la transicion es invalida) y audita el estado
 *       anterior y el nuevo.</li>
 *   <li><strong>consultar (Req 23.3):</strong> 404 + auditoria del intento si no
 *       es accesible.</li>
 *   <li><strong>listar (Req 7.7, 7.9):</strong> listado paginado (20/100) con
 *       filtros por estado y por Cliente; sin coincidencias devuelve una pagina
 *       vacia con total 0.</li>
 * </ul>
 *
 * <h2>Precondiciones de generacion (Property 7, en orden)</h2>
 * <ol>
 *   <li>La Cotizacion existe en el tenant (via
 *       {@link CotizacionParaFabricacionPort}); si no, 404 y auditoria del acceso
 *       cruzado (Req 23.3).</li>
 *   <li>La Cotizacion esta en estado {@code aprobada} (Req 7.1, 7.2); si no,
 *       {@link ReglaNegocioException} (422) con el mensaje "se requiere una
 *       Cotizacion aprobada".</li>
 *   <li>La Cotizacion no tiene ya una Orden_Fabricacion vinculada (Req 7.3); si la
 *       tiene, {@link ConflictoUnicidadException} (409). Doble defensa: se
 *       pre-verifica con {@code existsByCotizacionId} y ademas se captura la
 *       violacion del indice unico {@code (tenant_id, cotizacion_id)} de V17.</li>
 *   <li>La Cotizacion tiene al menos una Prueba_Diseno {@code aprobada} (Req 15.5,
 *       via {@link PruebaDisenoAprobadaPort}); si no,
 *       {@link ReglaNegocioException} (422) con el mensaje "se requiere una
 *       Prueba_Diseno aprobada".</li>
 * </ol>
 * El orden coloca la unica precondicion de conflicto (409) entre las dos de regla
 * de negocio (422). La comprobacion de estado {@code aprobada} va primero porque
 * es la condicion natural de arranque del flujo; la de unicidad evita trabajo
 * innecesario si ya existe una OF; y la de Prueba_Diseno aprobada cierra el
 * gating de calidad (Req 15.5). En todos los casos de rechazo NO se crea ninguna
 * Orden_Fabricacion (Req 7.2, 7.3, 15.5).
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 7.10)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto;
 * el cambio de estado incluye el estado anterior y el nuevo (Req 7.10).</p>
 */
@Service
public class ServicioOrdenesFabricacion {

    /** Tipo de recurso de auditoria/RBAC de la Orden_Fabricacion. */
    static final String RECURSO_ORDEN_FABRICACION = "orden_fabricacion";

    /** Recurso de auditoria del acceso cruzado al Cliente (Req 1.5). */
    static final String RECURSO_CLIENTE = "cliente";

    /** Recurso de auditoria del acceso cruzado al Material (Req 5.3). */
    static final String RECURSO_MATERIAL = "material";

    private final OrdenFabricacionRepository ordenFabricacionRepository;
    private final PartidaOrdenFabricacionRepository partidaRepository;
    private final CotizacionParaFabricacionPort cotizacionParaFabricacion;
    private final PruebaDisenoAprobadaPort pruebaDisenoAprobada;
    private final ClienteExistentePort clienteExistente;
    private final MaterialAccesiblePort materialAccesible;
    private final ConsumoMaterialPort consumoMaterialPort;
    private final AuditoriaPort auditoria;

    public ServicioOrdenesFabricacion(OrdenFabricacionRepository ordenFabricacionRepository,
                                      PartidaOrdenFabricacionRepository partidaRepository,
                                      CotizacionParaFabricacionPort cotizacionParaFabricacion,
                                      PruebaDisenoAprobadaPort pruebaDisenoAprobada,
                                      ClienteExistentePort clienteExistente,
                                      MaterialAccesiblePort materialAccesible,
                                      ConsumoMaterialPort consumoMaterialPort,
                                      AuditoriaPort auditoria) {
        this.ordenFabricacionRepository = ordenFabricacionRepository;
        this.partidaRepository = partidaRepository;
        this.cotizacionParaFabricacion = cotizacionParaFabricacion;
        this.pruebaDisenoAprobada = pruebaDisenoAprobada;
        this.clienteExistente = clienteExistente;
        this.materialAccesible = materialAccesible;
        this.consumoMaterialPort = consumoMaterialPort;
        this.auditoria = auditoria;
    }

    /**
     * Genera una Orden_Fabricacion a partir de una Cotizacion, aplicando las tres
     * precondiciones de {@link CotizacionParaFabricacionPort} (Property 7). En caso
     * de exito crea la OF en estado {@code pendiente} vinculada a la Cotizacion y a
     * su Cliente, y devuelve su DTO con el identificador (Req 7.1, 7.4).
     *
     * @param cotizacionId identificador de la Cotizacion aprobada.
     * @return el DTO de la Orden_Fabricacion creada (estado {@code pendiente}).
     * @throws RecursoNoEncontradoException si la Cotizacion no existe en el tenant
     *         (404, Req 23.3).
     * @throws ReglaNegocioException si la Cotizacion no esta aprobada (422, Req 7.2)
     *         o no tiene una Prueba_Diseno aprobada (422, Req 15.5).
     * @throws ConflictoUnicidadException si la Cotizacion ya tiene una OF asociada
     *         (409, Req 7.3).
     */
    @Transactional
    public OrdenFabricacionDto generar(UUID cotizacionId) {
        String actor = actorActual();
        if (cotizacionId == null) {
            throw new ReglaNegocioException(
                    "La Orden_Fabricacion debe asociarse a una Cotizacion existente.");
        }

        // Precondicion 0: la Cotizacion existe en el tenant (Req 23.3).
        CotizacionParaFabricacion cotizacion = cotizacionParaFabricacion
                .buscarParaFabricacion(cotizacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, "cotizacion", cotizacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Cotizacion indicada para la Orden_Fabricacion.");
                });

        // Precondicion 1 (Req 7.1, 7.2): la Cotizacion esta 'aprobada'.
        if (!cotizacion.estaAprobada()) {
            throw new ReglaNegocioException("se requiere una Cotizacion aprobada");
        }

        // Precondicion 2 (Req 7.3): la Cotizacion no tiene ya una OF (pre-check).
        if (ordenFabricacionRepository.existsByCotizacionId(cotizacionId)) {
            throw new ConflictoUnicidadException(
                    "la Cotizacion ya tiene una Orden_Fabricacion asociada");
        }

        // Precondicion 3 (Req 15.5): la Cotizacion tiene una Prueba_Diseno aprobada.
        if (!pruebaDisenoAprobada.tieneAprobadaPorCotizacion(cotizacionId)) {
            throw new ReglaNegocioException("se requiere una Prueba_Diseno aprobada");
        }

        OrdenFabricacion orden =
                OrdenFabricacion.generar(cotizacionId, cotizacion.clienteId(), actor);
        OrdenFabricacion guardada;
        try {
            // saveAndFlush para materializar la violacion del indice unico
            // (tenant_id, cotizacion_id) dentro de este try (segunda capa de
            // defensa del Req 7.3 ante concurrencia).
            guardada = ordenFabricacionRepository.saveAndFlush(orden);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente contra uq_orden_fabricacion_cotizacion (V17):
            // otro hilo genero la OF para la misma Cotizacion entre el pre-check y
            // el guardado.
            throw new ConflictoUnicidadException(
                    "la Cotizacion ya tiene una Orden_Fabricacion asociada");
        }

        auditar(actor, "generar", guardada.getId(),
                "generada Orden_Fabricacion en estado '" + guardada.getEstado().valorBd()
                        + "' [cotizacion=" + cotizacionId + ", cliente="
                        + guardada.getClienteId() + "]",
                null, guardada.getEstado().valorBd());
        return OrdenFabricacionDto.de(guardada);
    }

    /**
     * Crea una Orden_Fabricacion por el <strong>origen generico</strong> (Req 1.1,
     * 1.2, §A1): asociada directamente a un Cliente, sin Cotizacion de origen ni las
     * tres precondiciones del flujo de anuncios. Se comporta de forma idéntica al
     * resto de la vida de la OF una vez creada (Req 1.7); la unica diferencia
     * observable es {@code cotizacionId = null} en el DTO.
     *
     * <p>Pasos (§A1):</p>
     * <ol>
     *   <li>{@code clienteId} nulo &rarr; 422 sin persistir (Req 1.4).</li>
     *   <li>Verifica el Cliente via {@link ClienteExistentePort}; si no existe/es
     *       inaccesible en el tenant &rarr; 404 + auditoria de acceso cruzado con
     *       recurso {@code cliente} (Req 1.5).</li>
     *   <li>Crea la OF con {@link OrdenFabricacion#crearDirecta(UUID, String)} en
     *       {@code pendiente}, la persiste y audita la creacion con recurso
     *       {@code orden_fabricacion} (Req 1.2).</li>
     * </ol>
     *
     * <p><strong>Partidas iniciales (§B1):</strong> las partidas del comando se
     * persisten en la <em>misma</em> transaccion (tras crear la OF), validando que
     * cada cantidad sea &gt; 0 (422, Req 5.2) y que cada Material sea accesible en el
     * tenant (404 + auditoria de acceso cruzado con recurso {@code material},
     * Req 5.3). Si un Material no es accesible, la excepcion revierte toda la
     * creacion (rollback de la OF, Req 1.5/5.3).</p>
     *
     * @param comando datos de creacion: Cliente y partidas iniciales (Req 1.1).
     * @return el DTO de la Orden_Fabricacion creada (estado {@code pendiente},
     *         {@code cotizacionId = null}).
     * @throws ReglaNegocioException        si {@code comando} o su {@code clienteId}
     *                                      es nulo, o una cantidad es &le; 0 (422,
     *                                      Req 1.4, 5.2).
     * @throws RecursoNoEncontradoException si el Cliente o un Material no existe/es
     *                                      inaccesible en el tenant (404, Req 1.5, 5.3).
     */
    @Transactional
    public OrdenFabricacionDto crearDirecta(CrearOrdenDirectaCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.clienteId() == null) {
            throw new ReglaNegocioException(
                    "La Orden_Fabricacion directa debe asociarse a un Cliente.");
        }
        UUID clienteId = comando.clienteId();

        // Verificacion de existencia del Cliente (Req 1.5): 404 + auditoria cruzada.
        if (!clienteExistente.existeEnTenant(clienteId)) {
            auditarAccesoCruzado(actor, RECURSO_CLIENTE, clienteId);
            throw new RecursoNoEncontradoException(
                    "No se encontro el Cliente indicado para la Orden_Fabricacion.");
        }

        OrdenFabricacion orden = OrdenFabricacion.crearDirecta(clienteId, actor);
        OrdenFabricacion guardada = ordenFabricacionRepository.save(orden);

        // Partidas iniciales (§B1, tarea 3.1): se persisten en la MISMA transaccion
        // validando cantidad > 0 (422) y accesibilidad de cada Material (404). Si un
        // Material no es accesible, la excepcion revierte toda la creacion (rollback
        // de la OF y de las partidas ya insertadas).
        persistirPartidas(guardada.getId(), comando.partidas(), actor);

        auditar(actor, "crear_directa", guardada.getId(),
                "creada Orden_Fabricacion directa en estado '"
                        + guardada.getEstado().valorBd() + "' [cliente="
                        + guardada.getClienteId() + "]",
                null, guardada.getEstado().valorBd());
        return OrdenFabricacionDto.de(guardada);
    }

    /**
     * Cambia el estado de una Orden_Fabricacion aplicando la maquina de estados
     * pura (Req 7.5, 7.6) y, en la transicion {@code pendiente -> en_produccion},
     * consumiendo atomicamente los Materiales de sus partidas (§B2, Decision D3,
     * Req 6). Todo ocurre dentro de la <strong>misma</strong> {@code @Transactional}
     * y se audita el estado anterior y el nuevo (Req 7.10).
     *
     * <h3>Orden de operaciones (§B2, Decision D3)</h3>
     * <ol>
     *   <li>Interpreta la etiqueta destino (422 si es desconocida, via
     *       {@link #interpretarEstado(String)}).</li>
     *   <li>Carga la OF (404 + auditoria si no es accesible, via {@link #cargar}).</li>
     *   <li><strong>Antes</strong> de consumir, comprueba con
     *       {@link EstadoOrdenFabricacion#puedeTransicionarA(EstadoOrdenFabricacion)}
     *       que la transicion es valida. El consumo se ejecuta <em>solo</em> cuando la
     *       transicion es valida y {@code destino == EN_PRODUCCION}; asi una transicion
     *       invalida (p. ej. {@code terminada -> en_produccion}) se rechaza con 409 en
     *       {@code orden.cambiarEstado(...)} <strong>sin</strong> haber consumido.</li>
     *   <li>Si {@code destino == EN_PRODUCCION} y la OF tiene partidas, mapea cada
     *       partida a un {@link ConsumoMaterial} e invoca (cableado, no reimplementacion)
     *       {@link ConsumoMaterialPort#consumirParaOrdenFabricacion(UUID, List)}. Un 422
     *       (existencias insuficientes) o 404 (Material inaccesible) revierte toda la
     *       transaccion: ni movimientos ni cambio de estado (Req 6.3, 6.5). Una OF sin
     *       partidas transita sin generar movimientos (Req 6.6).</li>
     *   <li>Aplica {@code orden.cambiarEstado(destino, actor)} (409 si la transicion es
     *       invalida, Req 7.6), persiste y audita, incluyendo el detalle del consumo
     *       cuando aplica (Req 6.4).</li>
     * </ol>
     *
     * @param ordenId     identificador de la Orden_Fabricacion.
     * @param nuevoEstado etiqueta del estado destino; obligatoria (Req 7.5).
     * @return el DTO de la Orden_Fabricacion con su nuevo estado.
     * @throws RecursoNoEncontradoException si la OF o un Material no son accesibles (404,
     *         Req 6.2).
     * @throws ReglaNegocioException si el estado es nulo/desconocido, o si las existencias
     *         son insuficientes para el consumo (422, Req 6.3).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 7.6).
     */
    @Transactional
    public OrdenFabricacionDto cambiarEstado(UUID ordenId, String nuevoEstado) {
        String actor = actorActual();
        EstadoOrdenFabricacion destino = interpretarEstado(nuevoEstado);
        OrdenFabricacion orden = cargar(ordenId, actor);
        EstadoOrdenFabricacion anterior = orden.getEstado();

        // Consumo atomico al arrancar la fabricacion (§B2, Decision D3): solo cuando la
        // transicion es valida y el destino es EN_PRODUCCION, de modo que una transicion
        // que luego seria 409 NO consuma. El resultado del cableado se incorpora al
        // detalle de auditoria (Req 6.4).
        int partidasConsumidas = 0;
        if (destino == EstadoOrdenFabricacion.EN_PRODUCCION
                && anterior.puedeTransicionarA(destino)) {
            List<PartidaOrdenFabricacion> partidas =
                    partidaRepository.findByOrdenFabricacionId(orden.getId());
            if (!partidas.isEmpty()) {
                List<ConsumoMaterial> consumos = new ArrayList<>(partidas.size());
                for (PartidaOrdenFabricacion partida : partidas) {
                    consumos.add(new ConsumoMaterial(partida.getMaterialId(), partida.getCantidad()));
                }
                // Cableado del mecanismo ya implementado en ServicioInventario: registra un
                // Movimiento_Inventario 'salida' por partida y descuenta existencias; 422 si
                // insuficiente, 404 si Material inaccesible. Al estar en la misma transaccion,
                // cualquiera de esos fallos revierte tambien el cambio de estado (Req 6.3, 6.5).
                consumoMaterialPort.consumirParaOrdenFabricacion(orden.getId(), consumos);
                partidasConsumidas = partidas.size();
            }
        }

        orden.cambiarEstado(destino, actor);
        OrdenFabricacion guardada = ordenFabricacionRepository.save(orden);
        String detalleConsumo = (partidasConsumidas > 0)
                ? " [consumo=" + partidasConsumidas + " partida(s)]"
                : "";
        auditar(actor, "cambiar_estado", guardada.getId(),
                "cambio de estado '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'"
                        + detalleConsumo,
                anterior.valorBd(), destino.valorBd());
        return OrdenFabricacionDto.de(guardada);
    }

    /**
     * Consulta puntual de una Orden_Fabricacion del tenant, con el detalle de sus
     * partidas de consumo (Req 5.5, 23.3). Lo consume {@code GET /ordenes-fabricacion/{id}}.
     *
     * @param ordenId identificador de la Orden_Fabricacion.
     * @return el DTO de detalle de la Orden_Fabricacion (OF + partidas).
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public OrdenFabricacionDetalleDto consultar(UUID ordenId) {
        String actor = actorActual();
        OrdenFabricacion orden = cargar(ordenId, actor);
        List<PartidaOrdenFabricacion> partidas =
                partidaRepository.findByOrdenFabricacionId(orden.getId());
        return OrdenFabricacionDetalleDto.de(orden, partidas);
    }

    /**
     * Reemplaza por completo las partidas de consumo de una Orden_Fabricacion (§B1,
     * Req 5.1–5.5): borra las existentes e inserta el conjunto indicado, dentro de
     * una unica transaccion. Solo se permite mientras la OF esta en
     * {@code pendiente} (Req 5, Decision D4).
     *
     * <p>Reglas (en orden):</p>
     * <ol>
     *   <li>OF no accesible en el tenant &rarr; 404 + auditoria (via {@link #cargar}).</li>
     *   <li>OF que <strong>no</strong> esta en {@code pendiente} &rarr; 422 con el
     *       mensaje "las partidas solo se editan con la Orden_Fabricacion en
     *       pendiente" (Req 5).</li>
     *   <li>Cualquier cantidad &le; 0 &rarr; 422 sin persistir (Req 5.2).</li>
     *   <li>Cualquier Material no accesible en el tenant &rarr; 404 + auditoria de
     *       acceso cruzado con recurso {@code material}, <em>antes</em> de persistir
     *       (Req 5.3).</li>
     * </ol>
     * <p>Al ejecutarse en una unica transaccion, cualquier rechazo revierte el borrado
     * de las partidas previas (atomicidad).</p>
     *
     * @param ordenId  identificador de la Orden_Fabricacion.
     * @param partidas nuevo conjunto de partidas (Material + cantidad); puede estar
     *                 vacio (deja la OF sin partidas). {@code null} se trata como vacio.
     * @return el DTO de detalle de la Orden_Fabricacion con sus partidas ya reemplazadas.
     * @throws RecursoNoEncontradoException si la OF o un Material no son accesibles (404).
     * @throws ReglaNegocioException        si la OF no esta en {@code pendiente} o una
     *                                      cantidad es &le; 0 (422).
     */
    @Transactional
    public OrdenFabricacionDetalleDto reemplazarPartidas(UUID ordenId,
                                                         List<PartidaInicial> partidas) {
        String actor = actorActual();
        OrdenFabricacion orden = cargar(ordenId, actor);
        if (orden.getEstado() != EstadoOrdenFabricacion.PENDIENTE) {
            throw new ReglaNegocioException(
                    "las partidas solo se editan con la Orden_Fabricacion en pendiente");
        }
        partidaRepository.deleteByOrdenFabricacionId(orden.getId());
        List<PartidaOrdenFabricacion> guardadas = persistirPartidas(orden.getId(), partidas, actor);
        auditar(actor, "reemplazar_partidas", orden.getId(),
                "reemplazadas partidas de la Orden_Fabricacion [total=" + guardadas.size() + "]",
                null, null);
        return OrdenFabricacionDetalleDto.de(orden, guardadas);
    }

    /**
     * Listado paginado de Ordenes de Fabricacion del tenant con filtros opcionales
     * por estado y por Cliente (Req 7.7, 7.9). Un filtro nulo no restringe; sin
     * coincidencias se devuelve una pagina vacia con total 0.
     *
     * @param estado    etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Ordenes de Fabricacion como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<OrdenFabricacionDto> listar(String estado, UUID clienteId, Pageable pageable) {
        EstadoOrdenFabricacion filtro =
                (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return ordenFabricacionRepository.buscarConFiltros(filtro, clienteId, pageable)
                .map(OrdenFabricacionDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Valida y persiste el conjunto de partidas de una Orden_Fabricacion (§B1). Por
     * cada partida: exige cantidad &gt; 0 (422, Req 5.2, validado por la factoria del
     * dominio) y verifica que el Material sea accesible en el tenant (404 + auditoria
     * de acceso cruzado con recurso {@code material}, <em>antes</em> de persistir esa
     * partida, Req 5.3). Al ejecutarse dentro de la transaccion del llamador, un fallo
     * revierte las partidas ya insertadas y, en {@code crearDirecta}, la propia OF.
     *
     * @param ordenId  Orden_Fabricacion de las partidas.
     * @param partidas partidas a persistir; {@code null}/vacia no persiste ninguna.
     * @param actor    actor de auditoria/marcas.
     * @return las partidas persistidas (lista vacia si no habia partidas).
     */
    private List<PartidaOrdenFabricacion> persistirPartidas(UUID ordenId,
                                                            List<PartidaInicial> partidas,
                                                            String actor) {
        List<PartidaOrdenFabricacion> guardadas = new ArrayList<>();
        if (partidas == null || partidas.isEmpty()) {
            return guardadas;
        }
        for (PartidaInicial partida : partidas) {
            UUID materialId = (partida == null) ? null : partida.materialId();
            // Cantidad > 0 (422) la valida la factoria de dominio; se invoca antes de
            // tocar la BD para no persistir nada ante una cantidad invalida (Req 5.2).
            PartidaOrdenFabricacion nueva = PartidaOrdenFabricacion.crear(
                    ordenId, materialId, (partida == null) ? null : partida.cantidad(), actor);
            // Material accesible en el tenant (404 + auditoria) ANTES de persistir (Req 5.3).
            if (!materialAccesible.esAccesible(materialId)) {
                auditarAccesoCruzado(actor, RECURSO_MATERIAL, materialId);
                throw new RecursoNoEncontradoException(
                        "No se encontro el Material indicado para la partida de la Orden_Fabricacion.");
            }
            guardadas.add(partidaRepository.save(nueva));
        }
        return guardadas;
    }

    private OrdenFabricacion cargar(UUID ordenId, String actor) {
        if (ordenId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Orden_Fabricacion solicitada.");
        }
        return ordenFabricacionRepository.findById(ordenId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_ORDEN_FABRICACION, ordenId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Orden_Fabricacion solicitada.");
                });
    }

    private EstadoOrdenFabricacion interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        try {
            return EstadoOrdenFabricacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Orden_Fabricacion desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID ordenId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_ORDEN_FABRICACION,
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

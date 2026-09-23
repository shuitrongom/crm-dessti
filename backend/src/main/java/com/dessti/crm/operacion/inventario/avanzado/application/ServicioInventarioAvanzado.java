package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.AlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.CapaCostoRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ConfigInventarioMaterialRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.ExistenciaAlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.LoteRepository;
import com.dessti.crm.operacion.inventario.avanzado.adapter.out.persistence.MovimientoAlmacenRepository;
import com.dessti.crm.operacion.inventario.avanzado.domain.Almacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.CapaCosto;
import com.dessti.crm.operacion.inventario.avanzado.domain.CapaCostoValor;
import com.dessti.crm.operacion.inventario.avanzado.domain.ConfigInventarioMaterial;
import com.dessti.crm.operacion.inventario.avanzado.domain.ExistenciaAlmacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.Lote;
import com.dessti.crm.operacion.inventario.avanzado.domain.MetodoCosteo;
import com.dessti.crm.operacion.inventario.avanzado.domain.MotorCosteo;
import com.dessti.crm.operacion.inventario.avanzado.domain.MovimientoAlmacen;
import com.dessti.crm.operacion.inventario.avanzado.domain.ResultadoEntrada;
import com.dessti.crm.operacion.inventario.avanzado.domain.ResultadoSalida;
import com.dessti.crm.operacion.inventario.avanzado.domain.TipoMovimientoAlmacen;
import com.dessti.crm.operacion.inventario.domain.Material;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el inventario AVANZADO por Almacen (Req 60). Replica
 * el patron establecido por {@code ServicioInventario} (Req 18): tenant via
 * {@link TenantContext}, auditoria via {@link AuditoriaPort}, actor via
 * {@link AutenticacionActual}, 404 con auditoria de acceso cruzado (Req 23.3).
 *
 * <h2>Operaciones de la tarea 23.1 (Req 60)</h2>
 * <ul>
 *   <li><strong>crearAlmacen / actualizarAlmacen / consultarAlmacen / listarAlmacenes:</strong>
 *       CRUD de Almacenes con listado paginado (20/100) y filtros por nombre y estado.</li>
 *   <li><strong>configurarInventarioMaterial (upsert):</strong> crea o actualiza la
 *       configuracion de inventario por Material (metodo de costeo, stock maximo, control
 *       de lote y parametros del punto de reorden), validando que el Material exista.</li>
 *   <li><strong>consultarKardex (SOLO LECTURA):</strong> lista el Kardex cronologico de un
 *       Material en un Almacen con rango de fechas opcional; NO modifica datos.</li>
 *   <li><strong>listarExistencias:</strong> lista los saldos por Almacen/Material.</li>
 * </ul>
 *
 * <h2>Alcance (23.1 vs 23.2)</h2>
 * <p>Este servicio NO registra entradas/salidas ni transferencias con costeo: esa logica
 * (motor promedio/PEPS, lotes y transferencias) la implementa la tarea 23.2. El helper
 * {@link #evaluarNotificaciones(ConfigInventarioMaterial, UUID, BigDecimal, String)} se
 * define aqui para reutilizarse desde el registro de movimientos de la tarea 23.2: evalua
 * un saldo frente a la configuracion del Material y emite las notificaciones de stock
 * minimo/maximo y reabastecimiento (Req 60).</p>
 */
@Service
public class ServicioInventarioAvanzado {

    /** Tipo de recurso de auditoria/RBAC del Almacen. */
    static final String RECURSO_ALMACEN = "almacen";

    /** Tipo de recurso de auditoria/RBAC del Material (configuracion de inventario). */
    static final String RECURSO_MATERIAL = "material";

    /** Tipo de recurso de auditoria/RBAC de un movimiento de inventario por Almacen. */
    static final String RECURSO_MOVIMIENTO = "movimiento_inventario";

    /** Tipo de recurso de auditoria/RBAC de un Lote. */
    static final String RECURSO_LOTE = "lote";

    private final AlmacenRepository almacenRepository;
    private final ConfigInventarioMaterialRepository configRepository;
    private final ExistenciaAlmacenRepository existenciaRepository;
    private final MovimientoAlmacenRepository movimientoRepository;
    private final CapaCostoRepository capaCostoRepository;
    private final LoteRepository loteRepository;
    private final MaterialRepository materialRepository;
    private final NotificadorInventarioAvanzadoPort notificador;
    private final AuditoriaPort auditoria;

    public ServicioInventarioAvanzado(AlmacenRepository almacenRepository,
                                      ConfigInventarioMaterialRepository configRepository,
                                      ExistenciaAlmacenRepository existenciaRepository,
                                      MovimientoAlmacenRepository movimientoRepository,
                                      CapaCostoRepository capaCostoRepository,
                                      LoteRepository loteRepository,
                                      MaterialRepository materialRepository,
                                      NotificadorInventarioAvanzadoPort notificador,
                                      AuditoriaPort auditoria) {
        this.almacenRepository = almacenRepository;
        this.configRepository = configRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.capaCostoRepository = capaCostoRepository;
        this.loteRepository = loteRepository;
        this.materialRepository = materialRepository;
        this.notificador = notificador;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------
    // Almacenes (Req 60)
    // ------------------------------------------------------------------

    /**
     * Da de alta un Almacen (Req 60) y audita.
     *
     * @param comando datos del Almacen (nombre y tipo).
     * @return el DTO del Almacen creado.
     * @throws ReglaNegocioException si el nombre o el tipo son invalidos (422).
     */
    @Transactional
    public AlmacenDto crearAlmacen(CrearAlmacenCommand comando) {
        String actor = actorActual();
        Almacen almacen = Almacen.crear(comando.nombre(), comando.tipo(), actor);
        Almacen guardado = almacenRepository.save(almacen);
        auditar(actor, "crear", RECURSO_ALMACEN, guardado.getId(),
                "alta de Almacen '" + guardado.getNombre() + "' [tipo=" + guardado.getTipo() + "]",
                null, "activo");
        return AlmacenDto.de(guardado);
    }

    /**
     * Edita (renombra/reclasifica) un Almacen (Req 60) y audita.
     *
     * @param almacenId identificador del Almacen.
     * @param comando   nuevos nombre y tipo.
     * @return el DTO del Almacen actualizado.
     * @throws RecursoNoEncontradoException si el Almacen no es accesible (404, Req 23.3).
     * @throws ReglaNegocioException si el nombre o el tipo son invalidos (422).
     */
    @Transactional
    public AlmacenDto actualizarAlmacen(UUID almacenId, ActualizarAlmacenCommand comando) {
        String actor = actorActual();
        Almacen almacen = cargarAlmacen(almacenId, actor);
        String nombreAnterior = almacen.getNombre();
        String tipoAnterior = almacen.getTipo();
        almacen.actualizar(comando.nombre(), comando.tipo(), actor);
        Almacen guardado = almacenRepository.save(almacen);
        auditar(actor, "actualizar", RECURSO_ALMACEN, guardado.getId(),
                "edicion de Almacen '" + guardado.getNombre() + "' [tipo=" + guardado.getTipo() + "]",
                nombreAnterior + "/" + tipoAnterior,
                guardado.getNombre() + "/" + guardado.getTipo());
        return AlmacenDto.de(guardado);
    }

    /**
     * Consulta puntual de un Almacen del tenant (Req 23.3).
     *
     * @param almacenId identificador del Almacen.
     * @return el DTO del Almacen.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public AlmacenDto consultarAlmacen(UUID almacenId) {
        String actor = actorActual();
        return AlmacenDto.de(cargarAlmacen(almacenId, actor));
    }

    /**
     * Listado paginado de Almacenes del tenant con filtros opcionales por nombre y por
     * estado activo (Req 60). Un filtro nulo no restringe.
     *
     * @param nombre   fragmento del nombre a filtrar; {@code null}/blanco no filtra.
     * @param activo   estado activo a filtrar; {@code null} no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Almacenes como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<AlmacenDto> listarAlmacenes(String nombre, Boolean activo, Pageable pageable) {
        String filtroNombre = (nombre == null || nombre.isBlank()) ? null : nombre.trim();
        return almacenRepository.buscarConFiltros(filtroNombre, activo, pageable)
                .map(AlmacenDto::de);
    }

    // ------------------------------------------------------------------
    // Configuracion de inventario por Material (Req 60)
    // ------------------------------------------------------------------

    /**
     * Configura (upsert) el inventario avanzado de un Material (Req 60): si el Material aun
     * no tiene configuracion la crea con los valores dados; si ya existe, la actualiza.
     * Valida que el Material exista (activo) y audita.
     *
     * @param materialId identificador del Material.
     * @param comando    parametros de configuracion.
     * @return el DTO de la configuracion resultante (incluye el punto de reorden derivado).
     * @throws RecursoNoEncontradoException si el Material no es accesible (404, Req 23.3).
     * @throws ReglaNegocioException si algun parametro es invalido (422).
     */
    @Transactional
    public ConfigInventarioMaterialDto configurarInventarioMaterial(
            UUID materialId, ConfigurarInventarioMaterialCommand comando) {
        String actor = actorActual();
        cargarMaterial(materialId, actor);
        MetodoCosteo metodo = interpretarMetodo(comando.metodoCosteo());

        ConfigInventarioMaterial config = configRepository.findByMaterialId(materialId)
                .orElseGet(() -> ConfigInventarioMaterial.predeterminada(materialId, actor));
        config.actualizar(metodo, comando.stockMaximo(), comando.controlLote(),
                comando.consumoPromedio(), comando.tiempoEntregaDias(), comando.stockSeguridad(),
                actor);
        ConfigInventarioMaterial guardada = configRepository.save(config);

        auditar(actor, "actualizar", RECURSO_MATERIAL, materialId,
                "configuracion de inventario del Material " + materialId
                        + " [metodo=" + guardada.getMetodoCosteo().valorBd()
                        + ", stockMaximo=" + guardada.getStockMaximo()
                        + ", controlLote=" + guardada.isControlLote()
                        + ", puntoReorden=" + guardada.puntoReorden() + "]",
                null, "metodo=" + guardada.getMetodoCosteo().valorBd());
        return ConfigInventarioMaterialDto.de(guardada);
    }

    // ------------------------------------------------------------------
    // Kardex por Almacen (SOLO LECTURA, Req 60)
    // ------------------------------------------------------------------

    /**
     * Lista el Kardex cronologico (por fecha ascendente) de un Material en un Almacen, con
     * rango de fechas opcional (Req 60). Operacion de SOLO LECTURA: no modifica datos.
     * Verifica que el Almacen y el Material sean accesibles (404 en caso contrario).
     *
     * @param almacenId  Almacen cuyo Kardex se consulta.
     * @param materialId Material cuyo Kardex se consulta.
     * @param desde      instante minimo (inclusive); {@code null} no filtra.
     * @param hasta      instante maximo (inclusive); {@code null} no filtra.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de movimientos del Kardex como DTOs.
     * @throws RecursoNoEncontradoException si el Almacen o el Material no son accesibles (404).
     */
    @Transactional(readOnly = true)
    public Page<MovimientoAlmacenDto> consultarKardex(UUID almacenId, UUID materialId,
                                                      Instant desde, Instant hasta,
                                                      Pageable pageable) {
        String actor = actorActual();
        cargarAlmacen(almacenId, actor);
        cargarMaterial(materialId, actor);
        return movimientoRepository.buscarKardex(almacenId, materialId, desde, hasta, pageable)
                .map(MovimientoAlmacenDto::de);
    }

    // ------------------------------------------------------------------
    // Existencias por Almacen (Req 60)
    // ------------------------------------------------------------------

    /**
     * Listado paginado de saldos de existencias por Almacen y/o Material (Req 60). Cada
     * filtro nulo no restringe.
     *
     * @param almacenId  Almacen a filtrar; {@code null} no filtra.
     * @param materialId Material a filtrar; {@code null} no filtra.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de saldos como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ExistenciaAlmacenDto> listarExistencias(UUID almacenId, UUID materialId,
                                                        Pageable pageable) {
        return existenciaRepository.buscarConFiltros(almacenId, materialId, pageable)
                .map(ExistenciaAlmacenDto::de);
    }

    // ------------------------------------------------------------------
    // Registro de movimientos con costeo (Req 60, tarea 23.2)
    // ------------------------------------------------------------------

    /**
     * Registra una ENTRADA de inventario en un Almacen aplicando el costeo configurado del
     * Material (promedio o PEPS) y actualizando el saldo perpetuo (Req 60.5, 60.10, 60.11).
     * Si el Material controla lotes y se informa un codigo, hace upsert del Lote y lo asocia
     * al movimiento y a la capa de costo (Req 60.4). Agrega la fila de Kardex, audita y evalua
     * las notificaciones de stock (min/max/reabastecimiento).
     *
     * @param comando datos del movimiento (Almacen, Material, lote opcional, cantidad, costo).
     * @return el DTO de la fila de Kardex registrada.
     * @throws RecursoNoEncontradoException si el Almacen o el Material no son accesibles (404).
     * @throws ReglaNegocioException si la cantidad/costo son invalidos (422).
     */
    @Transactional
    public MovimientoAlmacenDto registrarEntrada(RegistrarMovimientoAlmacenCommand comando) {
        String actor = actorActual();
        return aplicarEntradaInterna(comando, TipoMovimientoAlmacen.ENTRADA, null, actor);
    }

    /**
     * Registra una SALIDA de inventario de un Almacen determinando el costo segun el metodo
     * configurado del Material (promedio vigente o consumo de capas PEPS mas antiguas) y
     * actualizando el saldo perpetuo (Req 60.5, 60.10, 60.11). Rechaza (422 "existencias
     * insuficientes") si la cantidad supera el saldo. Agrega la fila de Kardex, audita y
     * evalua las notificaciones de stock.
     *
     * @param comando datos del movimiento (Almacen, Material, lote opcional, cantidad).
     * @return el DTO de la fila de Kardex registrada.
     * @throws RecursoNoEncontradoException si el Almacen o el Material no son accesibles (404).
     * @throws ReglaNegocioException si la cantidad es invalida o excede el saldo (422).
     */
    @Transactional
    public MovimientoAlmacenDto registrarSalida(RegistrarMovimientoAlmacenCommand comando) {
        String actor = actorActual();
        return aplicarSalidaInterna(comando, TipoMovimientoAlmacen.SALIDA, null, actor);
    }

    /**
     * Transfiere una cantidad de un Material entre dos Almacenes dentro de UNA transaccion
     * (Req 60.13): registra una SALIDA en el origen y una ENTRADA por la misma cantidad en el
     * destino, agrupadas por un {@code transferencia_id} y CONSERVANDO EL COSTO (la entrada en
     * destino usa el costo unitario calculado por la salida en origen). Si algo falla (por
     * ejemplo, existencias insuficientes en origen, 422), se revierten ambas patas.
     *
     * @param comando datos de la transferencia (origen, destino, Material, cantidad).
     * @return el DTO de la pata de ENTRADA en el destino (resultado principal).
     * @throws RecursoNoEncontradoException si algun Almacen o el Material no son accesibles (404).
     * @throws ReglaNegocioException si origen == destino, cantidad invalida o saldo insuficiente (422).
     */
    @Transactional
    public MovimientoAlmacenDto transferir(TransferirCommand comando) {
        String actor = actorActual();
        if (comando.almacenOrigenId() != null
                && comando.almacenOrigenId().equals(comando.almacenDestinoId())) {
            throw new ReglaNegocioException(
                    "La transferencia debe realizarse entre dos Almacenes distintos.");
        }
        UUID transferenciaId = UUID.randomUUID();

        // Pata de SALIDA en el origen: determina el costo unitario a conservar (Req 60.13).
        RegistrarMovimientoAlmacenCommand salidaCmd = new RegistrarMovimientoAlmacenCommand(
                comando.almacenOrigenId(), comando.materialId(), null,
                comando.cantidad(), null, comando.motivo());
        MovimientoAlmacenDto salida = aplicarSalidaInterna(
                salidaCmd, TipoMovimientoAlmacen.TRANSFERENCIA_SALIDA, transferenciaId, actor);

        // Pata de ENTRADA en el destino: reutiliza el costo unitario de la salida (Req 60.13).
        RegistrarMovimientoAlmacenCommand entradaCmd = new RegistrarMovimientoAlmacenCommand(
                comando.almacenDestinoId(), comando.materialId(), null,
                comando.cantidad(), salida.costoUnitario(), comando.motivo());
        return aplicarEntradaInterna(
                entradaCmd, TipoMovimientoAlmacen.TRANSFERENCIA_ENTRADA, transferenciaId, actor);
    }

    // ------------------------------------------------------------------
    // Lotes (Req 60, tarea 23.2)
    // ------------------------------------------------------------------

    /**
     * Da de alta un Lote de un Material (Req 60) y audita. El codigo es unico por Material
     * dentro del tenant; un codigo duplicado se rechaza (422).
     *
     * @param comando datos del Lote (Material, codigo, caducidad opcional).
     * @return el DTO del Lote creado.
     * @throws RecursoNoEncontradoException si el Material no es accesible (404).
     * @throws ReglaNegocioException si el codigo es invalido o ya existe (422).
     */
    @Transactional
    public LoteDto crearLote(CrearLoteCommand comando) {
        String actor = actorActual();
        cargarMaterial(comando.materialId(), actor);
        loteRepository.findByMaterialIdAndCodigo(comando.materialId(), normalizar(comando.codigo()))
                .ifPresent(existente -> {
                    throw new ReglaNegocioException(
                            "Ya existe un Lote con ese codigo para el Material.");
                });
        Lote lote = Lote.crear(comando.materialId(), comando.codigo(), comando.fechaCaducidad(), actor);
        Lote guardado = loteRepository.save(lote);
        auditar(actor, "crear", RECURSO_LOTE, guardado.getId(),
                "alta de Lote '" + guardado.getCodigo() + "' del Material " + comando.materialId(),
                null, guardado.getCodigo());
        return LoteDto.de(guardado);
    }

    /**
     * Lista los Lotes de un Material del tenant vigente de forma paginada (Req 60). Verifica
     * que el Material sea accesible (404 en caso contrario).
     *
     * @param materialId Material cuyos Lotes se listan.
     * @param pageable   parametros de paginacion ya acotados (20/100).
     * @return la pagina de Lotes como DTOs.
     * @throws RecursoNoEncontradoException si el Material no es accesible (404).
     */
    @Transactional(readOnly = true)
    public Page<LoteDto> listarLotes(UUID materialId, Pageable pageable) {
        String actor = actorActual();
        cargarMaterial(materialId, actor);
        List<Lote> lotes = loteRepository.findByMaterialId(materialId);
        return paginar(lotes, pageable).map(LoteDto::de);
    }

    // ------------------------------------------------------------------
    // Nucleo del registro de movimientos (reutilizado por transferencias)
    // ------------------------------------------------------------------

    /**
     * Nucleo de una ENTRADA: carga la configuracion/saldo/capas, aplica el {@link MotorCosteo},
     * persiste el saldo, la nueva capa PEPS y la fila de Kardex, audita y notifica. El
     * {@code tipo} permite reutilizarlo tanto para {@link TipoMovimientoAlmacen#ENTRADA} como
     * para {@link TipoMovimientoAlmacen#TRANSFERENCIA_ENTRADA} (con {@code transferenciaId}).
     */
    private MovimientoAlmacenDto aplicarEntradaInterna(RegistrarMovimientoAlmacenCommand comando,
                                                       TipoMovimientoAlmacen tipo,
                                                       UUID transferenciaId, String actor) {
        cargarAlmacen(comando.almacenId(), actor);
        Material material = cargarMaterial(comando.materialId(), actor);
        ConfigInventarioMaterial config = cargarOConfigPredeterminada(comando.materialId(), actor);
        ExistenciaAlmacen existencia = cargarOCrearExistencia(comando.almacenId(), comando.materialId(), actor);
        UUID loteId = resolverLote(config, comando.materialId(), comando.loteCodigo(), actor);

        List<CapaCostoValor> capasActuales = capasValor(comando.almacenId(), comando.materialId());
        ResultadoEntrada resultado = MotorCosteo.aplicarEntrada(
                config.getMetodoCosteo(), existencia.getCantidad(), existencia.getCostoPromedio(),
                capasActuales, comando.cantidad(), comando.costoUnitario());

        existencia.aplicarSaldo(resultado.nuevoSaldoCantidad(), resultado.nuevoCostoPromedio(), actor);
        existenciaRepository.save(existencia);

        if (config.getMetodoCosteo() == MetodoCosteo.PEPS) {
            long secuencia = siguienteSecuencia(comando.almacenId(), comando.materialId());
            CapaCosto capa = CapaCosto.crear(comando.almacenId(), comando.materialId(), loteId,
                    comando.cantidad(), comando.costoUnitario(), secuencia, actor);
            capaCostoRepository.save(capa);
        }

        MovimientoAlmacen movimiento = MovimientoAlmacen.registrar(
                comando.almacenId(), comando.materialId(), loteId, tipo, comando.cantidad(),
                resultado.costoUnitarioMovimiento(), resultado.costoTotalMovimiento(),
                resultado.nuevoSaldoCantidad(), saldoCostoTotal(resultado.nuevoSaldoCantidad(),
                        resultado.nuevoCostoPromedio()),
                transferenciaId, comando.motivo(), actor);
        MovimientoAlmacen guardado = movimientoRepository.save(movimiento);

        auditarMovimiento(actor, tipo, guardado, transferenciaId);
        evaluarNotificaciones(config, comando.almacenId(), resultado.nuevoSaldoCantidad(),
                material.getNombre());
        return MovimientoAlmacenDto.de(guardado);
    }

    /**
     * Nucleo de una SALIDA: carga la configuracion/saldo/capas, aplica el {@link MotorCosteo}
     * (422 "existencias insuficientes" si excede el saldo), persiste el saldo, reconcilia las
     * capas PEPS consumidas, agrega la fila de Kardex, audita y notifica. El {@code tipo}
     * permite reutilizarlo para {@link TipoMovimientoAlmacen#SALIDA} y
     * {@link TipoMovimientoAlmacen#TRANSFERENCIA_SALIDA} (con {@code transferenciaId}).
     */
    private MovimientoAlmacenDto aplicarSalidaInterna(RegistrarMovimientoAlmacenCommand comando,
                                                      TipoMovimientoAlmacen tipo,
                                                      UUID transferenciaId, String actor) {
        cargarAlmacen(comando.almacenId(), actor);
        Material material = cargarMaterial(comando.materialId(), actor);
        ConfigInventarioMaterial config = cargarOConfigPredeterminada(comando.materialId(), actor);
        ExistenciaAlmacen existencia = cargarExistenciaParaSalida(comando.almacenId(), comando.materialId());
        UUID loteId = resolverLote(config, comando.materialId(), comando.loteCodigo(), actor);

        List<CapaCosto> capas = capaCostoRepository
                .findByAlmacenIdAndMaterialIdOrderBySecuenciaAsc(comando.almacenId(), comando.materialId());
        List<CapaCostoValor> capasActuales = aValor(capas);
        ResultadoSalida resultado = MotorCosteo.aplicarSalida(
                config.getMetodoCosteo(), existencia.getCantidad(), existencia.getCostoPromedio(),
                capasActuales, comando.cantidad());

        existencia.aplicarSaldo(resultado.nuevoSaldoCantidad(), resultado.nuevoCostoPromedio(), actor);
        existenciaRepository.save(existencia);

        if (config.getMetodoCosteo() == MetodoCosteo.PEPS) {
            consumirCapasPeps(capas, comando.cantidad(), actor);
        }

        MovimientoAlmacen movimiento = MovimientoAlmacen.registrar(
                comando.almacenId(), comando.materialId(), loteId, tipo, comando.cantidad(),
                resultado.costoUnitarioMovimiento(), resultado.costoTotalMovimiento(),
                resultado.nuevoSaldoCantidad(), saldoCostoTotal(resultado.nuevoSaldoCantidad(),
                        resultado.nuevoCostoPromedio()),
                transferenciaId, comando.motivo(), actor);
        MovimientoAlmacen guardado = movimientoRepository.save(movimiento);

        auditarMovimiento(actor, tipo, guardado, transferenciaId);
        evaluarNotificaciones(config, comando.almacenId(), resultado.nuevoSaldoCantidad(),
                material.getNombre());
        return MovimientoAlmacenDto.de(guardado);
    }

    /**
     * Reconcilia las filas JPA {@link CapaCosto} con el consumo PEPS que calcula el
     * {@link MotorCosteo} (Req 60.11): recorre las capas en orden de {@code secuencia}
     * ascendente y, consumiendo la misma cantidad en el mismo orden, ELIMINA las capas
     * totalmente consumidas y REDUCE la primera capa parcial. Mantiene la BD sincronizada con
     * la lista de capas restantes que devuelve el motor, conservando el engine puro.
     *
     * @param capas          capas actuales ordenadas por secuencia ascendente (FIFO).
     * @param cantidadSalida cantidad a consumir; el motor ya garantizo que hay saldo suficiente.
     * @param actor          identificador de quien consume, para {@code updated_by}.
     */
    private void consumirCapasPeps(List<CapaCosto> capas, BigDecimal cantidadSalida, String actor) {
        BigDecimal porConsumir = cantidadSalida;
        for (CapaCosto capa : capas) {
            if (porConsumir.signum() <= 0) {
                break;
            }
            BigDecimal disponible = capa.getCantidadRestante();
            if (disponible.compareTo(porConsumir) <= 0) {
                // Capa totalmente consumida: se elimina.
                porConsumir = porConsumir.subtract(disponible);
                capaCostoRepository.delete(capa);
            } else {
                // Capa parcialmente consumida: se reduce su remanente.
                capa.reducir(disponible.subtract(porConsumir), actor);
                capaCostoRepository.save(capa);
                porConsumir = BigDecimal.ZERO;
            }
        }
    }

    // ------------------------------------------------------------------
    // Evaluacion de notificaciones (reutilizable por la tarea 23.2)
    // ------------------------------------------------------------------

    /**
     * Evalua un saldo de existencias frente a la configuracion de inventario del Material y
     * emite las notificaciones que correspondan (Req 60): stock MINIMO y sugerencia de
     * REABASTECIMIENTO cuando la cantidad cae en o por debajo del punto de reorden, y stock
     * MAXIMO cuando supera el stock maximo configurado. No muta estado; solo notifica.
     *
     * <p>Se define en la tarea 23.1 para reutilizarse desde el registro de movimientos de la
     * tarea 23.2 (tras aplicar el costeo y obtener el nuevo saldo). Visibilidad de paquete
     * para acotar su uso al submodulo de inventario avanzado.</p>
     *
     * @param config        configuracion de inventario del Material (puede ser {@code null}
     *                      si el Material no tiene configuracion avanzada: no notifica).
     * @param almacenId     Almacen del saldo evaluado.
     * @param saldoCantidad cantidad en existencia a evaluar.
     * @param nombreMaterial nombre del Material para el mensaje; puede ser {@code null}.
     */
    void evaluarNotificaciones(ConfigInventarioMaterial config, UUID almacenId,
                               BigDecimal saldoCantidad, String nombreMaterial) {
        if (config == null || saldoCantidad == null) {
            return;
        }
        UUID tenantId = TenantContext.require();
        UUID materialId = config.getMaterialId();
        BigDecimal puntoReorden = config.puntoReorden();

        if (saldoCantidad.compareTo(puntoReorden) <= 0) {
            notificador.notificarStockMinimo(new NotificacionStockMinimo(
                    tenantId, almacenId, materialId, nombreMaterial, saldoCantidad, puntoReorden));
            notificador.notificarReabastecimiento(new NotificacionReabastecimiento(
                    tenantId, almacenId, materialId, nombreMaterial, saldoCantidad, puntoReorden));
        }

        BigDecimal stockMaximo = config.getStockMaximo();
        if (stockMaximo != null && saldoCantidad.compareTo(stockMaximo) > 0) {
            notificador.notificarStockMaximo(new NotificacionStockMaximo(
                    tenantId, almacenId, materialId, nombreMaterial, saldoCantidad, stockMaximo));
        }
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private Almacen cargarAlmacen(UUID almacenId, String actor) {
        if (almacenId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Almacen solicitado.");
        }
        return almacenRepository.findById(almacenId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_ALMACEN, almacenId);
                    throw new RecursoNoEncontradoException("No se encontro el Almacen solicitado.");
                });
    }

    private Material cargarMaterial(UUID materialId, String actor) {
        if (materialId == null) {
            throw new RecursoNoEncontradoException("No se encontro el Material solicitado.");
        }
        return materialRepository.findByIdAndActivoTrue(materialId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_MATERIAL, materialId);
                    throw new RecursoNoEncontradoException("No se encontro el Material solicitado.");
                });
    }

    /**
     * Carga la configuracion de inventario del Material o, si aun no existe, devuelve una
     * configuracion PREDETERMINADA en memoria (metodo promedio, sin control de lote, ceros)
     * sin persistirla. Asi el registro de movimientos funciona aunque no se haya configurado
     * el Material explicitamente (Req 60), usando el metodo de costeo por defecto.
     */
    private ConfigInventarioMaterial cargarOConfigPredeterminada(UUID materialId, String actor) {
        return configRepository.findByMaterialId(materialId)
                .orElseGet(() -> ConfigInventarioMaterial.predeterminada(materialId, actor));
    }

    /**
     * Carga el saldo de existencias del (Almacen, Material) o crea uno inicial (cantidad 0,
     * costo 0) si aun no existe (Req 60). El saldo recien creado se persiste al aplicar el
     * movimiento en la misma transaccion.
     */
    private ExistenciaAlmacen cargarOCrearExistencia(UUID almacenId, UUID materialId, String actor) {
        return existenciaRepository.findByAlmacenIdAndMaterialId(almacenId, materialId)
                .orElseGet(() -> ExistenciaAlmacen.paraAlmacenMaterial(almacenId, materialId, actor));
    }

    /**
     * Carga el saldo de existencias del (Almacen, Material) para una SALIDA. Si no existe
     * saldo, no hay nada que consumir: se rechaza con 422 "existencias insuficientes",
     * coherente con la no negatividad del inventario (Req 60.10, Property 31).
     */
    private ExistenciaAlmacen cargarExistenciaParaSalida(UUID almacenId, UUID materialId) {
        return existenciaRepository.findByAlmacenIdAndMaterialId(almacenId, materialId)
                .orElseThrow(() -> new ReglaNegocioException("existencias insuficientes"));
    }

    /**
     * Resuelve (upsert) el Lote a asociar a un movimiento cuando el Material tiene el control
     * de lote habilitado y se informa un codigo (Req 60.4). Si el control de lote esta
     * deshabilitado o no se informa codigo, devuelve {@code null} (movimiento sin lote). Si el
     * Lote no existe, lo crea; si existe, lo reutiliza. Devuelve el identificador del Lote.
     */
    private UUID resolverLote(ConfigInventarioMaterial config, UUID materialId,
                              String loteCodigo, String actor) {
        if (config == null || !config.isControlLote()
                || loteCodigo == null || loteCodigo.isBlank()) {
            return null;
        }
        String codigo = normalizar(loteCodigo);
        Lote lote = loteRepository.findByMaterialIdAndCodigo(materialId, codigo)
                .orElseGet(() -> {
                    Lote nuevo = Lote.crear(materialId, codigo, null, actor);
                    return loteRepository.save(nuevo);
                });
        return lote.getId();
    }

    /**
     * Devuelve las capas de costo PEPS actuales del (Almacen, Material) como value objects
     * PUROS {@link CapaCostoValor} ordenadas por {@code secuencia} ascendente (FIFO), para
     * alimentar el {@link MotorCosteo} sin acoplarlo a JPA (Req 60.11, Property 33).
     */
    private List<CapaCostoValor> capasValor(UUID almacenId, UUID materialId) {
        return aValor(capaCostoRepository
                .findByAlmacenIdAndMaterialIdOrderBySecuenciaAsc(almacenId, materialId));
    }

    /**
     * Proyecta las filas JPA {@link CapaCosto} (ya ordenadas por {@code secuencia}) a la
     * lista de value objects puros {@link CapaCostoValor} que consume el motor de costeo.
     */
    private static List<CapaCostoValor> aValor(List<CapaCosto> capas) {
        List<CapaCostoValor> valores = new ArrayList<>(capas.size());
        for (CapaCosto capa : capas) {
            valores.add(new CapaCostoValor(capa.getCantidadRestante(), capa.getCostoUnitario()));
        }
        return valores;
    }

    /**
     * Calcula la siguiente {@code secuencia} monotonica para una nueva capa PEPS del
     * (Almacen, Material): {@code MAX(secuencia) + 1}, o 1 si aun no hay capas. Preserva el
     * orden de llegada para el consumo FIFO (DECISION 23.2).
     */
    private long siguienteSecuencia(UUID almacenId, UUID materialId) {
        Long maximo = capaCostoRepository.maxSecuencia(almacenId, materialId);
        return (maximo == null ? 0L : maximo) + 1L;
    }

    /**
     * Calcula el snapshot del costo total del saldo tras un movimiento como
     * {@code cantidad * costoPromedio}, redondeado a la escala de costo (4, HALF_UP),
     * para la columna {@code saldo_costo_total} del Kardex (Req 60.12).
     */
    private static BigDecimal saldoCostoTotal(BigDecimal saldoCantidad, BigDecimal costoPromedio) {
        BigDecimal cantidad = saldoCantidad == null ? BigDecimal.ZERO : saldoCantidad;
        BigDecimal costo = costoPromedio == null ? BigDecimal.ZERO : costoPromedio;
        return cantidad.multiply(costo).setScale(ExistenciaAlmacen.ESCALA_COSTO, RoundingMode.HALF_UP);
    }

    /**
     * Audita el registro de un movimiento de inventario por Almacen (Req 60.7), incluyendo el
     * tipo, la cantidad, el costo y el saldo resultante; si el movimiento pertenece a una
     * transferencia, referencia el {@code transferencia_id} que agrupa ambas patas (Req 60.13).
     */
    private void auditarMovimiento(String actor, TipoMovimientoAlmacen tipo,
                                   MovimientoAlmacen movimiento, UUID transferenciaId) {
        String detalle = "movimiento '" + tipo.valorBd() + "' cantidad=" + movimiento.getCantidad()
                + " costoUnitario=" + movimiento.getCostoUnitario()
                + " costoTotal=" + movimiento.getCostoTotal()
                + " sobre Material " + movimiento.getMaterialId()
                + " en Almacen " + movimiento.getAlmacenId()
                + " -> saldo=" + movimiento.getSaldoCantidad()
                + (transferenciaId == null ? "" : " [transferencia=" + transferenciaId + "]");
        auditar(actor, "registrar_movimiento", RECURSO_MOVIMIENTO, movimiento.getId(),
                detalle, null, "saldo=" + movimiento.getSaldoCantidad());
    }

    /**
     * Normaliza un texto recortando espacios; devuelve {@code null} si es nulo o queda vacio.
     */
    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Pagina en memoria una lista ya materializada (por ejemplo los Lotes de un Material),
     * respetando el {@link Pageable} recibido (offset/tamano) y conservando el total real
     * para los metadatos de paginacion (Req 12, Property 24).
     */
    private static <T> Page<T> paginar(List<T> elementos, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(elementos);
        }
        int desde = (int) Math.min((long) pageable.getOffset(), elementos.size());
        int hasta = (int) Math.min((long) desde + pageable.getPageSize(), elementos.size());
        List<T> contenido = elementos.subList(desde, hasta);
        return new PageImpl<>(contenido, pageable, elementos.size());
    }
    private MetodoCosteo interpretarMetodo(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El metodo de costeo es obligatorio.");
        }
        try {
            return MetodoCosteo.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Metodo de costeo desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, String recurso, UUID recursoId,
                         String detalle, String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, recurso,
                detalle + " [id=" + recursoId + "]",
                aJson(valorAnterior), aJson(valorNuevo)));
    }

    /**
     * Coacciona un valor a una cadena JSON valida para las columnas JSONB
     * {@code valor_anterior}/{@code valor_nuevo} de la bitacora (evita el error
     * 22P02 de PostgreSQL -> HTTP 500). Un {@code null} o texto en blanco se
     * conserva como {@code null}; un texto que ya es JSON valido (objeto,
     * arreglo, cadena entrecomillada, numero, booleano o {@code null}) se
     * respeta; cualquier otro texto plano se envuelve como literal de cadena
     * JSON con el escape adecuado.
     */
    private static String aJson(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        char inicio = limpio.charAt(0);
        boolean pareceJson = inicio == '{' || inicio == '[' || inicio == '"'
                || "true".equals(limpio) || "false".equals(limpio) || "null".equals(limpio)
                || limpio.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
        if (pareceJson) {
            return limpio;
        }
        StringBuilder sb = new StringBuilder(limpio.length() + 2);
        sb.append('"');
        for (int i = 0; i < limpio.length(); i++) {
            char ch = limpio.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
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

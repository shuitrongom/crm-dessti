package com.dessti.crm.comercial.cotizacion.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.cotizacion.application.DatosClientePort.DatosCliente;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;
import com.dessti.crm.comercial.cotizacion.domain.EstadoCotizacion;
import com.dessti.crm.comercial.cotizacion.domain.PartidaCotizacion;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort;
import com.dessti.crm.comercial.producto.application.SugerenciaPrecioPort.ConsultaSugerenciaPrecio;
import com.dessti.crm.notificaciones.application.MensajeNotificacion;
import com.dessti.crm.notificaciones.application.NotificadorCorreoPort;
import com.dessti.crm.notificaciones.application.ResultadoEnvio;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que gobierna el ciclo de vida de las {@link Cotizacion}
 * (Req 6). Replica el patron establecido por {@code ServicioOportunidades}/
 * {@code ServicioProductos}.
 *
 * <h2>Operaciones (Req 6)</h2>
 * <ul>
 *   <li><strong>crearCotizacion (Req 6.1, 6.2, 6.3, 6.5):</strong> verifica que
 *       el Cliente exista y este activo en el tenant (via
 *       {@link ClienteExistentePort}; 404 si no), construye las partidas
 *       (sugiriendo el precio de las que refieren un Producto sin precio, via
 *       {@link SugerenciaPrecioPort}), exige entre 1 y 500 partidas, calcula los
 *       subtotales y el total half-up, fija estado inicial {@code borrador},
 *       persiste y audita.</li>
 *   <li><strong>agregarPartida (Req 6.3, 6.4, 6.5):</strong> recalcula totales al
 *       agregar; sugiere el precio si la partida refiere un Producto y no se
 *       indica; audita.</li>
 *   <li><strong>cambiarEstado (Req 6.6, 6.7, 6.10):</strong> aplica la maquina de
 *       estados pura (409 si la transicion es invalida; guarda de >=1 partida para
 *       enviar) y audita el estado anterior y el nuevo.</li>
 *   <li><strong>consultar (Req 4.3, 23.3):</strong> 404 + auditoria del intento si
 *       no es accesible.</li>
 *   <li><strong>listar (Req 6.8, 6.9):</strong> listado paginado (20/100) con
 *       filtros por Cliente y estado.</li>
 *   <li><strong>crearDesdeOportunidad (Req 14.5):</strong> crea un cascaron en
 *       {@code borrador} vinculado a la Oportunidad de origen (ver
 *       {@link com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CreacionCotizacionAdapter}).</li>
 * </ul>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 6.10)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la
 * peticion, Req 23.4). Cada operacion relevante se registra via
 * {@link AuditoriaPort} como evento de tenant con el actor derivado del contexto
 * de seguridad; el cambio de estado incluye el estado anterior y el nuevo (Req 6.10).</p>
 */
@Service
public class ServicioCotizaciones {

    /** Tipo de recurso de auditoria/RBAC de la Cotizacion. */
    static final String RECURSO_COTIZACION = "cotizacion";

    private final CotizacionRepository cotizacionRepository;
    private final ClienteExistentePort clienteExistente;
    private final CanalVentaExistentePort canalVentaExistente;
    private final SugerenciaPrecioPort sugerenciaPrecio;
    private final DatosClientePort datosCliente;
    private final FolioCotizacionPort folioCotizacion;
    private final CotizacionPdfService pdfService;
    private final NotificadorCorreoPort notificadorCorreo;
    private final EmpresaEmisorPort empresaEmisor;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioCotizaciones(CotizacionRepository cotizacionRepository,
                                ClienteExistentePort clienteExistente,
                                CanalVentaExistentePort canalVentaExistente,
                                SugerenciaPrecioPort sugerenciaPrecio,
                                DatosClientePort datosCliente,
                                FolioCotizacionPort folioCotizacion,
                                CotizacionPdfService pdfService,
                                NotificadorCorreoPort notificadorCorreo,
                                EmpresaEmisorPort empresaEmisor,
                                AuditoriaPort auditoria,
                                Clock clock) {
        this.cotizacionRepository = cotizacionRepository;
        this.clienteExistente = clienteExistente;
        this.canalVentaExistente = canalVentaExistente;
        this.sugerenciaPrecio = sugerenciaPrecio;
        this.datosCliente = datosCliente;
        this.folioCotizacion = folioCotizacion;
        this.pdfService = pdfService;
        this.notificadorCorreo = notificadorCorreo;
        this.empresaEmisor = empresaEmisor;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Prefijo de los folios legibles de Cotizacion (V60). */
    private static final String PREFIJO_FOLIO = "COT";

    /**
     * Da de alta una Cotizacion asociada a un Cliente existente con entre 1 y 500
     * partidas, en estado inicial {@code borrador} (Req 6.1, 6.2). Calcula los
     * subtotales y el total half-up (Req 6.3, 6.5; Property 2).
     *
     * @param comando datos de la Cotizacion a crear.
     * @return el DTO de la Cotizacion creada.
     * @throws RecursoNoEncontradoException si el Cliente no existe/activo en el
     *         tenant (404, Req 6.1, 23.3).
     * @throws ReglaNegocioException si faltan datos, el numero de partidas esta
     *         fuera de [1, 500], o una partida es invalida (422, Req 6.2, 6.4).
     */
    @Transactional
    public CotizacionDto crearCotizacion(CrearCotizacionCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la Cotizacion son obligatorios.");
        }
        if (comando.clienteId() == null) {
            throw new ReglaNegocioException("La Cotizacion debe asociarse a un Cliente existente.");
        }
        if (!clienteExistente.existeClienteActivo(comando.clienteId())) {
            auditarAccesoCruzado(actor, "cliente", comando.clienteId());
            throw new RecursoNoEncontradoException("No se encontro el Cliente indicado para la Cotizacion.");
        }
        List<CrearPartidaCommand> comandosPartida =
                (comando.partidas() == null) ? List.of() : comando.partidas();
        List<PartidaCotizacion> partidas = new ArrayList<>();
        for (CrearPartidaCommand pc : comandosPartida) {
            partidas.add(construirPartida(pc, actor));
        }
        Cotizacion cotizacion = Cotizacion.crear(comando.clienteId(), partidas, actor);

        // Datos descriptivos (V60): fecha de emision = hoy (segun el Clock),
        // moneda por defecto MXN, y los campos opcionales del comando. La regla
        // valido_hasta >= fecha_emision la aplica el dominio (422 si se viola).
        LocalDate hoy = LocalDate.now(clock);
        cotizacion.aplicarDatosDescriptivos(hoy, comando.validoHasta(),
                comando.condiciones(), comando.notas(), comando.moneda(), actor);

        // Folio legible por humanos (V60): consecutivo atomico por (tenant, anio).
        cotizacion.asignarFolio(componerFolio(hoy.getYear()));

        Cotizacion guardada = cotizacionRepository.save(cotizacion);
        auditar(actor, "crear", guardada.getId(),
                "creada cotizacion folio '" + guardada.getFolio() + "' en estado '"
                        + guardada.getEstado().valorBd() + "' con " + guardada.getPartidas().size()
                        + " partida(s), total " + guardada.getTotal().toPlainString(), null, null);
        return CotizacionDto.de(guardada, resolverCliente(guardada.getClienteId()));
    }

    /**
     * Compone el folio {@code COT-<anio>-<nnnn>} con el consecutivo por (tenant,
     * anio) reservado atomicamente (V60), con relleno de ceros a 4 digitos.
     */
    private String componerFolio(int anio) {
        int consecutivo = folioCotizacion.siguienteConsecutivo(anio);
        return String.format(Locale.ROOT, "%s-%d-%04d", PREFIJO_FOLIO, anio, consecutivo);
    }

    /** Resuelve los datos visibles del Cliente para el DTO/PDF; nulo si no accesible. */
    private DatosCliente resolverCliente(UUID clienteId) {
        return datosCliente.buscarPorId(clienteId).orElse(null);
    }

    /**
     * Agrega una Partida_Cotizacion a una Cotizacion en {@code borrador} y
     * recalcula los totales (Req 6.3, 6.5). Si la partida refiere un Producto y no
     * se indica precio, se sugiere via {@link SugerenciaPrecioPort} (Req 59.4).
     *
     * @param cotizacionId identificador de la Cotizacion.
     * @param comando      datos de la partida a agregar.
     * @return el DTO de la Cotizacion actualizada.
     * @throws RecursoNoEncontradoException si la Cotizacion no es accesible (404).
     * @throws ReglaNegocioException si la partida es invalida, la Cotizacion no
     *         esta en {@code borrador} o se excede el maximo de partidas (422).
     */
    @Transactional
    public CotizacionDto agregarPartida(UUID cotizacionId, CrearPartidaCommand comando) {
        String actor = actorActual();
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la partida son obligatorios.");
        }
        Cotizacion cotizacion = cargar(cotizacionId, actor);
        PartidaCotizacion partida = construirPartida(comando, actor);
        cotizacion.agregarPartida(partida, actor);
        Cotizacion guardada = cotizacionRepository.save(cotizacion);
        auditar(actor, "agregar_partida", guardada.getId(),
                "agregada partida; total recalculado " + guardada.getTotal().toPlainString(),
                null, null);
        return CotizacionDto.de(guardada, resolverCliente(guardada.getClienteId()));
    }

    /**
     * Cambia el estado de una Cotizacion aplicando la maquina de estados pura
     * (Req 6.6, 6.7) y auditando el estado anterior y el nuevo (Req 6.10).
     *
     * @param cotizacionId identificador de la Cotizacion.
     * @param nuevoEstado  etiqueta del estado destino; obligatoria (Req 6.6).
     * @return el DTO de la Cotizacion con su nuevo estado.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     * @throws ReglaNegocioException si el estado es nulo/desconocido, o si se
     *         intenta enviar sin partidas (422).
     * @throws com.dessti.crm.platform.error.TransicionInvalidaException si la
     *         transicion no esta permitida (409, Req 6.7).
     */
    @Transactional
    public CotizacionDto cambiarEstado(UUID cotizacionId, String nuevoEstado) {
        String actor = actorActual();
        EstadoCotizacion destino = interpretarEstado(nuevoEstado);
        Cotizacion cotizacion = cargar(cotizacionId, actor);
        EstadoCotizacion anterior = cotizacion.getEstado();
        cotizacion.cambiarEstado(destino, actor);
        Cotizacion guardada = cotizacionRepository.save(cotizacion);
        auditar(actor, "cambiar_estado", guardada.getId(),
                "cambio de estado '" + anterior.valorBd() + "' -> '" + destino.valorBd() + "'",
                anterior.valorBd(), destino.valorBd());
        return CotizacionDto.de(guardada, resolverCliente(guardada.getClienteId()));
    }

    /**
     * Clasifica una Cotizacion por canal de venta, asigna o modifica el canal, o
     * lo limpia (Req 63.1). Si se indica un canal, se verifica que exista y este
     * activo en el tenant (via {@link CanalVentaExistentePort}); si no existe, se
     * responde 404 y se audita el intento de acceso cruzado (Req 23.3). La
     * asignacion/modificacion se audita con el actor, la accion, el recurso y la
     * marca temporal, registrando el canal anterior y el nuevo (Req 63.3).
     *
     * @param cotizacionId identificador de la Cotizacion.
     * @param canalVentaId identificador del Canal_Venta; {@code null} para limpiar
     *                     la clasificacion.
     * @return el DTO de la Cotizacion con su canal actualizado.
     * @throws RecursoNoEncontradoException si la Cotizacion no es accesible (404),
     *         o si el canal indicado no existe/activo en el tenant (404, Req 63.1).
     */
    @Transactional
    public CotizacionDto asignarCanalVenta(UUID cotizacionId, UUID canalVentaId) {
        String actor = actorActual();
        Cotizacion cotizacion = cargar(cotizacionId, actor);
        if (canalVentaId != null && !canalVentaExistente.existeCanalVentaActivo(canalVentaId)) {
            auditarAccesoCruzado(actor, "canal_venta", canalVentaId);
            throw new RecursoNoEncontradoException(
                    "No se encontro el canal de venta indicado para la Cotizacion.");
        }
        UUID anterior = cotizacion.getCanalVentaId();
        cotizacion.asignarCanalVenta(canalVentaId, actor);
        Cotizacion guardada = cotizacionRepository.save(cotizacion);
        auditar(actor, "asignar_canal", guardada.getId(),
                "clasificada por canal de venta [canal=" + canalVentaId + "]",
                anterior == null ? null : anterior.toString(),
                canalVentaId == null ? null : canalVentaId.toString());
        return CotizacionDto.de(guardada, resolverCliente(guardada.getClienteId()));
    }

    /**
     * Consulta puntual de una Cotizacion del tenant (Req 4.3, 23.3).
     *
     * @param cotizacionId identificador de la Cotizacion.
     * @return el DTO de la Cotizacion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public CotizacionDto consultarCotizacion(UUID cotizacionId) {
        String actor = actorActual();
        Cotizacion cotizacion = cargar(cotizacionId, actor);
        return CotizacionDto.de(cotizacion, resolverCliente(cotizacion.getClienteId()));
    }

    /**
     * Listado paginado de Cotizaciones del tenant con filtros opcionales por
     * Cliente y por estado (Req 6.8, 6.9). Un filtro nulo no restringe.
     *
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param canalVentaId canal de venta a filtrar; {@code null} no filtra (Req 63.2).
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Cotizaciones como DTOs.
     * @throws ReglaNegocioException si la etiqueta de estado es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<CotizacionDto> listarCotizaciones(UUID clienteId, String estado,
                                                  UUID canalVentaId, Pageable pageable) {
        EstadoCotizacion filtro = (estado == null || estado.isBlank()) ? null : interpretarEstado(estado);
        return cotizacionRepository.buscarConFiltros(clienteId, filtro, canalVentaId, pageable)
                .map(CotizacionDto::de);
    }

    /**
     * Crea un cascaron de Cotizacion en {@code borrador} vinculado a la
     * Oportunidad de origen y a su Cliente, y devuelve su identificador (Req 14.5).
     * La usa el adaptador del {@code CreacionCotizacionPort} del submodulo de
     * Oportunidades; la guarda de que la Oportunidad este en etapa {@code ganado}
     * ya la aplica {@code ServicioOportunidades} antes de invocarla.
     *
     * @param oportunidadId Oportunidad de origen (etapa {@code ganado}, verificada).
     * @param clienteId     Cliente de la Oportunidad.
     * @param actor         identificador de quien realiza la conversion.
     * @return el identificador de la Cotizacion creada.
     * @throws ReglaNegocioException si faltan datos (422).
     */
    @Transactional
    public UUID crearDesdeOportunidad(UUID oportunidadId, UUID clienteId, String actor) {
        String actorEfectivo = (actor == null || actor.isBlank()) ? actorActual() : actor;
        Cotizacion cotizacion = Cotizacion.crearCascaronConversion(oportunidadId, clienteId, actorEfectivo);
        Cotizacion guardada = cotizacionRepository.save(cotizacion);
        auditar(actorEfectivo, "crear_por_conversion", guardada.getId(),
                "creada cotizacion (cascaron) por conversion de oportunidad [oportunidad="
                        + oportunidadId + "]", null, null);
        return guardada.getId();
    }

    /**
     * Genera el PDF PREMIUM de una Cotizacion (V60): carga la Cotizacion (404 si
     * no es accesible), resuelve los datos visibles del Cliente y delega el armado
     * del documento en {@link CotizacionPdfService}, enriquecido con los datos del
     * emisor (la Empresa del tenant, {@link DatosEmisor}). El emisor se deriva
     * SIEMPRE del tenant del contexto (Req 1.7), nunca de la plataforma.
     *
     * @param cotizacionId identificador de la Cotizacion; obligatorio.
     * @return los bytes del PDF (empieza con la firma {@code %PDF}).
     * @throws RecursoNoEncontradoException si la Cotizacion no es accesible (404).
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(UUID cotizacionId) {
        String actor = actorActual();
        Cotizacion cotizacion = cargar(cotizacionId, actor);
        DatosCliente cliente = resolverCliente(cotizacion.getClienteId());
        DatosEmisor emisor = resolverEmisor();
        return pdfService.generar(cotizacion, cliente, emisor);
    }

    /**
     * Indica si los datos fiscales de la Empresa emisora del tenant del contexto
     * estan incompletos (falta RFC o direccion, Req 3), para que la descarga del
     * PDF pueda anexar un encabezado informativo sin bloquear la generacion.
     *
     * @return {@code true} si el emisor tiene datos fiscales incompletos.
     */
    @Transactional(readOnly = true)
    public boolean emisorIncompleto() {
        return resolverEmisor().datosFiscalesIncompletos();
    }

    /**
     * Resuelve los datos del emisor (la Empresa) del tenant del contexto (Req 1.7).
     * Si por alguna anomalia la Empresa no se resuelve, cae a un emisor minimo
     * neutro; nunca a los datos de plataforma (Dess-TI).
     *
     * @return los datos del emisor de la Cotizacion.
     */
    private DatosEmisor resolverEmisor() {
        return empresaEmisor.emisorDeTenant(TenantContext.require())
                .orElse(DatosEmisor.minimo("Empresa"));
    }

    /**
     * Envia la Cotizacion por correo electronico (V60): genera su PDF PREMIUM,
     * invoca el puerto de correo con un mensaje que referencia la Cotizacion,
     * marca {@code enviada_en = ahora} y, si estaba en {@code borrador} (con &ge;1
     * partida), la transiciona a {@code enviada}. Devuelve el DTO actualizado.
     *
     * <p>El destinatario es el {@code email} indicado; si es nulo/blanco, se usa
     * el correo del Cliente. Si no hay ninguno disponible, se responde 422.</p>
     *
     * <p>El envio se considera exitoso aunque el adaptador de correo activo sea el
     * de registro en log ({@code NotificadorCorreoRegistroLog}): el flujo registra
     * el intento y no falla; el proveedor SMTP real se cablea por configuracion
     * mas adelante ({@code crm.notificaciones.correo.*}). Un fallo reportado por el
     * adaptador (por ejemplo, un proveedor real caido) SI se propaga como 422.</p>
     *
     * @param cotizacionId identificador de la Cotizacion; obligatorio.
     * @param emailDestino correo explicito; {@code null}/blanco usa el del Cliente.
     * @return el DTO de la Cotizacion con {@code enviadaEn} (y estado) actualizado.
     * @throws RecursoNoEncontradoException si la Cotizacion no es accesible (404).
     * @throws ReglaNegocioException si no hay correo disponible o el envio falla (422).
     */
    @Transactional
    public CotizacionDto enviarPorCorreo(UUID cotizacionId, String emailDestino) {
        String actor = actorActual();
        Cotizacion cotizacion = cargar(cotizacionId, actor);
        DatosCliente cliente = resolverCliente(cotizacion.getClienteId());

        String destinatario = primeroNoBlanco(emailDestino,
                cliente == null ? null : cliente.email());
        if (destinatario == null) {
            throw new ReglaNegocioException("El cliente no tiene correo; indique uno.");
        }

        // El emisor es la Empresa del tenant (Req 1); se marca si sus datos
        // fiscales estan incompletos para avisar al administrador (Req 3).
        DatosEmisor emisor = resolverEmisor();

        // Se genera el PDF para dejar constancia de que el documento adjuntable se
        // construyo correctamente antes de intentar el envio (el contrato de
        // MensajeNotificacion no transporta adjuntos; el proveedor real los anexara).
        byte[] pdf = pdfService.generar(cotizacion, cliente, emisor);

        String asunto = "Cotizacion " + (cotizacion.getFolio() == null ? "" : cotizacion.getFolio());
        String contenido = "Se adjunta la cotizacion " + cotizacion.getFolio()
                + " por un total de " + cotizacion.getMoneda() + " "
                + cotizacion.getTotal().toPlainString()
                + " (" + pdf.length + " bytes de PDF).";
        ResultadoEnvio resultado = notificadorCorreo.enviarCorreo(new MensajeNotificacion(
                cotizacion.getId(), destinatario, asunto, contenido));
        if (resultado == null || !resultado.exito()) {
            throw new ReglaNegocioException("No se pudo enviar la cotizacion por correo.");
        }

        // Si la Cotizacion sigue en 'borrador' y tiene partidas, el envio la
        // promueve a 'enviada' (reutiliza la maquina de estados del dominio).
        if (cotizacion.getEstado() == EstadoCotizacion.BORRADOR
                && !cotizacion.getPartidas().isEmpty()) {
            cotizacion.cambiarEstado(EstadoCotizacion.ENVIADA, actor);
        }
        Instant ahora = clock.instant();
        cotizacion.marcarEnviada(ahora, actor);

        Cotizacion guardada = cotizacionRepository.save(cotizacion);
        auditar(actor, "enviar_correo", guardada.getId(),
                "cotizacion folio '" + guardada.getFolio() + "' enviada por correo a '"
                        + destinatario + "' en estado '" + guardada.getEstado().valorBd() + "'",
                null, null);
        return CotizacionDto.de(guardada, cliente)
                .conEmisorIncompleto(emisor.datosFiscalesIncompletos());
    }

    private static String primeroNoBlanco(String preferido, String alternativo) {
        if (preferido != null && !preferido.isBlank()) {
            return preferido.strip();
        }
        if (alternativo != null && !alternativo.isBlank()) {
            return alternativo.strip();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Construye una partida a partir de su comando, resolviendo el precio unitario:
     * si el comando lo indica, se usa tal cual (el Usuario puede sobrescribir la
     * sugerencia); si no y la partida refiere un Producto, se intenta sugerir via
     * {@link SugerenciaPrecioPort} (Req 59.4). Si no hay precio disponible, se
     * rechaza (la fabrica de la partida exige un precio en rango).
     */
    private PartidaCotizacion construirPartida(CrearPartidaCommand comando, String actor) {
        if (comando == null) {
            throw new ReglaNegocioException("Los datos de la partida son obligatorios.");
        }
        BigDecimal precio = comando.precioUnitario();
        if (precio == null && comando.productoId() != null) {
            precio = sugerenciaPrecio.sugerirPrecioUnitario(
                    new ConsultaSugerenciaPrecio(comando.productoId(), null, LocalDate.now()))
                    .orElse(null);
        }
        if (precio == null) {
            throw new ReglaNegocioException(
                    "El precio unitario de la partida es obligatorio (no se pudo sugerir para el Producto).");
        }
        return PartidaCotizacion.crear(
                comando.productoId(), comando.descripcion(), comando.cantidad(), precio, actor);
    }

    private Cotizacion cargar(UUID cotizacionId, String actor) {
        if (cotizacionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Cotizacion solicitada.");
        }
        return cotizacionRepository.findById(cotizacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_COTIZACION, cotizacionId);
                    throw new RecursoNoEncontradoException("No se encontro la Cotizacion solicitada.");
                });
    }

    private EstadoCotizacion interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado destino es obligatorio.");
        }
        try {
            return EstadoCotizacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Cotizacion desconocido: " + etiqueta);
        }
    }

    private void auditar(String actor, String accion, UUID cotizacionId, String detalle,
                         String valorAnterior, String valorNuevo) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_COTIZACION,
                detalle + " [id=" + cotizacionId + "]", valorAnterior, valorNuevo));
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

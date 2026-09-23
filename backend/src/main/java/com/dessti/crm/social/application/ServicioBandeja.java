package com.dessti.crm.social.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;
import com.dessti.crm.social.adapter.out.meta.MetaProperties;
import com.dessti.crm.social.adapter.out.persistence.ConsentimientoCanalRepository;
import com.dessti.crm.social.adapter.out.persistence.ConversacionRepository;
import com.dessti.crm.social.adapter.out.persistence.CuentaCanalSocialRepository;
import com.dessti.crm.social.adapter.out.persistence.MensajeSocialRepository;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.ConsentimientoCanal;
import com.dessti.crm.social.domain.Conversacion;
import com.dessti.crm.social.domain.CuentaCanalSocial;
import com.dessti.crm.social.domain.EstadoConsentimiento;
import com.dessti.crm.social.domain.GuardaOptIn;
import com.dessti.crm.social.domain.GuardaVentanaServicio;
import com.dessti.crm.social.domain.MensajeSocial;
import com.dessti.crm.social.domain.TipoMensaje;

/**
 * Servicio de aplicacion de la Bandeja_Unificada (Req 64). Orquesta la recepcion de
 * mensajes entrantes, el envio saliente con las guardas de negocio y la politica de
 * reintentos, el registro de consentimiento, el handover y el listado. Replica el
 * patron de {@code ServicioOrdenesFabricacion}.
 *
 * <h2>Guardas de negocio</h2>
 * <ul>
 *   <li><strong>Ventana_Servicio (Req 64.6/64.7; Property 37):</strong> dentro de
 *       las 24h (configurable) del ultimo entrante se permite texto libre; fuera de
 *       la ventana el texto libre se rechaza (422) y se exige una Plantilla_Mensaje
 *       aprobada (o mensaje interactivo). Se evalua con {@link GuardaVentanaServicio}.</li>
 *   <li><strong>Opt_In marketing (Req 64.8; Property 38):</strong> un mensaje de
 *       marketing sin Opt_In vigente para el canal se rechaza (422). Se evalua con
 *       {@link GuardaOptIn}.</li>
 * </ul>
 *
 * <h2>Reintentos (Req 64.13)</h2>
 * <p>El despacho por {@code MensajeriaSocialPort} se reintenta hasta
 * {@link MetaProperties#maxIntentosEfectivo()} veces ante fallo; el
 * {@code estado_entrega} resultante se persiste en el Mensaje_Social (Req 64.11).</p>
 */
@Service
public class ServicioBandeja {

    /** Tipo de recurso de auditoria/RBAC de la Conversacion. */
    static final String RECURSO_CONVERSACION = "conversacion";

    /** Tipo de recurso de auditoria/RBAC del consentimiento. */
    static final String RECURSO_CONSENTIMIENTO = "consentimiento";

    private final ConversacionRepository conversacionRepository;
    private final MensajeSocialRepository mensajeRepository;
    private final CuentaCanalSocialRepository cuentaRepository;
    private final ConsentimientoCanalRepository consentimientoRepository;
    private final MensajeriaSocialPort mensajeriaSocial;
    private final CaptacionLeadPort captacionLead;
    private final ClienteExistenteSocialPort clienteExistente;
    private final AuditoriaPort auditoria;
    private final Clock clock;
    private final MetaProperties propiedades;

    public ServicioBandeja(ConversacionRepository conversacionRepository,
                           MensajeSocialRepository mensajeRepository,
                           CuentaCanalSocialRepository cuentaRepository,
                           ConsentimientoCanalRepository consentimientoRepository,
                           MensajeriaSocialPort mensajeriaSocial,
                           CaptacionLeadPort captacionLead,
                           ClienteExistenteSocialPort clienteExistente,
                           AuditoriaPort auditoria,
                           Clock clock,
                           MetaProperties propiedades) {
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.cuentaRepository = cuentaRepository;
        this.consentimientoRepository = consentimientoRepository;
        this.mensajeriaSocial = mensajeriaSocial;
        this.captacionLead = captacionLead;
        this.clienteExistente = clienteExistente;
        this.auditoria = auditoria;
        this.clock = clock;
        this.propiedades = propiedades;
    }

    /**
     * Procesa un Mensaje_Social entrante recibido por webhook (Req 64.3, 64.4). El
     * controlador de webhooks ya valido la firma del evento. Resuelve la cuenta que
     * recibio el evento, hace upsert de la Conversacion por remitente, persiste el
     * entrante, actualiza {@code ultimo_entrante_utc} (Ventana_Servicio) y, si el
     * remitente no tiene coincidencia en el CRM, dispara la captura de lead via
     * {@link CaptacionLeadPort}.
     *
     * @param comando datos del entrante; obligatorio.
     * @return el DTO del Mensaje_Social entrante persistido.
     * @throws RecursoNoEncontradoException si no existe la cuenta que recibio el evento (404).
     * @throws ReglaNegocioException si el comando es invalido (422).
     */
    @Transactional
    public MensajeSocialDto recibirMensajeEntrante(MensajeEntranteCommand comando) {
        String actor = "webhook";
        if (comando == null || comando.canal() == null) {
            throw new ReglaNegocioException("El evento entrante debe indicar el Canal_Social.");
        }
        if (comando.remitenteExterno() == null || comando.remitenteExterno().isBlank()) {
            throw new ReglaNegocioException("El evento entrante debe indicar el remitente.");
        }

        CuentaCanalSocial cuenta = cuentaRepository
                .findByCanalAndIdentificadorExterno(comando.canal(), comando.identificadorCuenta())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Cuenta_Canal_Social del evento entrante."));

        Instant ahora = clock.instant();
        Conversacion conversacion = conversacionRepository
                .findByCuentaCanalSocialIdAndRemitenteExterno(cuenta.getId(), comando.remitenteExterno())
                .orElse(null);

        if (conversacion == null) {
            // Sin coincidencia previa: se abre la Conversacion y se captura el lead
            // (Contacto/Oportunidad) via el puerto desacoplado (Req 64.4).
            CaptacionLeadPort.ResultadoCaptacionLead lead = captacionLead.capturar(
                    new CaptacionLeadPort.SolicitudCaptacionLead(
                            comando.canal(), comando.remitenteExterno(), comando.nombreMostrado()));
            conversacion = Conversacion.abrir(cuenta.getId(), comando.canal(),
                    comando.remitenteExterno(), null,
                    (lead == null) ? null : lead.contactoId(), actor);
        }
        conversacion.registrarEntrante(ahora, actor);
        Conversacion guardadaConv = conversacionRepository.save(conversacion);

        MensajeSocial entrante = MensajeSocial.entrante(
                guardadaConv.getId(), comando.contenido(), comando.externoId(), ahora, actor);
        MensajeSocial guardado = mensajeRepository.save(entrante);

        auditarConversacion(actor, "recibir_entrante", guardadaConv.getId(),
                "entrante en canal '" + comando.canal().valorBd() + "' de remitente '"
                        + comando.remitenteExterno() + "'");
        return MensajeSocialDto.de(guardado);
    }

    /**
     * Envia un Mensaje_Social saliente en una Conversacion aplicando las guardas de
     * Ventana_Servicio (Req 64.7) y de Opt_In para marketing (Req 64.8) y
     * despachando por {@link MensajeriaSocialPort} con reintentos (Req 64.13). El
     * {@code estado_entrega} resultante se persiste (Req 64.11).
     *
     * @param conversacionId Conversacion destino; obligatorio.
     * @param comando        tipo/contenido/marketing del mensaje; obligatorio.
     * @return el DTO del Mensaje_Social saliente persistido.
     * @throws RecursoNoEncontradoException si la Conversacion no es accesible (404).
     * @throws ReglaNegocioException si la Conversacion esta cerrada; si se intenta
     *         texto libre fuera de la Ventana_Servicio (422, Req 64.7); o si es
     *         marketing sin Opt_In vigente (422, Req 64.8).
     */
    @Transactional
    public MensajeSocialDto enviarMensaje(UUID conversacionId, EnviarMensajeCommand comando) {
        String actor = actorActual();
        if (comando == null || comando.tipo() == null) {
            throw new ReglaNegocioException("El envio debe indicar el tipo del Mensaje_Social.");
        }
        Conversacion conversacion = cargarConversacion(conversacionId, actor);
        if (conversacion.estaCerrada()) {
            throw new ReglaNegocioException(
                    "La Conversacion esta cerrada; no admite el envio de mensajes.");
        }

        Instant ahora = clock.instant();
        int ventanaHoras = propiedades.ventanaServicioHorasEfectiva();

        // Guarda de Ventana_Servicio (Req 64.6/64.7; Property 37).
        if (!GuardaVentanaServicio.permiteEnvio(
                comando.tipo(), conversacion.getUltimoEntranteUtc(), ahora, ventanaHoras)) {
            throw new ReglaNegocioException(
                    "Fuera de la Ventana_Servicio: se requiere una Plantilla_Mensaje aprobada; "
                            + "no se admite texto libre.");
        }

        // Guarda de Opt_In para marketing (Req 64.8; Property 38).
        boolean tieneOptIn = tieneOptInVigente(conversacion.getCanal(), conversacion.getRemitenteExterno());
        if (!GuardaOptIn.puedeEnviarMarketing(comando.esMarketing(), tieneOptIn)) {
            throw new ReglaNegocioException(
                    "El destinatario no tiene un Opt_In vigente para este Canal_Social; "
                            + "no se admite mensajeria de marketing.");
        }

        MensajeSocial saliente = MensajeSocial.saliente(
                conversacion.getId(), comando.tipo(), comando.contenido(),
                comando.esMarketing(), ahora, actor);

        // Despacho por el puerto con politica de reintentos (Req 64.13).
        ResultadoEnvioSocial resultado = despacharConReintentos(conversacion, comando, actor);
        saliente.registrarResultadoEnvio(resultado.externoId(), resultado.estadoEntrega(), actor);
        MensajeSocial guardado = mensajeRepository.save(saliente);

        auditarConversacion(actor, "enviar", conversacion.getId(),
                "saliente tipo '" + comando.tipo().valorBd() + "' marketing=" + comando.esMarketing()
                        + " estado_entrega='" + resultado.estadoEntrega().valorBd() + "'");
        return MensajeSocialDto.de(guardado);
    }

    /**
     * Registra un Opt_In u Opt_Out del sujeto en un canal (Req 64.9), con el actor y
     * la marca temporal UTC del reloj del servicio.
     *
     * @param canal         Canal_Social; obligatorio.
     * @param sujetoExterno identificador del sujeto en el canal; obligatorio.
     * @param clienteId     Cliente asociado; opcional.
     * @param estado        {@link EstadoConsentimiento#OPT_IN} u {@code OPT_OUT}; obligatorio.
     * @return el DTO del Mensaje_Social no aplica; se devuelve el consentimiento como resumen.
     */
    @Transactional
    public ConsentimientoRegistradoDto registrarConsentimiento(CanalSocial canal, String sujetoExterno,
                                                               UUID clienteId, EstadoConsentimiento estado) {
        String actor = actorActual();
        ConsentimientoCanal consentimiento = ConsentimientoCanal.registrar(
                canal, sujetoExterno, clienteId, estado, clock.instant(), actor);
        ConsentimientoCanal guardado = consentimientoRepository.save(consentimiento);
        auditarConsentimiento(actor, guardado.getId(),
                "registrado " + estado.valorBd() + " canal '" + canal.valorBd()
                        + "' sujeto '" + guardado.getSujetoExterno() + "'");
        return new ConsentimientoRegistradoDto(
                guardado.getId(), guardado.getCanal().valorBd(), guardado.getSujetoExterno(),
                guardado.getEstado().valorBd(), guardado.getRegistradoEn());
    }

    /**
     * Asigna/transfiere una Conversacion a un Usuario (handover, Req 64.10).
     *
     * @param conversacionId Conversacion; obligatorio.
     * @param usuarioId      Usuario responsable; obligatorio.
     * @return el DTO de la Conversacion con su nuevo estado/asignacion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public ConversacionDto asignar(UUID conversacionId, UUID usuarioId) {
        String actor = actorActual();
        Conversacion conversacion = cargarConversacion(conversacionId, actor);
        conversacion.asignar(usuarioId, actor);
        Conversacion guardada = conversacionRepository.save(conversacion);
        auditarConversacion(actor, "asignar", guardada.getId(),
                "handover a usuario " + usuarioId);
        return ConversacionDto.de(guardada);
    }

    /**
     * Cierra una Conversacion (Req 64.10).
     *
     * @param conversacionId Conversacion; obligatorio.
     * @return el DTO de la Conversacion cerrada.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional
    public ConversacionDto cerrar(UUID conversacionId) {
        String actor = actorActual();
        Conversacion conversacion = cargarConversacion(conversacionId, actor);
        conversacion.cerrar(actor);
        Conversacion guardada = conversacionRepository.save(conversacion);
        auditarConversacion(actor, "cerrar", guardada.getId(), "Conversacion cerrada");
        return ConversacionDto.de(guardada);
    }

    /**
     * Vincula una Conversacion a un Cliente existente del tenant (lead social,
     * Req 64.4, 5.1, 5.2). Valida que el Cliente exista y este activo en el tenant
     * vigente antes de invocar el metodo de dominio {@code vincular}; audita la
     * operacion. Un {@code clienteId} nulo desvincula/no altera el Cliente actual
     * segun la semantica del dominio.
     *
     * @param conversacionId Conversacion a vincular; obligatorio.
     * @param clienteId      Cliente del tenant a vincular; opcional (nulo no valida).
     * @return el DTO de la Conversacion con su Cliente vinculado.
     * @throws RecursoNoEncontradoException si la Conversacion no es accesible (404) o
     *         si el Cliente indicado no existe en el tenant (404).
     */
    @Transactional
    public ConversacionDto vincular(UUID conversacionId, UUID clienteId) {
        String actor = actorActual();
        Conversacion conversacion = cargarConversacion(conversacionId, actor);
        if (clienteId != null && !clienteExistente.existeClienteActivo(clienteId)) {
            auditarAccesoCruzado(actor, "cliente", clienteId);
            throw new RecursoNoEncontradoException("No se encontro el Cliente indicado.");
        }
        conversacion.vincular(clienteId, null, actor);
        Conversacion guardada = conversacionRepository.save(conversacion);
        auditarConversacion(actor, "vincular", guardada.getId(),
                "vinculada a cliente " + clienteId);
        return ConversacionDto.de(guardada);
    }

    /**
     * Consulta puntual de una Conversacion del tenant (Req 23.3).
     *
     * @param conversacionId identificador de la Conversacion.
     * @return el DTO de la Conversacion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public ConversacionDto consultar(UUID conversacionId) {
        String actor = actorActual();
        return ConversacionDto.de(cargarConversacion(conversacionId, actor));
    }

    /**
     * Listado paginado de la Bandeja_Unificada del tenant con filtros opcionales por
     * canal, Cliente y estado (Req 64.5, 64.14).
     *
     * @param canal     Canal_Social a filtrar; {@code null} no filtra.
     * @param clienteId Cliente a filtrar; {@code null} no filtra.
     * @param estado    estado a filtrar; {@code null} no filtra.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de Conversaciones como DTOs.
     */
    @Transactional(readOnly = true)
    public Page<ConversacionDto> listarBandeja(CanalSocial canal, UUID clienteId,
                                               com.dessti.crm.social.domain.EstadoConversacion estado,
                                               Pageable pageable) {
        return conversacionRepository.buscarBandeja(canal, clienteId, estado, pageable)
                .map(ConversacionDto::de);
    }

    /**
     * Historial paginado de mensajes de una Conversacion del tenant (Req 64.5).
     *
     * @param conversacionId Conversacion; obligatorio.
     * @param pageable       parametros de paginacion ya acotados (20/100).
     * @return la pagina de mensajes como DTOs.
     * @throws RecursoNoEncontradoException si la Conversacion no es accesible (404).
     */
    @Transactional(readOnly = true)
    public Page<MensajeSocialDto> listarMensajes(UUID conversacionId, Pageable pageable) {
        String actor = actorActual();
        cargarConversacion(conversacionId, actor);
        return mensajeRepository.findByConversacionId(conversacionId, pageable)
                .map(MensajeSocialDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    /**
     * Determina si el sujeto tiene un Opt_In vigente en el canal: el ultimo registro
     * de consentimiento por (canal, sujeto) esta en estado {@code opt_in} (Req 64.9).
     *
     * @param canal         Canal_Social.
     * @param sujetoExterno identificador del sujeto.
     * @return {@code true} si hay un Opt_In vigente.
     */
    boolean tieneOptInVigente(CanalSocial canal, String sujetoExterno) {
        return consentimientoRepository
                .findFirstByCanalAndSujetoExternoOrderByRegistradoEnDesc(canal, sujetoExterno)
                .map(ConsentimientoCanal::esVigente)
                .orElse(false);
    }

    private ResultadoEnvioSocial despacharConReintentos(Conversacion conversacion,
                                                        EnviarMensajeCommand comando, String actor) {
        SolicitudEnvioSocial solicitud = new SolicitudEnvioSocial(
                conversacion.getCanal(),
                credencialesRefDe(conversacion),
                conversacion.getRemitenteExterno(),
                comando.tipo(),
                comando.contenido());

        int maxIntentos = propiedades.maxIntentosEfectivo();
        ResultadoEnvioSocial resultado = null;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            resultado = despachar(comando.tipo(), solicitud);
            if (resultado.exito()) {
                return resultado;
            }
            // Fallo: se reintenta hasta agotar los intentos (Req 64.13).
            auditarConversacion(actor, "reintento_envio", conversacion.getId(),
                    "intento " + intento + "/" + maxIntentos + " fallido");
        }
        return resultado;
    }

    private ResultadoEnvioSocial despachar(TipoMensaje tipo, SolicitudEnvioSocial solicitud) {
        return switch (tipo) {
            case TEXTO -> mensajeriaSocial.enviarTexto(solicitud);
            case PLANTILLA -> mensajeriaSocial.enviarPlantilla(solicitud);
            case INTERACTIVO -> mensajeriaSocial.enviarInteractivo(solicitud);
        };
    }

    private String credencialesRefDe(Conversacion conversacion) {
        return cuentaRepository.findById(conversacion.getCuentaCanalSocialId())
                .map(CuentaCanalSocial::getCredencialesRef)
                .orElse(null);
    }

    private Conversacion cargarConversacion(UUID conversacionId, String actor) {
        if (conversacionId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Conversacion solicitada.");
        }
        return conversacionRepository.findById(conversacionId)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, RECURSO_CONVERSACION, conversacionId);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Conversacion solicitada.");
                });
    }

    private void auditarConversacion(String actor, String accion, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_CONVERSACION,
                detalle + " [id=" + id + "]", null, null));
    }

    private void auditarConsentimiento(String actor, UUID id, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "registrar", RECURSO_CONSENTIMIENTO,
                detalle + " [id=" + id + "]", null, null));
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

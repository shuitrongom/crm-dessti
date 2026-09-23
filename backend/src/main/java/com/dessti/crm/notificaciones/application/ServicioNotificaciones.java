package com.dessti.crm.notificaciones.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.notificaciones.adapter.out.persistence.IntentoEnvioNotificacionRepository;
import com.dessti.crm.notificaciones.adapter.out.persistence.NotificacionRepository;
import com.dessti.crm.notificaciones.domain.CanalNotificacion;
import com.dessti.crm.notificaciones.domain.EstadoNotificacion;
import com.dessti.crm.notificaciones.domain.IntentoEnvioNotificacion;
import com.dessti.crm.notificaciones.domain.Notificacion;
import com.dessti.crm.notificaciones.domain.TipoEventoNotificacion;
import com.dessti.crm.platform.audit.AuditoriaPort;
import com.dessti.crm.platform.audit.EventoAuditoria;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;
import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.security.rbac.AutenticacionActual;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Servicio de aplicacion que implementa el {@link NotificacionPort} y gobierna la
 * generacion, entrega y auditoria de las {@link Notificacion} (Req 46). Replica el
 * patron establecido por {@code ServicioOrdenesFabricacion} (tenant via
 * {@link TenantContext}, auditoria via {@link AuditoriaPort}/{@link EventoAuditoria},
 * actor via {@link AutenticacionActual}).
 *
 * <h2>Flujo de {@link #notificar(SolicitudNotificacion)} (Req 46.1, 46.3, 46.5, 46.7)</h2>
 * <ol>
 *   <li>Crea la Notificacion en estado {@code pendiente} con el contenido ya
 *       minimizado por el llamador (Req 46.4).</li>
 *   <li><strong>Guarda de Opt_In (Req 46.7):</strong> si la Notificacion es de
 *       marketing y el canal es social y el destinatario NO tiene Opt_In vigente
 *       (via {@link ConsentimientoPort}), marca la Notificacion como {@code omitida}
 *       con su motivo, audita la omision y termina sin enviar.</li>
 *   <li>En otro caso, despacha al puerto de salida del canal correspondiente
 *       (correo/WhatsApp o {@link NotificadorSocialPort} para Canales Sociales,
 *       Req 46.6) y reintenta hasta {@code maxIntentos} (Req 46.3), persistiendo un
 *       {@link IntentoEnvioNotificacion} por intento; fija {@code enviada} (con
 *       {@code enviada_en}) al primer exito o {@code fallida} al agotar los
 *       reintentos.</li>
 *   <li>Audita el resultado del envio con actor, evento de origen, destinatario,
 *       canal y resultado, con marca temporal UTC (Req 46.5).</li>
 * </ol>
 *
 * <h2>Aislamiento multi-tenant (Req 23) y auditoria (Req 46.5)</h2>
 * <p>El {@code tenant_id} se deriva del {@link TenantContext} (nunca de la solicitud,
 * Req 23.4). El actor se deriva del contexto autenticado o {@code "sistema"} para
 * eventos generados por procesos internos.</p>
 */
@Service
public class ServicioNotificaciones implements NotificacionPort {

    /** Tipo de recurso de auditoria/RBAC de la Notificacion. */
    static final String RECURSO_NOTIFICACION = "notificacion";

    private final NotificacionRepository notificacionRepository;
    private final IntentoEnvioNotificacionRepository intentoRepository;
    private final NotificadorCorreoPort notificadorCorreo;
    private final NotificadorWhatsappPort notificadorWhatsapp;
    private final NotificadorSocialPort notificadorSocial;
    private final ConsentimientoPort consentimiento;
    private final ReintentosProperties reintentos;
    private final AuditoriaPort auditoria;
    private final Clock clock;

    public ServicioNotificaciones(NotificacionRepository notificacionRepository,
                                  IntentoEnvioNotificacionRepository intentoRepository,
                                  NotificadorCorreoPort notificadorCorreo,
                                  NotificadorWhatsappPort notificadorWhatsapp,
                                  NotificadorSocialPort notificadorSocial,
                                  ConsentimientoPort consentimiento,
                                  ReintentosProperties reintentos,
                                  AuditoriaPort auditoria,
                                  Clock clock) {
        this.notificacionRepository = notificacionRepository;
        this.intentoRepository = intentoRepository;
        this.notificadorCorreo = notificadorCorreo;
        this.notificadorWhatsapp = notificadorWhatsapp;
        this.notificadorSocial = notificadorSocial;
        this.consentimiento = consentimiento;
        this.reintentos = reintentos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ReglaNegocioException si la solicitud es nula o incompleta (422).
     */
    @Override
    @Transactional
    public NotificacionDto notificar(SolicitudNotificacion solicitud) {
        if (solicitud == null) {
            throw new ReglaNegocioException("La solicitud de Notificacion es obligatoria.");
        }
        String actor = actorActual();
        Instant ahora = clock.instant();

        Notificacion notificacion = Notificacion.generar(
                solicitud.eventoOrigen(),
                solicitud.canalPreferido(),
                solicitud.destinatario(),
                solicitud.asunto(),
                solicitud.contenido(),
                solicitud.esMarketing(),
                solicitud.referenciaTipo(),
                solicitud.referenciaId(),
                actor,
                ahora);
        Notificacion guardada = notificacionRepository.saveAndFlush(notificacion);

        CanalNotificacion canal = guardada.getCanal();

        // Guarda de Opt_In para marketing en Canal_Social (Req 46.7).
        if (guardada.isEsMarketing() && canal.esSocial()
                && !consentimiento.tieneOptInVigente(canal, guardada.getDestinatario())) {
            String motivo = "sin Opt_In vigente para marketing en el canal social '"
                    + canal.valorBd() + "'";
            guardada.marcarOmitida(motivo, actor);
            Notificacion omitida = notificacionRepository.save(guardada);
            auditar(actor, "omitir", omitida, "omitida por " + motivo);
            return NotificacionDto.de(omitida);
        }

        // Despacho con politica de reintentos configurable (Req 46.2, 46.3).
        MensajeNotificacion mensaje = new MensajeNotificacion(
                guardada.getId(),
                guardada.getDestinatario(),
                guardada.getAsunto(),
                guardada.getContenido());

        int maxIntentos = reintentos.maxIntentosEfectivo();
        ResultadoEnvio ultimo = null;
        for (int numeroIntento = 1; numeroIntento <= maxIntentos; numeroIntento++) {
            ultimo = despachar(canal, mensaje);
            registrarIntento(guardada.getId(), numeroIntento, ultimo, actor);
            if (ultimo.exito()) {
                break;
            }
        }

        boolean exito = ultimo != null && ultimo.exito();
        if (exito) {
            guardada.marcarEnviada(clock.instant(), actor);
        } else {
            guardada.marcarFallida(actor);
        }
        Notificacion finalizada = notificacionRepository.save(guardada);

        auditar(actor, exito ? "enviar" : "fallar_envio", finalizada,
                (exito ? "enviada" : "fallida tras agotar reintentos")
                        + " por canal '" + canal.valorBd() + "'"
                        + (exito ? "" : " [motivo=" + motivoSeguro(ultimo) + "]"));
        return NotificacionDto.de(finalizada);
    }

    /**
     * Consulta puntual de una Notificacion del tenant (Req 23.3).
     *
     * @param id identificador de la Notificacion.
     * @return el DTO de la Notificacion.
     * @throws RecursoNoEncontradoException si no es accesible (404).
     */
    @Transactional(readOnly = true)
    public NotificacionDto consultar(UUID id) {
        String actor = actorActual();
        return NotificacionDto.de(cargar(id, actor));
    }

    /**
     * Listado paginado de Notificaciones del tenant con filtros opcionales por
     * estado, por evento de origen y por canal. Un filtro nulo/blanco no restringe;
     * sin coincidencias se devuelve una pagina vacia con total 0.
     *
     * @param estado   etiqueta de estado a filtrar; {@code null}/blanco no filtra.
     * @param evento   etiqueta de evento a filtrar; {@code null}/blanco no filtra.
     * @param canal    etiqueta de canal a filtrar; {@code null}/blanco no filtra.
     * @param pageable parametros de paginacion ya acotados (20/100).
     * @return la pagina de Notificaciones como DTOs.
     * @throws ReglaNegocioException si una etiqueta de filtro es desconocida (422).
     */
    @Transactional(readOnly = true)
    public Page<NotificacionDto> listar(String estado, String evento, String canal, Pageable pageable) {
        EstadoNotificacion filtroEstado = interpretarEstado(estado);
        TipoEventoNotificacion filtroEvento = interpretarEvento(evento);
        CanalNotificacion filtroCanal = interpretarCanal(canal);
        return notificacionRepository
                .buscarConFiltros(filtroEstado, filtroEvento, filtroCanal, pageable)
                .map(NotificacionDto::de);
    }

    // ------------------------------------------------------------------
    // Reglas internas
    // ------------------------------------------------------------------

    private ResultadoEnvio despachar(CanalNotificacion canal, MensajeNotificacion mensaje) {
        try {
            return switch (canal) {
                case CORREO -> notificadorCorreo.enviarCorreo(mensaje);
                // Los Canales Sociales reutilizan la integracion social (Req 46.6).
                case WHATSAPP, MESSENGER, INSTAGRAM ->
                        notificadorSocial.enviarPorCanalSocial(canal, mensaje);
            };
        } catch (RuntimeException ex) {
            // Un adaptador que lanza excepcion se trata como fallo del intento
            // (Req 46.3); el mensaje se registra sin datos sensibles.
            return ResultadoEnvio.fallido("excepcion del adaptador: " + ex.getClass().getSimpleName());
        }
    }

    private void registrarIntento(UUID notificacionId, int numeroIntento,
                                  ResultadoEnvio resultado, String actor) {
        IntentoEnvioNotificacion intento = IntentoEnvioNotificacion.registrar(
                notificacionId,
                numeroIntento,
                resultado.exito(),
                resultado.exito() ? null : motivoSeguro(resultado),
                clock.instant(),
                actor);
        intentoRepository.save(intento);
    }

    private Notificacion cargar(UUID id, String actor) {
        if (id == null) {
            throw new RecursoNoEncontradoException("No se encontro la Notificacion solicitada.");
        }
        return notificacionRepository.findById(id)
                .orElseGet(() -> {
                    auditarAccesoCruzado(actor, id);
                    throw new RecursoNoEncontradoException(
                            "No se encontro la Notificacion solicitada.");
                });
    }

    private EstadoNotificacion interpretarEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return EstadoNotificacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Notificacion desconocido: " + etiqueta);
        }
    }

    private TipoEventoNotificacion interpretarEvento(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return TipoEventoNotificacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Evento de origen de Notificacion desconocido: " + etiqueta);
        }
    }

    private CanalNotificacion interpretarCanal(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return CanalNotificacion.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Canal de Notificacion desconocido: " + etiqueta);
        }
    }

    private static String motivoSeguro(ResultadoEnvio resultado) {
        if (resultado == null || resultado.mensajeError() == null || resultado.mensajeError().isBlank()) {
            return "fallo de envio sin detalle";
        }
        return resultado.mensajeError();
    }

    private void auditar(String actor, String accion, Notificacion notificacion, String detalle) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, accion, RECURSO_NOTIFICACION,
                detalle + " [id=" + notificacion.getId()
                        + ", evento=" + notificacion.getEventoOrigen().valorBd()
                        + ", canal=" + notificacion.getCanal().valorBd()
                        + ", destinatario=" + notificacion.getDestinatario() + "]",
                null, notificacion.getEstado().valorBd()));
    }

    private void auditarAccesoCruzado(String actor, UUID recursoId) {
        auditoria.registrar(EventoAuditoria.deTenant(
                TenantContext.require(), actor, "acceso_denegado", RECURSO_NOTIFICACION,
                "intento de acceso a notificacion no disponible en el tenant [id=" + recursoId + "]",
                null, null));
    }

    private String actorActual() {
        return AutenticacionActual.obtener()
                .map(Authentication::getName)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElse("sistema");
    }
}

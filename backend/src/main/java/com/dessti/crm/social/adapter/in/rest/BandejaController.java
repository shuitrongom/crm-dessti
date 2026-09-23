package com.dessti.crm.social.adapter.in.rest;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.platform.web.pagination.PageRequestFactory;
import com.dessti.crm.platform.web.pagination.PaginaResponse;
import com.dessti.crm.social.application.ConsentimientoRegistradoDto;
import com.dessti.crm.social.application.ConversacionDto;
import com.dessti.crm.social.application.EnviarMensajeCommand;
import com.dessti.crm.social.application.MensajeSocialDto;
import com.dessti.crm.social.application.ServicioBandeja;
import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoConsentimiento;
import com.dessti.crm.social.domain.EstadoConversacion;
import com.dessti.crm.social.domain.TipoMensaje;

import jakarta.validation.Valid;

/**
 * Adaptador de entrada REST de la Bandeja_Unificada (Req 64.5-64.14; tarea 40.3):
 * listado de Conversaciones consolidadas, historial de mensajes, envio con guardas
 * de negocio, handover y registro de consentimiento.
 *
 * <p>Rutas (relativas al context-path {@code /api/v1}):</p>
 * <ul>
 *   <li>{@code GET /social/bandeja?canal=&clienteId=&estado=&page=&size=} —
 *       Bandeja_Unificada paginada
 *       ({@code @autorizador.tiene('bandeja','leer')}) (Req 64.5, 64.14).</li>
 *   <li>{@code GET /social/bandeja/{id}} — consulta de Conversacion
 *       ({@code @autorizador.tiene('conversacion','leer')}); 404 si no accesible.</li>
 *   <li>{@code GET /social/bandeja/{id}/mensajes?page=&size=} — historial
 *       ({@code @autorizador.tiene('conversacion','leer')}).</li>
 *   <li>{@code POST /social/bandeja/{id}/mensajes} — envio de Mensaje_Social
 *       ({@code @autorizador.tiene('conversacion','enviar')}); 201; 422 fuera de la
 *       Ventana_Servicio con texto libre (Req 64.7) o marketing sin Opt_In (Req 64.8).</li>
 *   <li>{@code PUT /social/bandeja/{id}/asignacion} — handover
 *       ({@code @autorizador.tiene('conversacion','actualizar')}) (Req 64.10).</li>
 *   <li>{@code PUT /social/bandeja/{id}/cierre} — cierre de Conversacion
 *       ({@code @autorizador.tiene('conversacion','actualizar')}).</li>
 *   <li>{@code POST /social/bandeja/consentimientos} — Opt_In/Opt_Out
 *       ({@code @autorizador.tiene('consentimiento','registrar')}) (Req 64.9).</li>
 * </ul>
 *
 * <p>Los permisos {@code bandeja:{leer,actualizar}} y {@code conversacion:{leer,
 * actualizar}} se sembraron en V5; {@code conversacion:enviar} y
 * {@code consentimiento:registrar} se siembran en V41 (roles {@code marketing} y
 * {@code ventas}).</p>
 */
@RestController
@RequestMapping("/social/bandeja")
public class BandejaController {

    private final ServicioBandeja servicioBandeja;

    public BandejaController(ServicioBandeja servicioBandeja) {
        this.servicioBandeja = servicioBandeja;
    }

    /**
     * Lista la Bandeja_Unificada del tenant de forma paginada (20/100) con filtros
     * opcionales por canal, Cliente y estado (Req 64.5, 64.14).
     *
     * @param canal     etiqueta del canal a filtrar; opcional.
     * @param clienteId Cliente a filtrar; opcional.
     * @param estado    etiqueta de estado a filtrar; opcional.
     * @param page      numero de pagina 0-index; opcional.
     * @param size      tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link ConversacionDto}.
     */
    @GetMapping
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('bandeja','leer')")
    public PaginaResponse<ConversacionDto> listar(
            @RequestParam(name = "canal", required = false) String canal,
            @RequestParam(name = "clienteId", required = false) UUID clienteId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        CanalSocial canalFiltro = ParseoSocial.canalOpcional(canal);
        EstadoConversacion estadoFiltro = ParseoSocial.estadoConversacionOpcional(estado);
        return PaginaResponse.de(
                servicioBandeja.listarBandeja(canalFiltro, clienteId, estadoFiltro, pageable));
    }

    /**
     * Consulta una Conversacion por su identificador (Req 23.3).
     *
     * @param id identificador de la Conversacion.
     * @return 200 OK con el {@link ConversacionDto}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('conversacion','leer')")
    public ResponseEntity<ConversacionDto> consultar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioBandeja.consultar(id));
    }

    /**
     * Historial paginado de mensajes de una Conversacion (Req 64.5).
     *
     * @param id   identificador de la Conversacion.
     * @param page numero de pagina 0-index; opcional.
     * @param size tamano de pagina; opcional (20/100).
     * @return 200 OK con la pagina de {@link MensajeSocialDto}.
     */
    @GetMapping("/{id}/mensajes")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('conversacion','leer')")
    public PaginaResponse<MensajeSocialDto> listarMensajes(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        Pageable pageable = PageRequestFactory.acotando(page, size);
        return PaginaResponse.de(servicioBandeja.listarMensajes(id, pageable));
    }

    /**
     * Envia un Mensaje_Social en la Conversacion aplicando las guardas de
     * Ventana_Servicio (Req 64.7) y Opt_In de marketing (Req 64.8). 201 con el DTO.
     *
     * @param id      identificador de la Conversacion.
     * @param request tipo, contenido y marca de marketing del mensaje.
     * @return 201 Created con el {@link MensajeSocialDto} enviado.
     */
    @PostMapping("/{id}/mensajes")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('conversacion','enviar')")
    public ResponseEntity<MensajeSocialDto> enviar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EnviarMensajeRequest request) {
        TipoMensaje tipo = ParseoSocial.tipoRequerido(request.tipo());
        EnviarMensajeCommand comando =
                new EnviarMensajeCommand(tipo, request.contenido(), request.esMarketing());
        MensajeSocialDto dto = servicioBandeja.enviarMensaje(id, comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Asigna/transfiere la Conversacion a un Usuario (handover, Req 64.10).
     *
     * @param id      identificador de la Conversacion.
     * @param request Usuario responsable.
     * @return 200 OK con el {@link ConversacionDto}.
     */
    @PutMapping("/{id}/asignacion")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('conversacion','actualizar')")
    public ResponseEntity<ConversacionDto> asignar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AsignarConversacionRequest request) {
        return ResponseEntity.ok(servicioBandeja.asignar(id, request.usuarioId()));
    }

    /**
     * Vincula la Conversacion a un Cliente existente del tenant (lead social,
     * Req 64.4, 5.1, 5.2). Valida en el servicio que el Cliente exista en el tenant
     * (404 si no) e invoca el metodo de dominio {@code vincular}.
     *
     * @param id      identificador de la Conversacion.
     * @param request Cliente a vincular (sin credenciales ni datos de otro tenant).
     * @return 200 OK con el {@link ConversacionDto} ya vinculado.
     */
    @PutMapping("/{id}/vinculacion")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('conversacion','actualizar')")
    public ResponseEntity<ConversacionDto> vincular(
            @PathVariable("id") UUID id,
            @RequestBody VincularConversacionRequest request) {
        return ResponseEntity.ok(servicioBandeja.vincular(id, request.clienteId()));
    }

    /**
     * Cierra la Conversacion (Req 64.10).
     *
     * @param id identificador de la Conversacion.
     * @return 200 OK con el {@link ConversacionDto} cerrado.
     */
    @PutMapping("/{id}/cierre")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('conversacion','actualizar')")
    public ResponseEntity<ConversacionDto> cerrar(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(servicioBandeja.cerrar(id));
    }

    /**
     * Registra un Opt_In u Opt_Out del sujeto en un canal (Req 64.9). 201 con el
     * resumen del consentimiento registrado.
     *
     * @param request canal, sujeto, cliente y estado del consentimiento.
     * @return 201 Created con el {@link ConsentimientoRegistradoDto}.
     */
    @PostMapping("/consentimientos")
    @PreAuthorize("@autorizador.moduloHabilitado('redes-sociales') and @autorizador.tiene('consentimiento','registrar')")
    public ResponseEntity<ConsentimientoRegistradoDto> registrarConsentimiento(
            @Valid @RequestBody RegistrarConsentimientoRequest request) {
        CanalSocial canal = ParseoSocial.canalRequerido(request.canal());
        EstadoConsentimiento estado = ParseoSocial.estadoConsentimientoRequerido(request.estado());
        ConsentimientoRegistradoDto dto = servicioBandeja.registrarConsentimiento(
                canal, request.sujetoExterno(), request.clienteId(), estado);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}

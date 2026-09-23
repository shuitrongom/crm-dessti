package com.dessti.crm.social.adapter.in.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dessti.crm.social.adapter.out.meta.FirmaWebhookMeta;
import com.dessti.crm.social.adapter.out.meta.MetaProperties;
import com.dessti.crm.social.application.MensajeEntranteCommand;
import com.dessti.crm.social.application.ServicioBandeja;
import com.dessti.crm.social.domain.CanalSocial;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Controlador REST dedicado a la recepcion de <strong>webhooks</strong> entrantes de
 * Meta (Req 64.3, 64.4, 9; tarea 40.2). Es la unica via de ingreso de eventos de
 * WhatsApp, Messenger e Instagram.
 *
 * <h2>TLS y validacion de firma (Req 9, 64.3)</h2>
 * <p>La terminacion TLS ocurre en el Proxy_Inverso (IIS); las peticiones llegan al
 * backend por HTTP interno con cabeceras {@code X-Forwarded-*} (ver
 * {@code application.yml} y {@code docs/tls-y-proxy-inverso-iis.md}). El controlador
 * <strong>valida la autenticidad del evento</strong> recomputando el HMAC-SHA256 del
 * cuerpo crudo con el <em>app secret</em> (Req 11) y comparandolo con la cabecera
 * {@code X-Hub-Signature-256} (via {@link FirmaWebhookMeta}) <em>antes</em> de
 * procesar. Una firma ausente o invalida se rechaza con 403 sin persistir nada.</p>
 *
 * <h2>Autorizacion</h2>
 * <p>El endpoint de recepcion es <strong>publico</strong> respecto al RBAC de
 * Usuario (lo invoca Meta, no un Usuario autenticado): su autenticacion es la firma
 * HMAC. Se anota con {@code permitAll} y la seguridad efectiva es la validacion de
 * firma. El {@code tenant_id} se deriva de la Cuenta_Canal_Social que recibio el
 * evento, resuelta por el servicio.</p>
 *
 * <p><strong>Nota de integracion (seguimiento):</strong> el alta de las rutas
 * {@code /social/webhooks/**} en la lista de rutas publicas de la cadena de
 * seguridad de plataforma y la resolucion del {@code tenant_id} del webhook (a
 * partir del identificador de la cuenta receptora, fuera del contexto autenticado)
 * son responsabilidad de la plataforma y quedan como trabajo posterior; este modulo
 * no modifica la configuracion de seguridad transversal. La validacion de firma y el
 * mapeo del evento entrante (nucleo verificable de la tarea 40.2/40.6) son
 * independientes de ese cableado.</p>
 *
 * <h2>Handshake de verificacion (GET)</h2>
 * <p>Meta verifica la suscripcion del webhook con un GET que incluye
 * {@code hub.mode=subscribe}, {@code hub.verify_token} y {@code hub.challenge}. Si
 * el token coincide con el configurado ({@code crm.social.verify-token}, Req 11) se
 * devuelve el {@code hub.challenge} en texto plano; en caso contrario 403.</p>
 */
@RestController
@RequestMapping("/social/webhooks")
public class SocialWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SocialWebhookController.class);

    private final ServicioBandeja servicioBandeja;
    private final MetaProperties propiedades;
    private final ObjectMapper objectMapper;

    public SocialWebhookController(ServicioBandeja servicioBandeja, MetaProperties propiedades) {
        this.servicioBandeja = servicioBandeja;
        this.propiedades = propiedades;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handshake de verificacion del webhook de Meta (Req 64.3). Devuelve el
     * {@code hub.challenge} si el {@code hub.verify_token} coincide con el
     * configurado; 403 en caso contrario.
     *
     * @param canal       canal del path (whatsapp/messenger/instagram).
     * @param mode        valor de {@code hub.mode} (debe ser {@code subscribe}).
     * @param verifyToken valor de {@code hub.verify_token}.
     * @param challenge   valor de {@code hub.challenge} a devolver.
     * @return 200 con el challenge, o 403 si el token no coincide.
     */
    @GetMapping("/{canal}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> verificar(
            @PathVariable("canal") String canal,
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        // Valida el canal para rechazar rutas desconocidas (422 -> aqui 403 por ser publico).
        CanalSocial canalSocial = interpretarCanal(canal);
        if (canalSocial == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String tokenEsperado = propiedades.verifyToken();
        boolean ok = "subscribe".equals(mode)
                && tokenEsperado != null && !tokenEsperado.isBlank()
                && tokenEsperado.equals(verifyToken);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    /**
     * Recepcion de un evento entrante de Meta (Req 64.3, 64.4). Valida la firma
     * HMAC del cuerpo crudo antes de procesar; en caso valido persiste el
     * Mensaje_Social entrante y su Conversacion. Devuelve 200 (el servicio de
     * eventos de Meta espera 2xx).
     *
     * @param canal         canal del path (whatsapp/messenger/instagram).
     * @param firma         cabecera {@code X-Hub-Signature-256} con la firma HMAC.
     * @param cuerpo        cuerpo crudo del evento (payload); se firma tal cual.
     * @return 200 si el evento se acepto; 403 si la firma es invalida.
     */
    @PostMapping(path = "/{canal}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> recibir(
            @PathVariable("canal") String canal,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String firma,
            @RequestBody String cuerpo) {
        CanalSocial canalSocial = interpretarCanal(canal);
        if (canalSocial == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Validacion de autenticidad del evento (Req 64.3): firma HMAC-SHA256.
        String appSecret = propiedades.appSecret();
        if (appSecret == null || appSecret.isBlank()
                || !FirmaWebhookMeta.esValida(appSecret, cuerpo, firma)) {
            log.warn("Webhook social rechazado por firma invalida en canal '{}'", canalSocial.valorBd());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        MensajeEntranteCommand comando = parsearEvento(canalSocial, cuerpo);
        if (comando != null) {
            servicioBandeja.recibirMensajeEntrante(comando);
        }
        return ResponseEntity.ok().build();
    }

    private CanalSocial interpretarCanal(String canal) {
        try {
            return CanalSocial.desdeValorBd(canal);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Extrae del payload los campos minimos de un evento entrante (identificador de
     * la cuenta receptora, remitente, contenido, id externo, nombre mostrado). El
     * parseo es tolerante: soporta una forma canonica plana
     * ({@code {"identificadorCuenta","remitente","texto","externoId","nombre"}}) que
     * los adaptadores por canal normalizan; un payload que no la contenga se ignora
     * de forma segura (devuelve {@code null}) tras haberse validado la firma.
     *
     * @param canal  Canal_Social del evento.
     * @param cuerpo cuerpo crudo del evento (ya validado por firma).
     * @return el comando de entrante, o {@code null} si el payload no trae mensaje.
     */
    private MensajeEntranteCommand parsearEvento(CanalSocial canal, String cuerpo) {
        try {
            JsonNode raiz = objectMapper.readTree(cuerpo);
            String identificadorCuenta = texto(raiz, "identificadorCuenta");
            String remitente = texto(raiz, "remitente");
            String contenido = texto(raiz, "texto");
            String externoId = texto(raiz, "externoId");
            String nombre = texto(raiz, "nombre");
            if (remitente == null || remitente.isBlank()
                    || contenido == null || contenido.isBlank()) {
                return null;
            }
            return new MensajeEntranteCommand(
                    canal, identificadorCuenta, remitente, contenido, externoId, nombre);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Payload de webhook social no parseable en canal '{}'", canal.valorBd());
            return null;
        }
    }

    private static String texto(JsonNode raiz, String campo) {
        JsonNode nodo = raiz.get(campo);
        return (nodo == null || nodo.isNull()) ? null : nodo.asText();
    }
}

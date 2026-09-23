package com.dessti.crm.vertical.anuncios.permiso.adapter.out;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dessti.crm.notificaciones.application.NotificacionPort;
import com.dessti.crm.notificaciones.application.SolicitudNotificacion;
import com.dessti.crm.notificaciones.domain.CanalNotificacion;
import com.dessti.crm.notificaciones.domain.TipoEventoNotificacion;
import com.dessti.crm.vertical.anuncios.permiso.application.DestinatarioPermisoPort;
import com.dessti.crm.vertical.anuncios.permiso.application.NotificacionVencimientoPermiso;
import com.dessti.crm.vertical.anuncios.permiso.application.NotificadorPermisoPort;

/**
 * Adaptador de salida <strong>real</strong> del {@link NotificadorPermisoPort} que
 * integra la notificación de vencimiento próximo de un {@code Permiso_Instalacion}
 * con el módulo de notificaciones (Req 13.2, 13.3; §E1, Decisión D7).
 *
 * <p>Delega en {@link NotificacionPort#notificar(SolicitudNotificacion)}, de modo
 * que el aviso deja de ser "sólo log": genera una {@code Notificacion} persistida
 * (consultable en la bandeja del tenant) e integrada con reintentos y auditoría
 * (Req 13.2). El contenido incluye el Sitio, el tipo de permiso, la fecha de
 * vencimiento y los días restantes (Req 13.3).</p>
 *
 * <h2>Resolución de destinatario y canal (§E1)</h2>
 * <ul>
 *   <li><strong>Canal:</strong> {@link CanalNotificacion#CORREO} por defecto. No es
 *       un canal social, por lo que no aplica la guarda de Opt_In de marketing
 *       ({@code esMarketing = false}).</li>
 *   <li><strong>Destinatario:</strong> se resuelve con el {@link DestinatarioPermisoPort}
 *       (correo del responsable del Sitio si existiera; en su defecto, correo de
 *       contacto de la Empresa del tenant). Si no hay destinatario resoluble, se
 *       registra la omisión en el log y <strong>no</strong> se lanza excepción, para
 *       no abortar el barrido de {@code ServicioPermisos.notificarVencimientosProximos()}
 *       (§E1).</li>
 * </ul>
 *
 * <h2>Reemplazo del placeholder</h2>
 * <p>Se registra como bean real ({@code @Component}) del {@link NotificadorPermisoPort}.
 * Como {@code PermisoConfig} declara el placeholder {@code NotificadorPermisoRegistroLog}
 * con {@code @ConditionalOnMissingBean(NotificadorPermisoPort.class)}, la presencia de
 * este adaptador hace que el placeholder <strong>no</strong> se registre, quedando este
 * como la única implementación del puerto en el contexto.</p>
 *
 * <p>El contenido está minimizado (Req 13.3 / Req 46.4): incluye sólo lo necesario
 * para el aviso, sin volcar datos sensibles. El identificador del Sitio se transporta
 * como referencia; el frontend resuelve su nombre para el usuario final.</p>
 */
@Component
public class NotificadorPermisoNotificaciones implements NotificadorPermisoPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPermisoNotificaciones.class);

    /** Recurso de negocio referenciado por la Notificación (coherente con RBAC/auditoría). */
    private static final String RECURSO_PERMISO = "permiso_instalacion";

    /** Asunto minimizado del correo de vencimiento próximo (Req 13.3 / 46.4). */
    private static final String ASUNTO = "Permiso por vencer";

    private final NotificacionPort notificacionPort;
    private final DestinatarioPermisoPort destinatarioPort;

    public NotificadorPermisoNotificaciones(NotificacionPort notificacionPort,
                                            DestinatarioPermisoPort destinatarioPort) {
        this.notificacionPort = notificacionPort;
        this.destinatarioPort = destinatarioPort;
    }

    @Override
    public void notificarVencimientoProximo(NotificacionVencimientoPermiso notificacion) {
        Optional<String> destinatario = destinatarioPort.resolverCorreo(notificacion);
        if (destinatario.isEmpty()) {
            // Sin destinatario resoluble: se registra la omisión y NO se aborta el
            // barrido (§E1). No se lanza excepción.
            log.warn("[PERMISO_POR_VENCER] omitida: sin destinatario resoluble para tenant={} permiso={} sitio={}",
                    notificacion.tenantId(), notificacion.permisoId(), notificacion.sitioId());
            return;
        }

        String contenido = componerContenido(notificacion);
        notificacionPort.notificar(new SolicitudNotificacion(
                TipoEventoNotificacion.PERMISO_POR_VENCER,
                CanalNotificacion.CORREO,
                destinatario.get(),
                ASUNTO,
                contenido,
                false,
                RECURSO_PERMISO,
                notificacion.permisoId()));
    }

    /**
     * Compone el contenido minimizado del aviso incluyendo Sitio, tipo de permiso,
     * fecha de vencimiento y días restantes (Req 13.3). El Sitio se transporta como
     * identificador (referencia); el frontend resuelve su nombre para el usuario
     * final, evitando exponer datos sensibles adicionales (Req 46.4).
     *
     * @param n metadatos del permiso próximo a vencer.
     * @return el contenido del aviso.
     */
    private String componerContenido(NotificacionVencimientoPermiso n) {
        return "Permiso próximo a vencer. Sitio " + n.sitioId()
                + ", tipo " + n.tipo()
                + ", vence " + n.fechaVencimiento()
                + " (" + n.diasParaVencer() + " días).";
    }
}

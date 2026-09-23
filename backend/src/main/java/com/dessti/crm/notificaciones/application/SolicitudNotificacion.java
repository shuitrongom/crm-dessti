package com.dessti.crm.notificaciones.application;

import java.util.UUID;

import com.dessti.crm.notificaciones.domain.CanalNotificacion;
import com.dessti.crm.notificaciones.domain.TipoEventoNotificacion;

/**
 * Solicitud inmutable para generar y enviar una Notificacion ante un evento
 * relevante (Req 46.1). Es el argumento del puerto de entrada
 * {@link NotificacionPort#notificar(SolicitudNotificacion)} que los modulos
 * productores (facturacion, permiso, mantenimiento, etc.) construyen.
 *
 * <h2>Contenido minimo, sin datos sensibles (Req 46.4)</h2>
 * <p>El {@link #asunto()} y el {@link #contenido()} deben venir ya <em>minimizados</em>
 * por el llamador: solo lo necesario para el aviso, sin exponer datos sensibles
 * innecesarios. La {@code ServicioNotificaciones} no depura el contenido.</p>
 *
 * @param eventoOrigen    evento de negocio que origina la Notificacion; obligatorio
 *                        (Req 46.1).
 * @param canalPreferido  canal por el que se desea entregar; obligatorio. Si es un
 *                        Canal_Social, aplican las reglas de Ventana/Plantilla/Opt_In
 *                        (Req 46.6, 46.7).
 * @param destinatario    correo, telefono o identificador social del destinatario;
 *                        obligatorio (Req 46.1).
 * @param asunto          asunto (correo); opcional, ya minimizado (Req 46.4).
 * @param contenido       contenido del aviso; obligatorio, ya minimizado (Req 46.4).
 * @param esMarketing     {@code true} si es una Notificacion de marketing (relevante
 *                        para la guarda de Opt_In en Canal_Social, Req 46.7).
 * @param referenciaTipo  tipo del recurso de negocio referenciado (por ejemplo
 *                        {@code factura}); opcional.
 * @param referenciaId    identificador del recurso de negocio referenciado; opcional.
 */
public record SolicitudNotificacion(
        TipoEventoNotificacion eventoOrigen,
        CanalNotificacion canalPreferido,
        String destinatario,
        String asunto,
        String contenido,
        boolean esMarketing,
        String referenciaTipo,
        UUID referenciaId) {
}

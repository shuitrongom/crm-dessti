package com.dessti.crm.notificaciones.application;

import com.dessti.crm.notificaciones.domain.CanalNotificacion;

/**
 * Puerto de <strong>salida</strong> del modulo notificaciones hacia la integracion
 * con <em>Canales Sociales</em> (WhatsApp/Messenger/Instagram) (Req 46.6). Es el
 * <strong>punto de costura</strong> (seam) que el modulo social del sistema podra
 * implementar mas adelante para reutilizar su integracion; mientras tanto,
 * {@link NotificadorSocialRegistroLog} provee un adaptador por defecto que solo
 * registra en el log.
 *
 * <h2>Independencia de modulos</h2>
 * <p>Este puerto pertenece <strong>exclusivamente</strong> al modulo notificaciones
 * ({@code com.dessti.crm.notificaciones}) y NO importa ni referencia ninguna clase
 * del modulo social. De esta forma, la Notificacion dirigida a un Canal_Social se
 * entrega "reutilizando la integracion" (Req 46.6) a traves de esta frontera
 * estable, sin acoplar los dos modulos: el modulo social podra registrar un bean
 * que implemente este puerto, desactivando el adaptador por defecto
 * ({@code @ConditionalOnMissingBean}).</p>
 *
 * <h2>Semantica del contrato (Req 46.6, 46.7)</h2>
 * <p>El envio por Canal_Social debe respetar, a nivel de contrato:</p>
 * <ul>
 *   <li><strong>Ventana_Servicio y Plantilla_Mensaje (Req 46.6):</strong> dentro de
 *       la ventana de servicio se admite mensaje libre; fuera de la ventana debe
 *       usarse una Plantilla_Mensaje aprobada. La resolucion concreta de la ventana
 *       y la plantilla corresponde a la implementacion real (modulo social); el
 *       adaptador por defecto documenta esta expectativa.</li>
 *   <li><strong>Opt_In (Req 46.7):</strong> la comprobacion de Opt_In vigente para
 *       Notificaciones de marketing la realiza la {@code ServicioNotificaciones}
 *       (via {@link ConsentimientoPort}) ANTES de invocar este puerto, de modo que
 *       aqui solo llegan envios permitidos.</li>
 * </ul>
 */
public interface NotificadorSocialPort {

    /**
     * Intenta entregar una Notificacion por un Canal_Social (Req 46.6).
     *
     * @param canal   Canal_Social destino (WhatsApp/Messenger/Instagram); obligatorio.
     * @param mensaje mensaje minimo a entregar; obligatorio (Req 46.4).
     * @return el resultado del intento (exito, o fallo con motivo sin secretos).
     */
    ResultadoEnvio enviarPorCanalSocial(CanalNotificacion canal, MensajeNotificacion mensaje);
}

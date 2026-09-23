package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.CanalSocial;
import com.dessti.crm.social.domain.EstadoEntrega;

/**
 * Puerto de salida hacia la mensajeria de <strong>Meta</strong> (WhatsApp Business
 * Cloud API, Graph API para Messenger/Instagram) (Req 64, 11). Es la frontera
 * hexagonal que <em>desacopla</em> el modulo {@code social} de la integracion
 * concreta con Meta, de modo que el adaptador real pueda intercambiarse sin tocar
 * la aplicacion. Sigue el patron de {@code PacPort}.
 *
 * <h2>Contrato</h2>
 * <ul>
 *   <li>{@link #enviarTexto(SolicitudEnvioSocial)} envia texto libre; solo debe
 *       invocarse DENTRO de la Ventana_Servicio (la guarda la aplica la aplicacion,
 *       Req 64.6/64.7).</li>
 *   <li>{@link #enviarPlantilla(SolicitudEnvioSocial)} envia una Plantilla_Mensaje
 *       aprobada; via admitida FUERA de la Ventana_Servicio (Req 64.7).</li>
 *   <li>{@link #enviarInteractivo(SolicitudEnvioSocial)} envia un mensaje
 *       interactivo (botones/listas) (Req 64.11).</li>
 *   <li>{@link #consultarEstado(String, CanalSocial)} consulta el estado de entrega
 *       de un mensaje ya enviado por su id externo (Req 64.11).</li>
 * </ul>
 *
 * <h2>Portabilidad, secretos y reintentos (Req 11, 64.13)</h2>
 * <p>La implementacion real (adaptadores HTTP a las APIs de Meta) es trabajo
 * futuro; el {@link com.dessti.crm.social.adapter.out.meta.MetaStubAdapter stub}
 * determinista cubre las pruebas y el arranque sin credenciales. Las credenciales
 * (tokens de acceso de Meta) se resuelven <strong>exclusivamente</strong> desde la
 * gestion de secretos ({@code crm.social.*} sobre variables de entorno, Req 11) a
 * partir de la referencia {@code credencialesRef} de cada cuenta, y
 * <strong>nunca</strong> se embeben en el codigo ni se escriben en logs. La
 * politica de reintentos ante fallo es configurable (Req 64.13) y la orquesta la
 * capa de aplicacion sobre este puerto.</p>
 *
 * <p>El contrato usa <em>records</em> inmutables de solicitud/resultado, sin tipos
 * de persistencia, para mantener el puerto estable y portable.</p>
 */
public interface MensajeriaSocialPort {

    /**
     * Envia un Mensaje_Social de <strong>texto libre</strong> (Req 64.6). Solo debe
     * invocarse dentro de la Ventana_Servicio (guarda aplicada por la aplicacion).
     *
     * @param solicitud datos del envio; obligatorio.
     * @return el resultado del envio: exito con id externo y estado, o fallo con motivo.
     */
    ResultadoEnvioSocial enviarTexto(SolicitudEnvioSocial solicitud);

    /**
     * Envia un Mensaje_Social basado en una <strong>Plantilla_Mensaje</strong>
     * aprobada (Req 64.7). Via admitida fuera de la Ventana_Servicio.
     *
     * @param solicitud datos del envio; obligatorio.
     * @return el resultado del envio.
     */
    ResultadoEnvioSocial enviarPlantilla(SolicitudEnvioSocial solicitud);

    /**
     * Envia un Mensaje_Social <strong>interactivo</strong> (botones/listas)
     * (Req 64.11).
     *
     * @param solicitud datos del envio; obligatorio.
     * @return el resultado del envio.
     */
    ResultadoEnvioSocial enviarInteractivo(SolicitudEnvioSocial solicitud);

    /**
     * Consulta el estado de entrega de un Mensaje_Social ya enviado, por su id
     * externo del proveedor (Req 64.11).
     *
     * @param externoId id del mensaje en el proveedor; obligatorio.
     * @param canal     Canal_Social del mensaje; obligatorio.
     * @return el estado de entrega actual segun el proveedor.
     */
    EstadoEntrega consultarEstado(String externoId, CanalSocial canal);
}

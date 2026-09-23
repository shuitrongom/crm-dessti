package com.dessti.crm.social.application;

import com.dessti.crm.social.domain.CanalSocial;

/**
 * Puerto de salida hacia la publicacion de contenidos y la Marketing API de
 * <strong>Meta</strong> (Graph API para Messenger/Facebook e Instagram) (Req 65,
 * 11). Es la frontera hexagonal que <em>desacopla</em> el modulo {@code social} de
 * la integracion concreta con Meta para publicar Publicacion_Social y consultar el
 * estado de Campaña_Publicitaria. Sigue el patron de {@link MensajeriaSocialPort},
 * pero es un puerto <strong>independiente y focalizado</strong> en la publicacion,
 * de modo que el contrato de la mensajeria del bloque 40 permanece intacto.
 *
 * <h2>Contrato</h2>
 * <ul>
 *   <li>{@link #publicar(SolicitudPublicacion)} publica una Publicacion_Social en su
 *       Canal_Social; devuelve exito con id externo o fallo con motivo, para que la
 *       aplicacion aplique la politica de reintentos (Req 65.5, 65.6).</li>
 *   <li>{@link #consultarEstadoCampana(String, CanalSocial)} consulta el estado de
 *       una Campaña_Publicitaria en la Marketing API, como instantanea de
 *       <strong>SOLO LECTURA</strong> (Req 65.9).</li>
 * </ul>
 *
 * <h2>Portabilidad, secretos y reintentos (Req 11, 65.6)</h2>
 * <p>La implementacion real (adaptadores HTTP a las APIs de Meta) es trabajo futuro;
 * el {@link com.dessti.crm.social.adapter.out.meta.PublicacionSocialStubAdapter stub}
 * determinista cubre las pruebas y el arranque sin credenciales. Las credenciales
 * (tokens de acceso de Meta) se resuelven <strong>exclusivamente</strong> desde la
 * gestion de secretos ({@code crm.social.*}, Req 11) a partir de la referencia
 * {@code credencialesRef} de cada cuenta, y <strong>nunca</strong> se embeben en el
 * codigo ni se escriben en logs. La politica de reintentos ante fallo es
 * configurable (Req 65.6) y la orquesta la capa de aplicacion sobre este puerto.</p>
 *
 * <p>El contrato usa <em>records</em> inmutables de solicitud/resultado, sin tipos
 * de persistencia, para mantener el puerto estable y portable.</p>
 */
public interface PublicacionSocialPort {

    /**
     * Publica una Publicacion_Social en su Canal_Social (Req 65.5). La aplicacion la
     * invoca cuando la publicacion esta {@code programada} y ha llegado su fecha
     * programada, aplicando la politica de reintentos ante fallo (Req 65.6).
     *
     * @param solicitud datos de la publicacion; obligatorio.
     * @return el resultado: exito con id externo, o fallo con motivo.
     */
    ResultadoPublicacion publicar(SolicitudPublicacion solicitud);

    /**
     * Consulta el estado de una Campaña_Publicitaria en la Marketing API de Meta por
     * su id externo, como instantanea de SOLO LECTURA (Req 65.9).
     *
     * @param externoId id de la campaña en el proveedor; obligatorio.
     * @param canal     Canal_Social de la campaña; opcional.
     * @return la instantanea de estado externo (disponible o no disponible).
     */
    EstadoCampanaExterno consultarEstadoCampana(String externoId, CanalSocial canal);
}

package com.dessti.crm.facturacion.application;

/**
 * Puerto de salida hacia el <strong>PAC</strong> (Proveedor Autorizado de
 * Certificacion) que timbra y cancela los CFDI ante el SAT (Req 35). Es la
 * frontera hexagonal que <em>desacopla</em> el dominio de facturacion de la
 * integracion concreta con el PAC (HTTP/SOAP/REST del proveedor), de modo que el
 * adaptador real pueda intercambiarse sin tocar la aplicacion (Req 35.8).
 *
 * <h2>Contrato</h2>
 * <ul>
 *   <li>{@link #timbrar(SolicitudTimbrado)} envia el CFDI al PAC para su Timbrado;
 *       en exito devuelve el {@link ResultadoTimbrado#folioFiscal() Folio_Fiscal}
 *       (UUID del SAT) y el sello; en rechazo devuelve
 *       {@link ResultadoTimbrado#exito() exito=false} y el motivo, sin folio
 *       (Req 35.1, 35.2).</li>
 *   <li>{@link #cancelar(SolicitudCancelacion)} solicita al PAC la cancelacion de
 *       un CFDI timbrado indicando el motivo del catalogo del SAT; devuelve el
 *       acuse o el motivo de rechazo (Req 35.4, 35.5).</li>
 * </ul>
 *
 * <h2>Portabilidad y secretos (Req 35.8, 11)</h2>
 * <p>La implementacion real (adaptador HTTP hacia el PAC) es trabajo futuro; el
 * {@link com.dessti.crm.facturacion.adapter.out.pac.PacStubAdapter stub}
 * determinista cubre las pruebas y el arranque sin credenciales. Las credenciales
 * del PAC (usuario, contrasena, URL) se resuelven <strong>exclusivamente</strong>
 * desde la gestion de secretos ({@code crm.pac.*} sobre variables de entorno,
 * Req 11) y <strong>nunca</strong> se embeben en el codigo ni se escriben en logs.</p>
 *
 * <p>El contrato usa <em>records</em> inmutables de solicitud/resultado, sin tipos
 * de dominio ni de persistencia, para mantener el puerto estable y portable.</p>
 */
public interface PacPort {

    /**
     * Solicita al PAC el Timbrado de un CFDI (Req 35.1, 35.2).
     *
     * @param solicitud datos del CFDI a timbrar; obligatorio.
     * @return el resultado del Timbrado: exito con Folio_Fiscal y sello, o rechazo
     *         con el motivo devuelto por el PAC.
     */
    ResultadoTimbrado timbrar(SolicitudTimbrado solicitud);

    /**
     * Solicita al PAC la cancelacion de un CFDI timbrado (Req 35.4, 35.5).
     *
     * @param solicitud datos de la cancelacion (Folio_Fiscal y motivo SAT);
     *                  obligatorio.
     * @return el resultado de la cancelacion: exito con acuse, o rechazo con el
     *         motivo devuelto por el PAC.
     */
    ResultadoCancelacion cancelar(SolicitudCancelacion solicitud);
}

package com.dessti.crm.portalcliente.application;

/**
 * Resultado de rechazar una Prueba_Diseno desde el Portal (Req 15.3): la version
 * que quedo {@code rechazada} y la <strong>nueva</strong> version {@code pendiente}
 * generada automaticamente. Es la forma que el Portal (Nucleo) publica en su
 * {@link ResumenPruebasDisenoPort}, desacoplada del record interno del vertical
 * (Req 10.5); reproduce su misma forma para preservar el contrato REST del Portal
 * (Req 10.4).
 *
 * @param rechazada    la Prueba_Diseno que se rechazo (estado {@code rechazada}).
 * @param nuevaVersion la nueva Prueba_Diseno generada (estado {@code pendiente},
 *                     {@code numeroVersion} = anterior + 1).
 */
public record ResultadoRechazoPruebaResumen(
        PruebaDisenoResumen rechazada,
        PruebaDisenoResumen nuevaVersion) {
}

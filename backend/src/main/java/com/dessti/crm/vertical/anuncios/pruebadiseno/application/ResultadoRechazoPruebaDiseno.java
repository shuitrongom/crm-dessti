package com.dessti.crm.vertical.anuncios.pruebadiseno.application;

/**
 * Resultado de rechazar una Prueba_Diseno (Req 15.3, Property 8): la version que
 * quedo {@code rechazada} y la <strong>nueva</strong> version {@code pendiente}
 * generada automaticamente con numero de version incrementado en 1.
 *
 * @param rechazada    la Prueba_Diseno que se rechazo (estado {@code rechazada}).
 * @param nuevaVersion la nueva Prueba_Diseno generada (estado {@code pendiente},
 *                     {@code numeroVersion} = anterior + 1).
 */
public record ResultadoRechazoPruebaDiseno(
        PruebaDisenoDto rechazada,
        PruebaDisenoDto nuevaVersion) {
}

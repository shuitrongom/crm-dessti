package com.dessti.crm.platform.empresas.rest;

import java.time.LocalDate;

/**
 * Cuerpo <strong>opcional</strong> de la peticion para activar la facturacion de
 * un Contrato en periodo de prueba (transicion {@code EN_PRUEBA} &rarr;
 * {@code ACTIVA}; Req 8.1, 8.4).
 *
 * <p>Ambos campos son opcionales: si se omiten (o si el cuerpo completo no se
 * envia), el servicio calcula los valores por omision (inicio de facturacion =
 * hoy y nuevo fin de vigencia derivado de la duracion del paquete).</p>
 *
 * @param inicioFacturacion fecha de inicio de la facturacion; opcional
 *                          ({@code null} = el servicio usa el valor por omision).
 * @param nuevaVigenciaFin  nuevo fin de vigencia del Contrato ya activo;
 *                          opcional ({@code null} = el servicio lo deriva del
 *                          paquete).
 */
public record ActivarFacturacionRequest(
        LocalDate inicioFacturacion,
        LocalDate nuevaVigenciaFin) {
}

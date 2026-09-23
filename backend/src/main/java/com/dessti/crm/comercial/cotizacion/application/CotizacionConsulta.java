package com.dessti.crm.comercial.cotizacion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vista de consulta minima de una {@code Cotizacion} publicada por el Nucleo a
 * traves del {@link CotizacionConsultaPort}, para que los Modulos-Vertical la
 * consuman sin depender de la entidad JPA {@code Cotizacion} ni de su persistencia
 * (Req 4.5, 10.3). Objeto de solo lectura, libre de dependencias de persistencia.
 *
 * <p>Expone exactamente lo que los flujos del vertical necesitan hoy de una
 * Cotizacion: su identificador, la <strong>etiqueta</strong> de su estado de
 * negocio ({@code borrador}/{@code enviada}/{@code aprobada}/{@code rechazada},
 * Req 6.6) —para que Orden_Fabricacion valide que este {@code aprobada}—, el
 * Cliente al que pertenece (para heredarlo, Req 7.2) y su importe {@code total}
 * (base de facturacion, Req 34.1).</p>
 *
 * @param id        identificador de la Cotizacion.
 * @param estado    etiqueta del estado de negocio de la Cotizacion (Req 6.6).
 * @param clienteId Cliente al que pertenece la Cotizacion (Req 6.1).
 * @param total     importe total de la Cotizacion (escala 2, Req 6.5).
 */
public record CotizacionConsulta(
        UUID id,
        String estado,
        UUID clienteId,
        BigDecimal total) {
}

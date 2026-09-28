package com.dessti.crm.portalcliente.application;

import java.util.UUID;

import com.dessti.crm.comercial.cotizacion.application.CotizacionDto;

/**
 * Puerto de salida que el <strong>Portal del Cliente</strong> define para permitir
 * que un Cliente <em>apruebe o rechace</em> una de sus Cotizaciones enviadas
 * (Req 45.2, 6.6). Lo implementa el Nucleo comercial (Cotizaciones), invirtiendo la
 * dependencia: el Portal no conoce la persistencia ni la maquina de estados de la
 * Cotizacion, solo expresa la decision del Cliente.
 *
 * <p>La transicion la valida la maquina de estados pura de la Cotizacion
 * ({@code enviada -> aprobada|rechazada}, Req 6.6): una Cotizacion que no este
 * {@code enviada} produce 409 (transicion invalida). La <strong>guarda de
 * propiedad</strong> (que la Cotizacion pertenezca al Cliente del Portal) la aplica
 * {@link ServicioPortalCliente} antes de invocar este puerto, respondiendo 404 si no
 * es del Cliente para no revelar recursos ajenos.</p>
 */
public interface DecisionCotizacionPort {

    /**
     * Aprueba una Cotizacion (transicion {@code enviada -> aprobada}, Req 6.6).
     *
     * @param cotizacionId identificador de la Cotizacion; debe estar {@code enviada}.
     * @return el DTO de la Cotizacion aprobada.
     */
    CotizacionDto aprobar(UUID cotizacionId);

    /**
     * Rechaza una Cotizacion (transicion {@code enviada -> rechazada}, Req 6.6).
     *
     * @param cotizacionId identificador de la Cotizacion; debe estar {@code enviada}.
     * @return el DTO de la Cotizacion rechazada.
     */
    CotizacionDto rechazar(UUID cotizacionId);
}

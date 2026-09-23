package com.dessti.crm.vertical.anuncios.levantamiento.application;

import java.util.UUID;

/**
 * Comando de creacion de un
 * {@link com.dessti.crm.vertical.anuncios.levantamiento.domain.LevantamientoSitio}
 * (Req 16.1, 16.2). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> este comando NO incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado y no se acepta
 * como parametro manipulable de la peticion.</p>
 *
 * @param mediciones            mediciones del sitio; obligatorio (Req 16.1).
 * @param tipoSuperficie        tipo de superficie o estructura; obligatorio (Req 16.1).
 * @param condicionesElectricas condiciones electricas; obligatorio (Req 16.1).
 * @param sitioId               Sitio vinculado; opcional (Req 16.2).
 * @param cotizacionId          Cotizacion vinculada; opcional (Req 16.2), debe existir.
 * @param ordenFabricacionId    Orden_Fabricacion vinculada; opcional (Req 16.2), debe existir.
 */
public record CrearLevantamientoCommand(
        String mediciones,
        String tipoSuperficie,
        String condicionesElectricas,
        UUID sitioId,
        UUID cotizacionId,
        UUID ordenFabricacionId) {
}

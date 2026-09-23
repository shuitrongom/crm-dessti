package com.dessti.crm.vertical.anuncios.levantamiento.adapter.in.rest;

import java.util.UUID;

import com.dessti.crm.vertical.anuncios.levantamiento.application.CrearLevantamientoCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear un Levantamiento_Sitio (Req 16.1, 16.2). DTO de
 * entrada del contrato REST, distinto de la entidad JPA. Los datos obligatorios se
 * validan con Bean Validation; los vinculos son opcionales y su existencia en el
 * tenant la verifica la capa de aplicacion (404 si no).
 *
 * @param mediciones            mediciones del sitio; obligatorio (Req 16.1).
 * @param tipoSuperficie        tipo de superficie o estructura; obligatorio (1..200, Req 16.1).
 * @param condicionesElectricas condiciones electricas; obligatorio (Req 16.1).
 * @param sitioId               Sitio vinculado; opcional (Req 16.2).
 * @param cotizacionId          Cotizacion vinculada; opcional (Req 16.2).
 * @param ordenFabricacionId    Orden_Fabricacion vinculada; opcional (Req 16.2).
 */
public record CrearLevantamientoRequest(
        @NotBlank String mediciones,
        @NotBlank @Size(max = 200) String tipoSuperficie,
        @NotBlank String condicionesElectricas,
        UUID sitioId,
        UUID cotizacionId,
        UUID ordenFabricacionId) {

    /**
     * Traduce la peticion REST al comando de aplicacion (Req 12.2).
     *
     * @return el {@link CrearLevantamientoCommand} equivalente.
     */
    public CrearLevantamientoCommand aComando() {
        return new CrearLevantamientoCommand(
                mediciones, tipoSuperficie, condicionesElectricas,
                sitioId, cotizacionId, ordenFabricacionId);
    }
}

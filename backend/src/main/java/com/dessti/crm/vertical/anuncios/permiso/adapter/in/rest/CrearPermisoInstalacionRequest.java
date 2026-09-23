package com.dessti.crm.vertical.anuncios.permiso.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.permiso.application.CrearPermisoInstalacionCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para crear un Permiso_Instalacion (Req 17.1). DTO de entrada
 * del contrato REST, distinto de la entidad JPA. Los datos obligatorios se validan
 * con Bean Validation; la etiqueta de tipo la interpreta la capa de aplicacion
 * (422 si es desconocida).
 *
 * @param tipo             etiqueta del tipo ({@code municipal}/{@code arrendador});
 *                         obligatorio (Req 17.1).
 * @param fechaVencimiento fecha de vencimiento; obligatoria (Req 17.1).
 * @param sitioId          Sitio vinculado; obligatorio (Req 17.1).
 */
public record CrearPermisoInstalacionRequest(
        @NotBlank String tipo,
        @NotNull LocalDate fechaVencimiento,
        @NotNull UUID sitioId) {

    /**
     * Traduce la peticion REST al comando de aplicacion (Req 12.2).
     *
     * @return el {@link CrearPermisoInstalacionCommand} equivalente.
     */
    public CrearPermisoInstalacionCommand aComando() {
        return new CrearPermisoInstalacionCommand(tipo, fechaVencimiento, sitioId);
    }
}

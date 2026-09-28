package com.dessti.crm.portalcliente.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para que un Cliente levante una Queja_Cliente desde el Portal
 * (Req 45.2, 70.1). El {@code clienteId} y el origen ({@code portal}) NO forman parte
 * del cuerpo: el Portal los resuelve del usuario autenticado, nunca de la peticion
 * (Req 23.4, 45.3).
 *
 * @param descripcion descripcion de la queja; obligatoria y no vacia (max 2000).
 */
public record RegistrarQuejaPortalRequest(
        @NotBlank @Size(max = 2000) String descripcion) {
}

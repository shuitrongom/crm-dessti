package com.dessti.crm.vertical.anuncios.levantamiento.adapter.in.rest;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * Cuerpo de la peticion para adjuntar fotografias a un Levantamiento_Sitio
 * (Req 16.3). DTO de entrada del contrato REST. Cada referencia es una URL o clave
 * de objeto de la fotografia en el almacen; no se envia el binario en este
 * endpoint.
 *
 * @param referencias URLs o claves de las fotografias a adjuntar; obligatorio y
 *                    no vacio (Req 16.3).
 */
public record AgregarFotosLevantamientoRequest(
        @NotEmpty List<@jakarta.validation.constraints.NotBlank String> referencias) {
}

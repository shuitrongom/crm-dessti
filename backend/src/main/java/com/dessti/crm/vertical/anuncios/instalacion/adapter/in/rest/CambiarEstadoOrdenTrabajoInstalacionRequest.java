package com.dessti.crm.vertical.anuncios.instalacion.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la peticion para cambiar el estado de una Orden_Trabajo_Instalacion
 * (Req 19.5). DTO de entrada del contrato REST. La etiqueta se valida y traduce a
 * {@link com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion}
 * en la capa de aplicacion; una transicion no permitida se rechaza con 409
 * (Req 19.5) y un intento de completar con pendientes sin resolver con 422
 * (Req 19.6).
 *
 * @param estado etiqueta del estado destino ({@code programada}, {@code en_curso},
 *               {@code completada}, {@code cancelada}); obligatoria.
 */
public record CambiarEstadoOrdenTrabajoInstalacionRequest(@NotBlank String estado) {
}

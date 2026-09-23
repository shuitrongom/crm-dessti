package com.dessti.crm.platform.giros.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para EDITAR un Giro (bugfix edicion de Giro):
 * {@code PUT /plataforma/giros/{id}}.
 *
 * <p>Solo permite editar los datos NO identificadores del Giro: el
 * {@code nombreVisible} (obligatorio, max 150) y la {@code descripcion}
 * (opcional). La {@code clave} canonica es INMUTABLE y por eso no forma parte
 * de este cuerpo. La normalizacion/validacion del nombre visible la aplica el
 * dominio ({@code Giro.actualizar}).</p>
 *
 * @param nombreVisible nuevo nombre visible del Giro; obligatorio (max 150).
 * @param descripcion   nueva descripcion del vertical; opcional.
 */
public record ActualizarGiroRequest(
        @NotBlank @Size(max = 150) String nombreVisible,
        String descripcion) {
}

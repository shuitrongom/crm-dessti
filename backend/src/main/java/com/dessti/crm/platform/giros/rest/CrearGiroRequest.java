package com.dessti.crm.platform.giros.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Giro (Req 1.1, tarea 2.6).
 *
 * <p>Contiene los datos exigidos por el alta de un Giro. La {@code clave} y el
 * {@code nombreVisible} son obligatorios; la {@code descripcion} es opcional. La
 * validacion de formato/normalizacion canonica de la clave (minusculas, kebab)
 * la aplica el dominio ({@code Giro.normalizarClave}); aqui solo se validan la
 * presencia y las longitudes maximas, alineadas con la tabla {@code giro} de la
 * migracion V50 (clave 60, nombre visible 150), replicando el estilo de
 * {@code platform.empresas.rest.CrearEmpresaRequest} (Bean Validation con
 * {@link NotBlank}/{@link Size}).</p>
 *
 * @param clave         clave canonica del vertical (p. ej.
 *                      {@code anuncios-luminosos}); obligatoria (max 60). Se
 *                      normaliza en el dominio.
 * @param nombreVisible nombre visible del Giro; obligatorio (max 150).
 * @param descripcion   descripcion del vertical; opcional.
 */
public record CrearGiroRequest(
        @NotBlank @Size(max = 60) String clave,
        @NotBlank @Size(max = 150) String nombreVisible,
        String descripcion) {
}

package com.dessti.crm.vertical.anuncios.permiso.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de creacion de un
 * {@link com.dessti.crm.vertical.anuncios.permiso.domain.PermisoInstalacion} (Req 17.1).
 * Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> este comando NO incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado y no se acepta
 * como parametro manipulable de la peticion.</p>
 *
 * @param tipo             etiqueta del tipo ({@code municipal}/{@code arrendador});
 *                         obligatorio (Req 17.1).
 * @param fechaVencimiento fecha de vencimiento; obligatoria (Req 17.1).
 * @param sitioId          Sitio vinculado; obligatorio (Req 17.1), debe existir.
 */
public record CrearPermisoInstalacionCommand(
        String tipo,
        LocalDate fechaVencimiento,
        UUID sitioId) {
}

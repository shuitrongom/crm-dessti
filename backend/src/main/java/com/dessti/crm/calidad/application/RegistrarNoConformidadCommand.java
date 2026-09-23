package com.dessti.crm.calidad.application;

import com.dessti.crm.calidad.domain.OrigenNoConformidad;

/**
 * Comando de aplicacion para registrar una
 * {@link com.dessti.crm.calidad.domain.NoConformidad} (Req 70.2).
 *
 * @param origen          origen de la No_Conformidad; obligatorio.
 * @param descripcion     descripcion; obligatoria.
 * @param procesoAfectado proceso afectado; obligatorio.
 */
public record RegistrarNoConformidadCommand(
        OrigenNoConformidad origen,
        String descripcion,
        String procesoAfectado) {
}

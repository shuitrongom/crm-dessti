package com.dessti.crm.reportesbi.application;

import java.util.UUID;

import com.dessti.crm.reportesbi.domain.WidgetTablero;

/**
 * DTO de salida de un {@link WidgetTablero} (Req 48.3), distinto de la entidad JPA.
 *
 * @param id                 identificador del widget.
 * @param area               etiqueta del area de la metrica.
 * @param metrica            clave de la metrica.
 * @param orden              orden de presentacion dentro del tablero.
 * @param configuracionJson  parametros de presentacion como JSON opaco; puede ser {@code null}.
 */
public record WidgetTableroDto(
        UUID id,
        String area,
        String metrica,
        int orden,
        String configuracionJson) {

    /**
     * Proyecta una entidad {@link WidgetTablero} a su DTO de salida.
     *
     * @param widget entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static WidgetTableroDto de(WidgetTablero widget) {
        return new WidgetTableroDto(widget.getId(), widget.getArea(), widget.getMetrica(),
                widget.getOrden(), widget.getConfiguracionJson());
    }
}

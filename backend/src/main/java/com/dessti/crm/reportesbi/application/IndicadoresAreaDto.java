package com.dessti.crm.reportesbi.application;

import java.util.List;

import com.dessti.crm.reportesbi.application.indicadores.IndicadoresArea;

/**
 * DTO de salida de los indicadores de un area (Req 22.1, 48.1), distinto del objeto de
 * dominio {@link IndicadoresArea}. Agrupa la etiqueta del area y la lista de sus
 * {@link IndicadorDto indicadores}.
 *
 * @param area        etiqueta ASCII estable del area (AreaIndicador.etiqueta()).
 * @param indicadores indicadores agregados del area; puede estar vacia.
 */
public record IndicadoresAreaDto(String area, List<IndicadorDto> indicadores) {

    /**
     * Proyecta un {@link IndicadoresArea} de dominio a su DTO.
     *
     * @param indicadoresArea indicadores de dominio de un area.
     * @return el DTO correspondiente.
     */
    public static IndicadoresAreaDto de(IndicadoresArea indicadoresArea) {
        List<IndicadorDto> dtos = indicadoresArea.indicadores().stream()
                .map(IndicadorDto::de)
                .toList();
        return new IndicadoresAreaDto(indicadoresArea.area().etiqueta(), dtos);
    }
}

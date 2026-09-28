package com.dessti.crm.operacion.proyecto.application;

import com.dessti.crm.operacion.proyecto.domain.AvanceSitio;
import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;
import com.dessti.crm.operacion.proyecto.domain.Sitio;

/**
 * DTO de salida que combina un {@link Sitio} con su {@link FaseSitioGenerica} para
 * Proyectos multi-sitio de giro generico (Req 3.2, 21.4). Es el analogo generico de
 * {@link SitioAvanceDto}: en lugar de las cuatro banderas de anuncios, expone la fase
 * operativa editable del Sitio (pendiente/en_preparacion/en_instalacion/entregado).
 *
 * @param sitio        datos del Sitio (Req 21.2).
 * @param fase         etiqueta de la fase operativa actual del Sitio.
 * @param nota         nota opcional del avance; {@code null} si no se registro.
 * @param evidenciaUrl referencia a la evidencia que respalda la fase; {@code null} si no hay.
 */
public record SitioFaseDto(
        SitioDto sitio,
        String fase,
        String nota,
        String evidenciaUrl) {

    /**
     * Combina el Sitio con su fase materializada. Un Sitio sin fila de avance se
     * proyecta como {@link FaseSitioGenerica#PENDIENTE} (default logico).
     *
     * @param sitio  entidad del Sitio.
     * @param avance avance materializado del Sitio; {@code null} si aun no existe.
     * @return el DTO combinado.
     */
    public static SitioFaseDto de(Sitio sitio, AvanceSitio avance) {
        FaseSitioGenerica fase = (avance == null)
                ? FaseSitioGenerica.PENDIENTE
                : avance.getFase();
        String nota = (avance == null) ? null : avance.getNota();
        String evidencia = (avance == null) ? null : avance.getEvidenciaUrl();
        return new SitioFaseDto(SitioDto.de(sitio), fase.valorBd(), nota, evidencia);
    }
}

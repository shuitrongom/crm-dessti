package com.dessti.crm.estrategia.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dessti.crm.estrategia.domain.ResultadoClave;

/**
 * DTO de salida de un {@link ResultadoClave} (metrica ponderada de un objetivo;
 * Req 58.8), distinto de la entidad de persistencia.
 *
 * @param id            identificador del resultado clave.
 * @param descripcion   descripcion de la metrica (Req 58.8).
 * @param valorObjetivo valor objetivo (meta medible), escala 4 (Req 58.8).
 * @param valorActual   valor actual medido, escala 4 (Req 58.8).
 * @param peso          peso relativo en la ponderacion, escala 2 (Req 58.8).
 */
public record ResultadoClaveDto(
        UUID id,
        String descripcion,
        BigDecimal valorObjetivo,
        BigDecimal valorActual,
        BigDecimal peso) {

    /**
     * Proyecta una entidad {@link ResultadoClave} a su DTO de salida.
     *
     * @param rc entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ResultadoClaveDto de(ResultadoClave rc) {
        return new ResultadoClaveDto(
                rc.getId(),
                rc.getDescripcion(),
                rc.getValorObjetivo(),
                rc.getValorActual(),
                rc.getPeso());
    }
}

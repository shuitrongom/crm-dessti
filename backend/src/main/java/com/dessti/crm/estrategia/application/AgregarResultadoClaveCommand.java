package com.dessti.crm.estrategia.application;

import java.math.BigDecimal;

/**
 * Comando de aplicacion para agregar un
 * {@link com.dessti.crm.estrategia.domain.ResultadoClave} a un objetivo (Req 58.8).
 * La validacion de campos la aplica el dominio ({@code ResultadoClave.crear}).
 *
 * @param descripcion   descripcion de la metrica; obligatoria (Req 58.8).
 * @param valorObjetivo valor objetivo (meta medible); estrictamente positivo (Req 58.8).
 * @param valorActual   valor actual medido; no negativo (Req 58.8).
 * @param peso          peso relativo en la ponderacion; en (0, 100] (Req 58.8).
 */
public record AgregarResultadoClaveCommand(
        String descripcion,
        BigDecimal valorObjetivo,
        BigDecimal valorActual,
        BigDecimal peso) {
}

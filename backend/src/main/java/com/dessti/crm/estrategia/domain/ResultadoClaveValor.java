package com.dessti.crm.estrategia.domain;

import java.math.BigDecimal;

/**
 * Objeto de valor <strong>puro</strong> del dominio que representa la contribucion
 * de un resultado clave al avance de un {@link ObjetivoEstrategico}: su valor
 * actual, su valor objetivo (meta medible) y su peso relativo (Req 58.8). Es
 * inmutable y no depende de Spring ni de JPA, por lo que la funcion pura de calculo
 * del avance ponderado ({@link CalculoAvanceObjetivo}) lo usa directamente y las
 * pruebas de propiedades (tarea 37.2) pueden ejercerlo sin infraestructura.
 *
 * <p>Se ubica en el <strong>dominio</strong> (y no en la capa de aplicacion)
 * porque es un concepto de negocio puro que la funcion de agregacion del dominio
 * necesita, respetando la regla de dependencias hexagonal (aplicacion y adaptadores
 * dependen del dominio, nunca al reves). La capa de aplicacion proyecta cada
 * {@link ResultadoClave} persistido a este objeto de valor para calcular el avance.</p>
 *
 * @param valorActual   valor actual medido de la metrica; no negativo (Req 58.8).
 * @param valorObjetivo valor objetivo (meta medible) de la metrica (Req 58.8).
 * @param peso          peso relativo del resultado clave en la ponderacion (Req 58.8).
 */
public record ResultadoClaveValor(
        BigDecimal valorActual,
        BigDecimal valorObjetivo,
        BigDecimal peso) {
}

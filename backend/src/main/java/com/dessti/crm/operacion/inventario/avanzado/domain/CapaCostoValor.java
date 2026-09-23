package com.dessti.crm.operacion.inventario.avanzado.domain;

import java.math.BigDecimal;

/**
 * Value object INMUTABLE y PURO de una capa de costo PEPS (FIFO) usado por el motor de
 * costeo {@link MotorCosteo} (Req 60, tarea 23.2). Representa la cantidad aun disponible
 * de una entrada y su costo unitario historico, SIN acoplamiento a JPA ni a Spring, de
 * modo que la matematica del costeo (promedio ponderado y consumo PEPS) sea testeable de
 * forma directa (Property 33: recosteo correcto segun metodo).
 *
 * <p>Es DISTINTO de la entidad JPA {@link CapaCosto}: aquella persiste la capa en la
 * tabla {@code capa_costo} de V26; este record solo transporta los dos valores que el
 * motor necesita para calcular. El servicio de aplicacion reconcilia las filas JPA con
 * la lista de {@code CapaCostoValor} que devuelve el motor.</p>
 *
 * <p><strong>Escalas (coherentes con V26):</strong> las cantidades se manejan a escala 3
 * ({@code NUMERIC(18,3)}) y los costos a escala 4 ({@code NUMERIC(18,4)}), con redondeo
 * {@code HALF_UP} aplicado por {@link MotorCosteo}.</p>
 *
 * @param cantidadRestante cantidad aun disponible en la capa; no negativa.
 * @param costoUnitario    costo unitario historico de la capa; no negativo.
 */
public record CapaCostoValor(BigDecimal cantidadRestante, BigDecimal costoUnitario) {
}

package com.dessti.crm.comercial.producto.application;

import java.time.LocalDate;

/**
 * Comando de creacion/definicion de una
 * {@link com.dessti.crm.comercial.producto.domain.ListaPrecios} (Req 59.3, 59.9).
 * Distinto de la entidad; no incluye el {@code tenant_id} (Req 23.4).
 *
 * @param nombre          nombre de la lista; obligatorio (1..200).
 * @param prioridad       prioridad de aplicacion (mayor = antes, Req 59.9).
 * @param segmento        segmento de Cliente; opcional ({@code null} = general).
 * @param vigenciaInicio  inicio de la vigencia; obligatorio.
 * @param vigenciaFin     fin de la vigencia; opcional (>= inicio si se indica).
 */
public record DefinirListaPreciosCommand(
        String nombre,
        int prioridad,
        String segmento,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFin) {
}

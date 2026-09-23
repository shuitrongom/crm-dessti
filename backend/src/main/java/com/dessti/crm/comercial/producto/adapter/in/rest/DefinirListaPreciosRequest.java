package com.dessti.crm.comercial.producto.adapter.in.rest;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para definir/actualizar una Lista_Precios (Req 59.3,
 * 59.9). DTO de entrada del contrato REST, distinto de la entidad y del comando
 * {@link com.dessti.crm.comercial.producto.application.DefinirListaPreciosCommand}
 * (Req 12.2). El {@code tenant_id} se deriva del contexto (Req 23.4).
 *
 * <p>La coherencia de la vigencia ({@code vigenciaFin >= vigenciaInicio}) la
 * valida el dominio (422).</p>
 *
 * @param nombre          nombre de la lista; obligatorio (1..200).
 * @param prioridad       prioridad de aplicacion (mayor = antes, Req 59.9).
 * @param segmento        segmento de Cliente; opcional ({@code null} = general).
 * @param vigenciaInicio  inicio de la vigencia; obligatorio.
 * @param vigenciaFin     fin de la vigencia; opcional.
 */
public record DefinirListaPreciosRequest(
        @NotBlank @Size(max = 200) String nombre,
        int prioridad,
        @Size(max = 100) String segmento,
        @NotNull LocalDate vigenciaInicio,
        LocalDate vigenciaFin) {
}

package com.dessti.crm.estrategia.application;

import java.time.LocalDate;

/**
 * Comando de aplicacion para crear un {@link com.dessti.crm.estrategia.domain.ObjetivoEstrategico}
 * (Req 58.2). Objeto de transferencia interno de la capa de aplicacion, distinto del
 * DTO de entrada REST y de la entidad JPA. La validacion de campos obligatorios la
 * aplica el dominio ({@code ObjetivoEstrategico.crear}, Req 58.3).
 *
 * @param nombre        nombre del objetivo; obligatorio (Req 58.2, 58.3).
 * @param responsable   responsable del objetivo; obligatorio (Req 58.2, 58.3).
 * @param periodoInicio inicio del periodo; obligatorio (Req 58.2, 58.3).
 * @param periodoFin    fin del periodo; obligatorio (Req 58.2, 58.3).
 * @param meta          meta medible; obligatoria (Req 58.2, 58.3).
 */
public record CrearObjetivoCommand(
        String nombre,
        String responsable,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        String meta) {
}

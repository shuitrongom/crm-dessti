package com.dessti.crm.estrategia.adapter.in.rest;

import java.time.LocalDate;

import com.dessti.crm.estrategia.application.CrearObjetivoCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear un Objetivo_Estrategico (Req 58.2, 58.3). DTO de
 * entrada del contrato REST, distinto de la entidad JPA. La validacion sintactica
 * (obligatoriedad y longitud) la aplica jakarta.validation; la validacion de negocio
 * (campo faltante nombrado, Req 58.3; periodo coherente) la aplica el dominio.
 *
 * @param nombre        nombre del objetivo; obligatorio (Req 58.2, 58.3).
 * @param responsable   responsable del objetivo; obligatorio (Req 58.2, 58.3).
 * @param periodoInicio inicio del periodo; obligatorio (Req 58.2, 58.3).
 * @param periodoFin    fin del periodo; obligatorio (Req 58.2, 58.3).
 * @param meta          meta medible; obligatoria (Req 58.2, 58.3).
 */
public record CrearObjetivoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 200) String responsable,
        @NotNull LocalDate periodoInicio,
        @NotNull LocalDate periodoFin,
        @NotBlank @Size(max = 500) String meta) {

    /**
     * Traduce la peticion REST al comando de aplicacion.
     *
     * @return el {@link CrearObjetivoCommand} equivalente.
     */
    public CrearObjetivoCommand aComando() {
        return new CrearObjetivoCommand(nombre, responsable, periodoInicio, periodoFin, meta);
    }
}

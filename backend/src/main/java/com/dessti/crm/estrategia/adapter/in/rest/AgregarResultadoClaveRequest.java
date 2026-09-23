package com.dessti.crm.estrategia.adapter.in.rest;

import java.math.BigDecimal;

import com.dessti.crm.estrategia.application.AgregarResultadoClaveCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para agregar un resultado clave ponderado a un objetivo
 * (Req 58.8). DTO de entrada del contrato REST, distinto de la entidad JPA. La
 * validacion de rangos (valor objetivo &gt; 0, valor actual &gt;= 0, peso en (0,100])
 * la aplica el dominio.
 *
 * @param descripcion   descripcion de la metrica; obligatoria (Req 58.8).
 * @param valorObjetivo valor objetivo (meta medible); obligatorio (Req 58.8).
 * @param valorActual   valor actual medido; obligatorio (Req 58.8).
 * @param peso          peso relativo en la ponderacion; obligatorio (Req 58.8).
 */
public record AgregarResultadoClaveRequest(
        @NotBlank @Size(max = 300) String descripcion,
        @NotNull BigDecimal valorObjetivo,
        @NotNull BigDecimal valorActual,
        @NotNull BigDecimal peso) {

    /**
     * Traduce la peticion REST al comando de aplicacion.
     *
     * @return el {@link AgregarResultadoClaveCommand} equivalente.
     */
    public AgregarResultadoClaveCommand aComando() {
        return new AgregarResultadoClaveCommand(descripcion, valorObjetivo, valorActual, peso);
    }
}

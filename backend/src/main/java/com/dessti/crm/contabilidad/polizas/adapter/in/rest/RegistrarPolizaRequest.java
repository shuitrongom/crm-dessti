package com.dessti.crm.contabilidad.polizas.adapter.in.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.contabilidad.polizas.domain.TipoPoliza;
import com.dessti.crm.platform.error.ReglaNegocioException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para registrar una Poliza_Contable balanceada (Req 38.2,
 * 38.3, 38.4). DTO de entrada del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.contabilidad.polizas.application.RegistrarPolizaCommand}
 * (Req 12.2). El {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param fecha     fecha contable; obligatoria.
 * @param tipo      tipo de poliza (ingreso, egreso, diario); obligatorio.
 * @param concepto  concepto descriptivo; obligatorio y no vacio.
 * @param origen    evento contable de origen (Req 38.2); opcional.
 * @param origenId  identificador del recurso de origen; opcional.
 * @param renglones renglones de cargo/abono; obligatorio, no vacio y balanceado
 *                  (Req 38.3). El balance lo valida el dominio (422 si no balancea).
 */
public record RegistrarPolizaRequest(
        @NotNull LocalDate fecha,
        @NotBlank String tipo,
        @NotBlank @Size(max = 300) String concepto,
        @Size(max = 40) String origen,
        UUID origenId,
        @NotNull @NotEmpty @Size(max = 500) @Valid List<RenglonPolizaRequest> renglones) {

    /**
     * Interpreta la etiqueta del tipo de poliza, rechazando con 422 si es desconocida.
     *
     * @return el tipo de poliza correspondiente.
     * @throws ReglaNegocioException si la etiqueta es desconocida (422).
     */
    public TipoPoliza tipoInterpretado() {
        try {
            return TipoPoliza.desdeValorBd(tipo);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de Poliza_Contable desconocido: " + tipo);
        }
    }
}

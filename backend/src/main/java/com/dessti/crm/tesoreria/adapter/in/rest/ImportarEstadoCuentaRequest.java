package com.dessti.crm.tesoreria.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para importar un Estado_Cuenta_Bancario (Req 43.2). DTO de
 * entrada del contrato REST, distinto del comando de aplicacion
 * {@link com.dessti.crm.tesoreria.application.ImportarEstadoCuentaCommand} (Req 12.2).
 *
 * <p>Los {@code movimientos} son opcionales: cuando se proveen, el adaptador de
 * importacion (stub) los reproduce; un adaptador real puede obtenerlos de la fuente
 * (archivo/API) indicada por {@code referenciaArchivo}.</p>
 *
 * @param referenciaArchivo referencia del origen (archivo/API); opcional (max 300).
 * @param periodoInicio     inicio del periodo; obligatorio.
 * @param periodoFin        fin del periodo; obligatorio.
 * @param movimientos       lineas a importar; opcional.
 */
public record ImportarEstadoCuentaRequest(
        @Size(max = 300) String referenciaArchivo,
        @NotNull LocalDate periodoInicio,
        @NotNull LocalDate periodoFin,
        @Size(max = 5000) @Valid List<MovimientoImportadoRequest> movimientos) {

    /**
     * Linea de un estado de cuenta a importar (Req 43.2).
     *
     * @param fecha       fecha del movimiento; obligatoria.
     * @param monto       monto con signo (+ deposito / - retiro); obligatorio.
     * @param referencia  referencia del movimiento; opcional (max 120).
     * @param descripcion descripcion/concepto; opcional (max 300).
     */
    public record MovimientoImportadoRequest(
            @NotNull LocalDate fecha,
            @NotNull BigDecimal monto,
            @Size(max = 120) String referencia,
            @Size(max = 300) String descripcion) {
    }
}

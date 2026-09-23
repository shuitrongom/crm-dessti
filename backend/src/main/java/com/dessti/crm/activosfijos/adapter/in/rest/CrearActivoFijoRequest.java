package com.dessti.crm.activosfijos.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para dar de alta un Activo_Fijo (Req 44.1). DTO de entrada
 * del contrato REST, distinto de la entidad JPA y del comando de aplicacion
 * (Req 12.2). El controlador lo traduce al comando antes de invocar el servicio.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> el {@code tenant_id} NO se
 * acepta en la peticion; se deriva del contexto autenticado.</p>
 *
 * <p><strong>Alcance de la validacion de campo (Req 44.2):</strong> aqui solo se
 * comprueba la presencia y unos limites amplios (Bean Validation -&gt; 400). Las
 * reglas de negocio (costo &gt; 0, vida util &gt; 0, valor residual en
 * {@code [0, costo]}, metodo valido) las revalida el dominio en la capa de
 * aplicacion (que responde 422).</p>
 *
 * @param nombre              nombre del bien; obligatorio (1..200).
 * @param costo               costo de adquisicion; obligatorio y positivo.
 * @param fechaAdquisicion    fecha de adquisicion; obligatoria.
 * @param vidaUtilMeses       vida util en meses; obligatoria y positiva.
 * @param metodoDepreciacion  etiqueta del metodo ({@code linea_recta}/{@code saldos_decrecientes}).
 * @param valorResidual       valor residual; opcional (se asume 0); no negativo.
 */
public record CrearActivoFijoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotNull @Positive BigDecimal costo,
        @NotNull LocalDate fechaAdquisicion,
        @NotNull @Positive Integer vidaUtilMeses,
        @NotBlank @Size(max = 20) String metodoDepreciacion,
        @PositiveOrZero BigDecimal valorResidual) {
}

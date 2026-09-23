package com.dessti.crm.social.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para crear una Campaña_Publicitaria (Req 65.7, 65.8). DTO de
 * entrada del contrato REST, distinto de la entidad JPA.
 *
 * <p>Las validaciones de rango del presupuesto ([0.01, 999,999,999.99]) y de
 * coherencia del periodo ({@code fecha_fin >= fecha_inicio}) las aplica el dominio
 * ({@code ValidacionCampana}, Property 39); aqui solo se exige presencia.</p>
 *
 * @param cuentaCanalSocialId Cuenta_Canal_Social asociada; opcional.
 * @param canal               etiqueta del Canal_Social; opcional (apoya el filtro del listado).
 * @param nombre              nombre descriptivo; obligatorio (Req 65.7).
 * @param presupuesto         presupuesto; obligatorio (Req 65.7, 65.8).
 * @param fechaInicio         fecha de inicio del periodo; obligatoria (Req 65.7).
 * @param fechaFin            fecha de fin del periodo; obligatoria (Req 65.8).
 * @param externoId           id externo en la Marketing API; opcional.
 */
public record CrearCampanaPublicitariaRequest(
        UUID cuentaCanalSocialId,
        @Size(max = 12) String canal,
        @NotBlank @Size(max = 200) String nombre,
        @NotNull BigDecimal presupuesto,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin,
        @Size(max = 120) String externoId) {
}

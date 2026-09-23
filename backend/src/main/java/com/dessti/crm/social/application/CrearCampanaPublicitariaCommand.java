package com.dessti.crm.social.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de creacion de una
 * {@link com.dessti.crm.social.domain.CampanaPublicitaria} (Req 65.7, 65.8).
 * Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param cuentaCanalSocialId Cuenta_Canal_Social asociada; opcional.
 * @param canal               Canal_Social; opcional (apoya el filtro del listado, Req 65.10).
 * @param nombre              nombre descriptivo; obligatorio (Req 65.7).
 * @param presupuesto         presupuesto en [0.01, 999,999,999.99]; obligatorio (Req 65.7, 65.8).
 * @param fechaInicio         fecha de inicio del periodo; obligatoria (Req 65.7).
 * @param fechaFin            fecha de fin del periodo ({@code >= inicio}); obligatoria (Req 65.8).
 * @param externoId           id externo en la Marketing API; opcional.
 */
public record CrearCampanaPublicitariaCommand(
        UUID cuentaCanalSocialId,
        com.dessti.crm.social.domain.CanalSocial canal,
        String nombre,
        BigDecimal presupuesto,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String externoId) {
}

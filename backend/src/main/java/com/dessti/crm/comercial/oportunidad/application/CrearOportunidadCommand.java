package com.dessti.crm.comercial.oportunidad.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de creacion de una {@link com.dessti.crm.comercial.oportunidad.domain.Oportunidad}
 * (Req 14.1). Objeto de entrada de la capa de aplicacion, distinto de la entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * @param clienteId     identificador del Cliente existente; obligatorio.
 * @param titulo        titulo de la Oportunidad; obligatorio (1..200).
 * @param valorEstimado valor estimado; obligatorio, en [0.01, 999,999,999.99].
 */
public record CrearOportunidadCommand(
        UUID clienteId,
        String titulo,
        BigDecimal valorEstimado) {
}

package com.dessti.crm.platform.audit;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Criterios de consulta de la bitacora de auditoria (Req 10.5, 10.9). Todos los
 * criterios son opcionales y se combinan con AND; un criterio ausente
 * ({@link Optional#empty()}) no restringe el resultado.
 *
 * <p>Soporta el filtrado exigido por el requisito: por <em>actor</em>, por tipo
 * de <em>recurso</em> y por <em>rango de fechas</em> ({@code timestamp_utc}).
 * Se incluye ademas el {@code tenantId} para acotar la lectura al ambito de una
 * empresa (o al ambito de plataforma). Este mismo filtro se usa tanto para la
 * consulta paginada (Req 10.5) como para la exportacion (Req 10.9).</p>
 *
 * @param tenantId  empresa a la que acotar la consulta; vacio para no filtrar
 *                  por empresa (segun politica de autorizacion del llamador).
 * @param actor     actor exacto a filtrar; vacio para no filtrar por actor.
 * @param recurso   tipo de recurso exacto a filtrar; vacio para no filtrar.
 * @param desde     limite inferior (inclusive) del rango de fechas UTC; vacio
 *                  para no acotar por fecha inicial.
 * @param hasta     limite superior (inclusive) del rango de fechas UTC; vacio
 *                  para no acotar por fecha final.
 */
public record FiltroAuditoria(
        Optional<UUID> tenantId,
        Optional<String> actor,
        Optional<String> recurso,
        Optional<Instant> desde,
        Optional<Instant> hasta) {

    /** Normaliza cualquier {@code null} a {@link Optional#empty()}. */
    public FiltroAuditoria {
        tenantId = normaliza(tenantId);
        actor = normaliza(actor);
        recurso = normaliza(recurso);
        desde = normaliza(desde);
        hasta = normaliza(hasta);
    }

    private static <T> Optional<T> normaliza(Optional<T> valor) {
        return (valor == null) ? Optional.empty() : valor;
    }

    /**
     * @return un filtro vacio que no aplica ninguna restriccion.
     */
    public static FiltroAuditoria vacio() {
        return new FiltroAuditoria(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }
}

package com.dessti.crm.platform.empresas.offboarding;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de salida de la <strong>exportacion estructurada</strong> de los datos de
 * negocio de una Empresa (Req 69.1), distinto de cualquier entidad de
 * persistencia. El adaptador REST lo serializa a un formato estructurado y
 * procesable (JSON).
 *
 * <p>La exportacion esta <strong>estrictamente limitada al {@code tenantId}</strong>
 * de la Empresa objetivo (Req 69.1, 69.5). El mapa {@link #recursos} agrupa, por
 * nombre de recurso ({@link RecursoTenantOffboarding#nombreRecurso()}), la
 * estructura de datos que cada modulo produjo para ese tenant. Nunca contiene
 * secretos (Req 10.10, 11.3).</p>
 *
 * @param tenantId      identificador de la Empresa exportada (su {@code tenant_id}).
 * @param generadoEn    instante de generacion de la exportacion en UTC (Req 69.6).
 * @param recursos      datos por recurso; clave = nombre del recurso, valor =
 *                      estructura serializable devuelta por su exportador. Puede
 *                      estar vacio si ningun modulo aporta datos.
 */
public record ExportacionTenantDto(
        UUID tenantId,
        Instant generadoEn,
        Map<String, Object> recursos) {
}

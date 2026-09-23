package com.dessti.crm.platform.empresas;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida de una {@link Suscripcion} (Req 25), distinto de la entidad de
 * persistencia. Expone el vinculo Empresa-Plan, el estado, la vigencia y el
 * override de modulos de la Empresa (Req 25.4).
 *
 * @param id                  identificador de la Suscripcion.
 * @param tenantId            Empresa (tenant) titular.
 * @param planId              Plan contratado; {@code null} cuando el instrumento
 *                            es una suscripcion (paquete).
 * @param tipoInstrumento     tipo de instrumento contratado (PLAN/SUSCRIPCION).
 * @param paqueteSuscripcionId identificador del Paquete de suscripcion contratado
 *                            cuando el instrumento es una suscripcion; {@code null}
 *                            cuando es un Plan. Uso interno, no se muestra como UUID.
 * @param estado             estado (activa/suspendida/cancelada, Req 25.2).
 * @param vigenciaInicio     inicio del periodo de vigencia.
 * @param vigenciaFin        fin del periodo de vigencia; {@code null} = sin fin.
 * @param modulosHabilitados subconjunto de modulos habilitados para la Empresa
 *                           (Req 25.4); {@code null} = hereda todos los del Plan,
 *                           lista (posiblemente vacia) = subconjunto especifico.
 * @param version            version para bloqueo optimista (Req 49).
 * @param createdAt          instante de alta (UTC).
 * @param updatedAt          instante de la ultima modificacion (UTC).
 */
public record SuscripcionDto(
        UUID id,
        UUID tenantId,
        UUID planId,
        TipoInstrumento tipoInstrumento,
        UUID paqueteSuscripcionId,
        EstadoSuscripcion estado,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFin,
        List<String> modulosHabilitados,
        String monedaFacturacion,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Suscripcion} a su DTO de salida.
     *
     * @param suscripcion entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static SuscripcionDto de(Suscripcion suscripcion) {
        return new SuscripcionDto(
                suscripcion.getId(),
                suscripcion.getTenantId(),
                suscripcion.getPlanId(),
                suscripcion.getTipoInstrumento(),
                suscripcion.getPaqueteSuscripcionId(),
                suscripcion.getEstado(),
                suscripcion.getVigenciaInicio(),
                suscripcion.getVigenciaFin(),
                suscripcion.getModulosHabilitados(),
                suscripcion.getMonedaFacturacion(),
                suscripcion.getVersion(),
                suscripcion.getCreatedAt(),
                suscripcion.getUpdatedAt());
    }
}

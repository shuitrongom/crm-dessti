package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de salida de un {@link PaqueteSuscripcion} (Req 3), distinto de la entidad
 * de persistencia. Espejo de {@link PlanDto}: expone el Giro, la moneda de
 * cotizacion, el precio por modulo y el total (suma de precios), ademas de los
 * limites y de los atributos propios del Paquete de Suscripcion (duracion del
 * contrato y periodo de prueba).
 *
 * @param id                  identificador del Paquete de Suscripcion.
 * @param nombre              nombre del Paquete (unico).
 * @param maxUsuarios         numero maximo de Usuarios del Paquete (Req 3.1).
 * @param giroId              Giro al que pertenece el Paquete.
 * @param monedaCodigo        codigo ISO 4217 de la moneda de cotizacion.
 * @param preciosModulos      precio por modulo del Paquete ({@code clave -> precio}).
 * @param total               total del Paquete: suma de los precios de sus
 *                            modulos (escala 2).
 * @param modulosHabilitados  modulos habilitados por el Paquete (claves de
 *                            {@code preciosModulos}) (Req 3.1).
 * @param duracionDias        duracion del contrato del Paquete en dias (Req 3.2).
 * @param admitePrueba        indica si el Paquete admite periodo de prueba
 *                            (Req 3.2).
 * @param duracionPruebaMeses duracion del periodo de prueba en meses;
 *                            {@code null} cuando el Paquete no admite prueba
 *                            (Req 3.2).
 * @param version             version para bloqueo optimista (Req 49).
 * @param createdAt           instante de alta (UTC).
 * @param updatedAt           instante de la ultima modificacion (UTC).
 */
public record PaqueteSuscripcionDto(
        UUID id,
        String nombre,
        int maxUsuarios,
        UUID giroId,
        String monedaCodigo,
        Map<String, BigDecimal> preciosModulos,
        BigDecimal total,
        List<String> modulosHabilitados,
        int duracionDias,
        boolean admitePrueba,
        Integer duracionPruebaMeses,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link PaqueteSuscripcion} a su DTO de salida.
     *
     * @param p entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static PaqueteSuscripcionDto de(PaqueteSuscripcion p) {
        return new PaqueteSuscripcionDto(
                p.getId(),
                p.getNombre(),
                p.getMaxUsuarios(),
                p.getGiroId(),
                p.getMonedaCodigo(),
                p.getPreciosModulos(),
                p.getTotal(),
                p.getModulosHabilitados(),
                p.getDuracionDias(),
                p.isAdmitePrueba(),
                p.getDuracionPruebaMeses(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}

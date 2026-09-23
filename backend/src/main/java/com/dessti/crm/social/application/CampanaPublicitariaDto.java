package com.dessti.crm.social.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.social.domain.CampanaPublicitaria;

/**
 * DTO de salida de una {@link CampanaPublicitaria} (Req 12.2, 65.7), distinto de la
 * entidad de persistencia. El estado operativo NO se incluye aqui: es de SOLO
 * LECTURA y se consulta aparte via el adaptador (Req 65.9,
 * {@link EstadoCampanaExterno}).
 *
 * @param id                  identificador de la Campaña_Publicitaria.
 * @param cuentaCanalSocialId Cuenta_Canal_Social asociada; {@code null} si no aplica.
 * @param canal               etiqueta del Canal_Social; {@code null} si no aplica.
 * @param nombre              nombre descriptivo.
 * @param presupuesto         presupuesto (escala 2) en [0.01, 999,999,999.99].
 * @param fechaInicio         fecha de inicio del periodo.
 * @param fechaFin            fecha de fin del periodo ({@code >= inicio}).
 * @param externoId           id externo en la Marketing API; {@code null} si no aplica.
 * @param estadoExterno       ultima instantanea conocida del estado externo; NO autoritativa.
 * @param version             version para concurrencia optimista (Req 49).
 * @param createdAt           instante de alta (UTC).
 * @param updatedAt           instante de la ultima modificacion (UTC).
 */
public record CampanaPublicitariaDto(
        UUID id,
        UUID cuentaCanalSocialId,
        String canal,
        String nombre,
        BigDecimal presupuesto,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String externoId,
        String estadoExterno,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link CampanaPublicitaria} a su DTO de salida.
     *
     * @param campana entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static CampanaPublicitariaDto de(CampanaPublicitaria campana) {
        return new CampanaPublicitariaDto(
                campana.getId(),
                campana.getCuentaCanalSocialId(),
                (campana.getCanal() == null) ? null : campana.getCanal().valorBd(),
                campana.getNombre(),
                campana.getPresupuesto(),
                campana.getFechaInicio(),
                campana.getFechaFin(),
                campana.getExternoId(),
                campana.getEstadoExterno(),
                campana.getVersion(),
                campana.getCreatedAt(),
                campana.getUpdatedAt());
    }
}

package com.dessti.crm.comercial.oportunidad.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.comercial.oportunidad.domain.Oportunidad;

/**
 * DTO de salida de una {@link Oportunidad} (Req 12.2, 14), distinto de la
 * entidad de persistencia. El controlador REST lo serializa; nunca se expone la
 * entidad JPA. La etapa se expone como su etiqueta de negocio
 * ({@code nuevo}, {@code calificado}, ...) coherente con el Req 14.3.
 *
 * @param id             identificador de la Oportunidad.
 * @param clienteId      Cliente al que pertenece (Req 14.1).
 * @param titulo         titulo de la Oportunidad.
 * @param valorEstimado  valor estimado (escala 2).
 * @param etapa          etiqueta de la etapa del pipeline (Req 14.3).
 * @param responsableUsuarioId Usuario responsable; {@code null} si no se asigno (Req 14.2).
 * @param cotizacionId   Cotizacion generada al convertir; {@code null} si no convertida (Req 14.5).
 * @param canalVentaId   Canal de venta al que se clasifica; {@code null} si no clasificada (Req 63.1).
 *                       Se expone para que los reportes comerciales puedan segmentar por canal (Req 63.2).
 * @param version        version para concurrencia optimista (Req 49).
 * @param createdAt      instante de alta (UTC).
 * @param updatedAt      instante de la ultima modificacion (UTC).
 */
public record OportunidadDto(
        UUID id,
        UUID clienteId,
        String titulo,
        BigDecimal valorEstimado,
        String etapa,
        UUID responsableUsuarioId,
        UUID cotizacionId,
        UUID canalVentaId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link Oportunidad} a su DTO de salida.
     *
     * @param oportunidad entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static OportunidadDto de(Oportunidad oportunidad) {
        return new OportunidadDto(
                oportunidad.getId(),
                oportunidad.getClienteId(),
                oportunidad.getTitulo(),
                oportunidad.getValorEstimado(),
                oportunidad.getEtapa().valorBd(),
                oportunidad.getResponsableUsuarioId(),
                oportunidad.getCotizacionId(),
                oportunidad.getCanalVentaId(),
                oportunidad.getVersion(),
                oportunidad.getCreatedAt(),
                oportunidad.getUpdatedAt());
    }
}

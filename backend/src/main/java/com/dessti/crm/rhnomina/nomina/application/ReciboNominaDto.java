package com.dessti.crm.rhnomina.nomina.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.dessti.crm.rhnomina.nomina.domain.ReciboNomina;

/**
 * DTO de salida de un {@link ReciboNomina} (Req 12.2, 41), distinto de la entidad de
 * persistencia. Expone los importes agregados calculados por Empleado, el estado
 * ({@code calculado}, {@code timbrado}, {@code cancelado}) y, cuando aplica, el
 * Folio_Fiscal del CFDI de nomina timbrado (Req 41.4, 41.7).
 *
 * @param id            identificador del Recibo_Nomina.
 * @param nominaId      Nomina a la que pertenece.
 * @param empleadoId    Empleado al que corresponde.
 * @param percepciones  total de percepciones (Req 41.1).
 * @param deducciones   total de deducciones (Req 41.1, 41.2).
 * @param subsidio      subsidio al empleo aplicado (Req 41.1).
 * @param neto          neto a pagar = percepciones - deducciones + subsidio (Req 41.1).
 * @param estado        etiqueta del estado (Req 41.5).
 * @param folioFiscal   Folio_Fiscal del CFDI de nomina; {@code null} si no timbrado (Req 41.4).
 * @param fechaTimbrado fecha/hora del Timbrado (UTC); {@code null} si no timbrado.
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 */
public record ReciboNominaDto(
        UUID id,
        UUID nominaId,
        UUID empleadoId,
        BigDecimal percepciones,
        BigDecimal deducciones,
        BigDecimal subsidio,
        BigDecimal neto,
        String estado,
        UUID folioFiscal,
        Instant fechaTimbrado,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Proyecta una entidad {@link ReciboNomina} a su DTO de salida.
     *
     * @param recibo entidad a proyectar.
     * @return el DTO correspondiente.
     */
    public static ReciboNominaDto de(ReciboNomina recibo) {
        return new ReciboNominaDto(
                recibo.getId(),
                recibo.getNominaId(),
                recibo.getEmpleadoId(),
                recibo.getPercepciones(),
                recibo.getDeducciones(),
                recibo.getSubsidio(),
                recibo.getNeto(),
                recibo.getEstado().valorBd(),
                recibo.getFolioFiscal(),
                recibo.getFechaTimbrado(),
                recibo.getVersion(),
                recibo.getCreatedAt(),
                recibo.getUpdatedAt());
    }
}

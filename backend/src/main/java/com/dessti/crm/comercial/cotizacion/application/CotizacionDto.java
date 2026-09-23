package com.dessti.crm.comercial.cotizacion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dessti.crm.comercial.cotizacion.application.DatosClientePort.DatosCliente;
import com.dessti.crm.comercial.cotizacion.domain.Cotizacion;

/**
 * DTO de salida de una {@link Cotizacion} (Req 12.2, 6), distinto de la entidad
 * de persistencia. El controlador REST lo serializa; nunca se expone la entidad
 * JPA. El estado se expone como su etiqueta de negocio ({@code borrador},
 * {@code enviada}, {@code aprobada}, {@code rechazada}) coherente con el Req 6.6.
 *
 * @param id            identificador de la Cotizacion.
 * @param folio         folio legible por humanos (p. ej. COT-2026-0001, V60);
 *                      {@code null} en filas historicas previas a V60.
 * @param clienteId     Cliente al que pertenece (Req 6.1).
 * @param clienteNombre nombre/razon social del Cliente, si es resoluble (V60).
 * @param clienteRfc    RFC del Cliente, si es resoluble (V60).
 * @param clienteEmail  correo del Cliente, si es resoluble (V60); {@code null}
 *                      si el Cliente no tiene correo.
 * @param oportunidadId Oportunidad de origen; {@code null} en alta manual (Req 14.5).
 * @param estado        etiqueta del estado (Req 6.6).
 * @param subtotal      suma de subtotales (escala 2, Req 6.5).
 * @param total         total = round(Σ subtotales, 2) (Req 6.5).
 * @param moneda        moneda ISO 4217 de la Cotizacion (por defecto MXN, V60).
 * @param fechaEmision  fecha de emision (por defecto la de creacion, V60).
 * @param validoHasta   fecha de vigencia/expiracion; opcional (V60).
 * @param condiciones   terminos y condiciones; opcional (V60).
 * @param notas         notas libres; opcional (V60).
 * @param enviadaEn     instante del ultimo envio por correo; {@code null} si nunca (V60).
 * @param partidas      partidas de la Cotizacion (Req 6.3).
 * @param canalVentaId  Canal de venta al que se clasifica; {@code null} si no clasificada (Req 63.1).
 * @param version       version para concurrencia optimista (Req 49).
 * @param createdAt     instante de alta (UTC).
 * @param updatedAt     instante de la ultima modificacion (UTC).
 * @param emisorIncompleto {@code true} si los datos fiscales de la Empresa emisora
 *                      estan incompletos (falta RFC o direccion) al generar/enviar el
 *                      documento, para que el frontend muestre un aviso no intrusivo
 *                      (Req 3); {@code false} en las consultas/altas que no resuelven
 *                      el emisor.
 */
public record CotizacionDto(
        UUID id,
        String folio,
        UUID clienteId,
        String clienteNombre,
        String clienteRfc,
        String clienteEmail,
        UUID oportunidadId,
        String estado,
        BigDecimal subtotal,
        BigDecimal total,
        String moneda,
        LocalDate fechaEmision,
        LocalDate validoHasta,
        String condiciones,
        String notas,
        Instant enviadaEn,
        List<PartidaCotizacionDto> partidas,
        UUID canalVentaId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        boolean emisorIncompleto) {

    /**
     * Proyecta una entidad {@link Cotizacion} a su DTO de salida (sin datos del
     * Cliente resueltos). Los campos {@code clienteNombre}/{@code clienteRfc}/
     * {@code clienteEmail} quedan en {@code null}; usar
     * {@link #de(Cotizacion, DatosCliente)} para incluirlos.
     *
     * @param cotizacion entidad a proyectar.
     * @return el DTO correspondiente, sin datos del Cliente resueltos.
     */
    public static CotizacionDto de(Cotizacion cotizacion) {
        return de(cotizacion, null);
    }

    /**
     * Proyecta una entidad {@link Cotizacion} a su DTO de salida, incluyendo sus
     * partidas y, si se aportan, los datos visibles del Cliente (nombre, RFC,
     * correo) para el frontend y el PDF (V60).
     *
     * @param cotizacion entidad a proyectar.
     * @param cliente    datos del Cliente resueltos; puede ser {@code null}.
     * @return el DTO correspondiente.
     */
    public static CotizacionDto de(Cotizacion cotizacion, DatosCliente cliente) {
        List<PartidaCotizacionDto> partidas = cotizacion.getPartidas().stream()
                .map(PartidaCotizacionDto::de)
                .toList();
        return new CotizacionDto(
                cotizacion.getId(),
                cotizacion.getFolio(),
                cotizacion.getClienteId(),
                cliente == null ? null : cliente.nombre(),
                cliente == null ? null : cliente.rfc(),
                cliente == null ? null : cliente.email(),
                cotizacion.getOportunidadId(),
                cotizacion.getEstado().valorBd(),
                cotizacion.getSubtotal(),
                cotizacion.getTotal(),
                cotizacion.getMoneda(),
                cotizacion.getFechaEmision(),
                cotizacion.getValidoHasta(),
                cotizacion.getCondiciones(),
                cotizacion.getNotas(),
                cotizacion.getEnviadaEn(),
                partidas,
                cotizacion.getCanalVentaId(),
                cotizacion.getVersion(),
                cotizacion.getCreatedAt(),
                cotizacion.getUpdatedAt(),
                false);
    }

    /**
     * Devuelve una copia de este DTO con el flag {@code emisorIncompleto}
     * indicado (Req 3). Se usa en el envio/generacion para propagar a la capa REST
     * si la Empresa emisora tiene datos fiscales incompletos, sin alterar el resto
     * del contenido.
     *
     * @param emisorIncompleto {@code true} si faltan RFC o direccion de la Empresa.
     * @return una copia con el flag actualizado.
     */
    public CotizacionDto conEmisorIncompleto(boolean emisorIncompleto) {
        return new CotizacionDto(id, folio, clienteId, clienteNombre, clienteRfc, clienteEmail,
                oportunidadId, estado, subtotal, total, moneda, fechaEmision, validoHasta,
                condiciones, notas, enviadaEn, partidas, canalVentaId, version, createdAt,
                updatedAt, emisorIncompleto);
    }
}

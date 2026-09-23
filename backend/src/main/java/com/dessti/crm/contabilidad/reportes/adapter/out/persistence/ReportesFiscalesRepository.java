package com.dessti.crm.contabilidad.reportes.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.facturacion.factura.domain.EstadoFactura;
import com.dessti.crm.facturacion.factura.domain.Factura;

/**
 * Repositorio Spring Data JPA de <strong>solo lectura</strong> que agrega los
 * importes fiscales de las Facturas timbradas de un periodo para los reportes de
 * ingresos por periodo y de IVA trasladado/retenido (Req 39.1). No expone
 * operaciones de escritura: es una agregacion que no modifica los datos de origen
 * (Req 39.2).
 *
 * <p>Como {@link Factura} extiende {@code TenantScopedEntity}, el filtro global de
 * Hibernate (Capa 1) y la RLS de V30 (Capa 2) acotan estas consultas al tenant
 * vigente (Req 23). El periodo se expresa en instantes UTC {@code [desde, hasta)}
 * sobre {@code fecha_timbrado}; la capa de aplicacion convierte el rango de fechas de
 * la peticion a esos instantes.</p>
 */
public interface ReportesFiscalesRepository extends JpaRepository<Factura, UUID> {

    /**
     * Agrega el subtotal, el IVA, las retenciones, el total y el numero de Facturas
     * <strong>timbradas</strong> con {@code fecha_timbrado} en el intervalo UTC
     * {@code [desde, hasta)}, opcionalmente acotado a un Cliente (Req 39.1, 39.3).
     * Solo considera Facturas en estado {@code timbrada} (excluye borradores y
     * canceladas), ya que solo estas representan ingresos fiscales efectivos.
     *
     * @param desde     instante minimo (inclusivo) de {@code fecha_timbrado}; obligatorio.
     * @param hasta     instante maximo (exclusivo) de {@code fecha_timbrado}; obligatorio.
     * @param clienteId Cliente a filtrar; {@code null} incluye a todos (Req 39.3).
     * @return los importes fiscales agregados del periodo (solo lectura).
     */
    @Query("""
            SELECT COUNT(f) AS numeroFacturas,
                   COALESCE(SUM(f.subtotal), 0) AS subtotal,
                   COALESCE(SUM(f.iva), 0) AS iva,
                   COALESCE(SUM(f.retenciones), 0) AS retenciones,
                   COALESCE(SUM(f.total), 0) AS total
            FROM Factura f
            WHERE f.estado = :estado
              AND f.fechaTimbrado >= :desde
              AND f.fechaTimbrado < :hasta
              AND (:clienteId IS NULL OR f.clienteId = :clienteId)
            """)
    IngresosPeriodoProjection agregarIngresosPeriodo(
            @Param("estado") EstadoFactura estado,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            @Param("clienteId") UUID clienteId);
}

package com.dessti.crm.vertical.manufactura.domain;

import java.util.UUID;

/**
 * Entidad de dominio de <strong>demostracion</strong> del giro
 * {@code manufactura}: Orden de Produccion (Req 12.1).
 *
 * <p><strong>Dominio puro (esqueleto).</strong> Es un record inmutable
 * <strong>SIN</strong> anotacion {@code @Entity}, sin tabla ni migracion de
 * negocio y sin repositorio JPA. Evidencia, junto con {@link Bom}, la ESTRUCTURA
 * del vertical de manufactura (BOM &rarr; Orden_Produccion) que encaja en el
 * {@code ContratoVertical}, sin exigir esquema de base de datos (ver
 * {@code package-info.java}).</p>
 *
 * <p>El campo {@code tenantId} ilustra, sin persistir, que la entidad seria
 * tenant-scoped ({@code tenant_id} + RLS) de implementarse el vertical de forma
 * completa; el {@code bomId} referencia la {@link Bom} de demostracion que la
 * orden consume. La cantidad a producir se modela con un tipo primitivo por
 * simplicidad del esqueleto.</p>
 *
 * @param id                 identificador de la orden de produccion (demostracion).
 * @param tenantId           Empresa (tenant) propietaria; ilustra el caracter
 *                           tenant-scoped que tendria de persistirse.
 * @param bomId              lista de materiales (BOM) que consume la orden.
 * @param cantidadAProducir  cantidad de producto terminado a producir.
 * @param estado             etiqueta de estado de la orden (demostracion).
 */
public record OrdenProduccion(
        UUID id,
        UUID tenantId,
        UUID bomId,
        int cantidadAProducir,
        String estado) {
}

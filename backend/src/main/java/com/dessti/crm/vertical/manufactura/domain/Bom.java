package com.dessti.crm.vertical.manufactura.domain;

import java.util.UUID;

/**
 * Entidad de dominio de <strong>demostracion</strong> del giro
 * {@code manufactura}: Lista de Materiales (BOM, <em>Bill of Materials</em>)
 * (Req 12.1).
 *
 * <p><strong>Dominio puro (esqueleto).</strong> Es un record inmutable
 * <strong>SIN</strong> anotacion {@code @Entity}, sin tabla ni migracion de
 * negocio y sin repositorio JPA. Su unico proposito es evidenciar que el
 * Vertical_Manufactura aporta entidades propias que encajan en el modelo
 * enchufable, no ofrecer persistencia real (ver {@code package-info.java}). Un
 * vertical funcional completo materializaria esta entidad como agregado
 * persistente tenant-scoped.</p>
 *
 * <p>Se incluye {@code tenantId} unicamente para <strong>ilustrar</strong> que,
 * de persistirse, la entidad seria multi-tenant (acotada por {@code tenant_id} +
 * RLS, como el resto del negocio), coherente con que el Giro es un atributo de la
 * Empresa y nunca un nuevo eje de aislamiento (Req 8.1).</p>
 *
 * @param id           identificador de la BOM (demostracion).
 * @param tenantId     Empresa (tenant) propietaria; ilustra el caracter
 *                     tenant-scoped que tendria de persistirse.
 * @param productoId   producto terminado al que corresponde la BOM.
 * @param descripcion  descripcion legible de la lista de materiales.
 * @param cantidadBase cantidad base de producto para la que aplica la BOM.
 */
public record Bom(
        UUID id,
        UUID tenantId,
        UUID productoId,
        String descripcion,
        int cantidadBase) {
}

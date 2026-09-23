package com.dessti.crm.rhnomina.organizacion.application;

import java.util.UUID;

/**
 * Comando de creacion de un {@link com.dessti.crm.rhnomina.organizacion.domain.Puesto}
 * (Req 61.1). Objeto de entrada de la capa de aplicacion, distinto de las entidades.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el {@code tenant_id};
 * el tenant se deriva del contexto autenticado.</p>
 *
 * @param nombre           nombre del Puesto; obligatorio (1..200).
 * @param descripcion      descripcion opcional (&lt;= 500).
 * @param puestoSuperiorId superior directo en la jerarquia; {@code null} si es raiz.
 */
public record CrearPuestoCommand(
        String nombre,
        String descripcion,
        UUID puestoSuperiorId) {
}

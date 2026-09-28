package com.dessti.crm.portalcliente.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyeccion de solo lectura de una Queja_Cliente para el Portal del Cliente
 * (Req 45, 70.1). Expone unicamente lo que el Cliente debe ver de su propia queja
 * (identificador, descripcion, estado y fecha de registro), sin campos internos del
 * Sistema de Gestion de Calidad (p. ej. la Accion_Correctiva vinculada).
 *
 * @param id           identificador de la Queja_Cliente.
 * @param descripcion  descripcion registrada por el Cliente.
 * @param estado       etiqueta del estado (registrada/vinculada/atendida).
 * @param registradaEn instante de registro (UTC).
 */
public record QuejaPortalResumen(
        UUID id,
        String descripcion,
        String estado,
        Instant registradaEn) {
}

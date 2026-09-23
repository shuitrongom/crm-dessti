package com.dessti.crm.operacion.proyecto.application;

import java.util.UUID;

/**
 * Comando de aplicacion para agregar un Sitio a un Proyecto existente (Req 21.2).
 * Es un objeto de entrada de la capa de aplicacion, independiente del contrato
 * REST. El {@code tenant_id} y el actor se derivan del contexto autenticado
 * (Req 23.4).
 *
 * @param proyectoId identificador del Proyecto al que se agrega el Sitio;
 *                   obligatorio (Req 21.2).
 * @param nombre     nombre del Sitio; obligatorio (1..200, Req 21.2).
 * @param direccion  direccion fisica del Sitio; opcional (hasta 500 caracteres).
 */
public record AgregarSitioCommand(UUID proyectoId, String nombre, String direccion) {
}

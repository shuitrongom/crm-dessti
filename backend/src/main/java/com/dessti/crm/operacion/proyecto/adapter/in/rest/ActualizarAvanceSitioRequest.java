package com.dessti.crm.operacion.proyecto.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la peticion para actualizar la fase operativa generica de un Sitio de un
 * Proyecto multi-sitio (Req 3.2), usado tanto para avanzar
 * ({@code /proyectos/{id}/sitios/{sitioId}/avance}) como para corregir/retroceder
 * ({@code .../correccion-fase}). El Proyecto y el Sitio se indican en la ruta; el
 * {@code tenant_id} y el actor se derivan del contexto (Req 23.4).
 *
 * @param fase         etiqueta de la fase destino (pendiente/en_preparacion/
 *                     en_instalacion/entregado); obligatoria. Se valida contra el dominio.
 * @param nota         nota opcional del avance/correccion; hasta 500 caracteres.
 * @param evidenciaUrl referencia opcional a la evidencia que respalda la fase (enlace a
 *                     foto, acta o documento); hasta 1000 caracteres.
 */
public record ActualizarAvanceSitioRequest(
        @NotBlank String fase,
        @Size(max = 500) String nota,
        @Size(max = 1000) String evidenciaUrl) {
}

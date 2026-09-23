package com.dessti.crm.operacion.inventario.avanzado.application;

/**
 * Comando de aplicacion para editar (renombrar/reclasificar) un Almacen (Req 60).
 * Transporta los datos de entrada del caso de uso, desacoplados del contrato REST y de
 * la entidad JPA. La validacion de formato la refuerza el dominio ({@code Almacen.actualizar}).
 *
 * @param nombre nuevo nombre del Almacen; obligatorio, 1..200 caracteres.
 * @param tipo   nuevo tipo del Almacen ({@code sucursal} o {@code bodega}); obligatorio.
 */
public record ActualizarAlmacenCommand(String nombre, String tipo) {
}

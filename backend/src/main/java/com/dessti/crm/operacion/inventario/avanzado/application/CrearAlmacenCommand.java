package com.dessti.crm.operacion.inventario.avanzado.application;

/**
 * Comando de aplicacion para dar de alta un Almacen (Req 60). Transporta los datos de
 * entrada del caso de uso, desacoplados del contrato REST y de la entidad JPA. La
 * validacion de formato la refuerza el dominio ({@code Almacen.crear}).
 *
 * @param nombre nombre del Almacen; obligatorio, 1..200 caracteres.
 * @param tipo   tipo del Almacen ({@code sucursal} o {@code bodega}); obligatorio.
 */
public record CrearAlmacenCommand(String nombre, String tipo) {
}

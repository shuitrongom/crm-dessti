/**
 * Submodulo <strong>Clientes y Contactos</strong> del modulo comercial-crm
 * (Req 5, 23). Primer modulo de negocio; establece el patron hexagonal por
 * submodulo que los modulos posteriores replican:
 *
 * <ul>
 *   <li>{@code domain}                   - entidades {@code Cliente}/{@code Contacto},
 *       reglas de validacion ({@code DatosContacto}) y borrado logico.</li>
 *   <li>{@code application}              - servicio de aplicacion
 *       {@code ServicioClientes}, DTOs y comandos.</li>
 *   <li>{@code adapter.out.persistence}  - repositorios Spring Data JPA.</li>
 * </ul>
 *
 * <p>La API REST (listado paginado/filtro, guardada por RBAC) corresponde a la
 * tarea 15.2 y consume los DTOs y el servicio definidos aqui.</p>
 */
package com.dessti.crm.comercial.cliente;

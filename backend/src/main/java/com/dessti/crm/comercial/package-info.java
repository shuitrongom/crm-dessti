/**
 * Modulo de negocio comercial-crm: Clientes, Contactos, Oportunidades/pipeline,
 * Cotizaciones, Pruebas de Diseno; ademas Catalogo de Productos/Listas de Precios
 * (Req 59) y Canal_Venta (Req 63). Ver design.md.
 *
 * <p>Este modulo sirve de referencia del patron hexagonal por modulo:</p>
 * <ul>
 *   <li>{@code domain}                   - entidades de dominio, reglas y puertos.</li>
 *   <li>{@code application}              - casos de uso / servicios de aplicacion.</li>
 *   <li>{@code adapter.in.rest}          - controladores REST + DTOs.</li>
 *   <li>{@code adapter.out.persistence}  - entidades JPA + repositorios.</li>
 * </ul>
 *
 * Placeholder de andamiaje: la logica de negocio se implementa en tareas 15+.
 */
package com.dessti.crm.comercial;

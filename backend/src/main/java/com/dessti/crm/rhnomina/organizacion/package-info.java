/**
 * Submodulo de <strong>Organizacion de personal</strong> del modulo rhnomina
 * (Req 61, 40, 23). Estructura el organigrama de la Empresa siguiendo el patron
 * hexagonal por submodulo:
 *
 * <ul>
 *   <li>{@code domain}                  - entidades {@code Puesto} (con jerarquia
 *       auto-referenciada), {@code AsignacionPuesto} y {@code EvaluacionDesempeno};
 *       el componente PURO {@code GrafoOrganigrama} (validacion aciclica y
 *       derivacion del organigrama, Property 35) y el nodo derivado
 *       {@code OrganigramaNodo}.</li>
 *   <li>{@code application}             - servicio de aplicacion
 *       {@code ServicioOrganizacion}, DTOs y comandos.</li>
 *   <li>{@code adapter.in.rest}         - controlador REST guardado por RBAC.</li>
 *   <li>{@code adapter.out.persistence} - repositorios Spring Data JPA.</li>
 * </ul>
 *
 * <h2>Alcance (Req 61)</h2>
 * <p>Definicion de Puestos y su jerarquia (organigrama de la Empresa); asignacion
 * de Empleados a Puestos; registro de Evaluacion_Desempeno con escala (1..5) e
 * historial; organigrama derivado de solo lectura; listado paginado con filtros
 * por Empleado o periodo; y auditoria de las operaciones.</p>
 *
 * <h2>Jerarquia aciclica (Req 61.7)</h2>
 * <p>La jerarquia de Puestos <strong>no</strong> puede formar ciclos. El
 * auto-superior directo se bloquea por CHECK en V36; el ciclo indirecto lo valida
 * el componente puro {@code GrafoOrganigrama.introduciriaCiclo} antes de crear o
 * mover un Puesto (HTTP 422). Esa funcion pura es la que ejercita la Property 35.</p>
 */
package com.dessti.crm.rhnomina.organizacion;

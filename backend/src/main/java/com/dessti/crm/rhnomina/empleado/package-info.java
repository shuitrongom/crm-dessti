/**
 * Submodulo base de <strong>Recursos Humanos</strong> del modulo rhnomina
 * (Req 40, 23). Gestiona el ciclo de vida del personal siguiendo el patron
 * hexagonal por submodulo:
 *
 * <ul>
 *   <li>{@code domain}                  - entidades {@code Empleado},
 *       {@code ContratoLaboral} e {@code Incidencia}, enumeraciones con sus
 *       convertidores ({@code TipoContrato}, {@code Periodicidad},
 *       {@code TipoIncidencia}), reglas de validacion ({@code ValidacionesEmpleado})
 *       y borrado logico.</li>
 *   <li>{@code application}             - servicio de aplicacion
 *       {@code ServicioEmpleados}, DTOs y comandos.</li>
 *   <li>{@code adapter.in.rest}         - controlador REST guardado por RBAC.</li>
 *   <li>{@code adapter.out.persistence} - repositorios Spring Data JPA.</li>
 * </ul>
 *
 * <h2>Alcance (Req 40)</h2>
 * <p>Alta de Empleado con datos obligatorios y validacion de RFC/CURP/NSS del
 * IMSS junto con su Contrato_Laboral (atomica); registro de Incidencia por
 * Periodo_Nomina; baja logica conservando el historico; listado paginado con
 * filtro por nombre/estado; y auditoria de las operaciones. El calculo de nomina
 * y la organizacion (Puesto/organigrama) pertenecen a bloques posteriores.</p>
 */
package com.dessti.crm.rhnomina.empleado;

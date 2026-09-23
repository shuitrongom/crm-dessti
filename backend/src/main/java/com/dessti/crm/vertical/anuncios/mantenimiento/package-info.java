/**
 * Modulo <strong>mantenimiento</strong> (Req 20; tarea 25.1). Establece la raiz de
 * paquetes {@code com.dessti.crm.vertical.anuncios.mantenimiento}, coherente con la organizacion de
 * los demas modulos de negocio del CRM.
 *
 * <p>Gestiona dos raices de agregado tenant-scoped (Req 23):</p>
 * <ul>
 *   <li>{@code Contrato_Mantenimiento}: contrato de SLA asociado a un Cliente con
 *       datos obligatorios (tipo {@code preventivo}/{@code correctivo} y los tiempos
 *       del SLA de respuesta y de resolucion en horas, ambos &gt; 0), con id unico
 *       (Req 20.1).</li>
 *   <li>{@code Ticket_Servicio}: incidencia/orden de servicio con id unico y estado
 *       inicial {@code abierto}, generada manualmente o por mantenimiento preventivo
 *       programado de un contrato (Req 20.2); asignable a un tecnico o a una
 *       Cuadrilla (Req 20.3); con maquina de estados {@code abierto -> asignado ->
 *       en_proceso -> resuelto -> cerrado} donde toda transicion no definida se
 *       rechaza conservando el estado (Req 20.4, 20.5); al pasar a {@code resuelto}
 *       registra el cumplimiento/incumplimiento del SLA comparando el tiempo
 *       transcurrido desde la apertura con los tiempos del contrato asociado
 *       (Req 20.6); listado paginado (20/100) con filtros por estado, por Cliente y
 *       por vencimiento del SLA (Req 20.7); y auditoria del alta y del cambio de
 *       estado (actor, estado anterior, estado nuevo, marca UTC, Req 20.8).</li>
 * </ul>
 *
 * <h2>Evaluacion del SLA (Req 20.6)</h2>
 * <p>El calculo del cumplimiento vive en la capa de aplicacion
 * ({@code ServicioMantenimiento}) porque requiere el contrato asociado y el
 * {@code Clock} inyectado; el dominio ({@code TicketServicio#marcarResuelto}) solo
 * persiste el resultado. La regla: {@code cumplido = horasTranscurridas <=
 * horasSla}, evaluada para el tiempo de respuesta y el de resolucion. Un ticket sin
 * contrato no tiene SLA que evaluar y sus banderas quedan nulas (V27 DECISION 2).</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code ContratoMantenimiento} y {@code TicketServicio}
 *       (entidades y raices de agregado), {@code EstadoTicketServicio} (enum +
 *       maquina de estados pura), {@code TipoContratoMantenimiento},
 *       {@code OrigenTicket}, {@code AsignadoTipo} y sus convertidores JPA.</li>
 *   <li>{@code application}: {@code ServicioMantenimiento} (casos de uso), sus DTOs
 *       y comandos.</li>
 *   <li>{@code adapter.in.rest}: {@code MantenimientoController} y sus DTOs de
 *       peticion.</li>
 *   <li>{@code adapter.out.persistence}: {@code ContratoMantenimientoRepository} y
 *       {@code TicketServicioRepository}.</li>
 * </ul>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> ambas raices extienden
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V27). Los permisos
 * {@code contrato_mantenimiento:*} y {@code ticket_servicio:*} ya se sembraron en V5
 * y se asignaron al rol {@code mantenimiento} (Req 27.7).</p>
 */
package com.dessti.crm.vertical.anuncios.mantenimiento;

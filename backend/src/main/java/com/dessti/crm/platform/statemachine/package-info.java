/**
 * Soporte reutilizable y <strong>puro</strong> para las maquinas de estado del
 * dominio (design.md, seccion <em>State Machines</em>).
 *
 * <p>El diseno establece que cada maquina de estado se implementa en el dominio
 * como una <strong>funcion pura</strong> que, dado {@code (estadoActual, evento)}
 * o {@code (estadoActual, estadoDestino)}, acepta la transicion si y solo si
 * pertenece al conjunto de transiciones definidas, y rechaza toda transicion que
 * parta de un estado final. Este paquete ofrece la infraestructura minima para
 * expresar ese patron de forma consistente entre modulos:</p>
 *
 * <ul>
 *   <li>{@link com.dessti.crm.platform.statemachine.MaquinaEstados}: tabla de
 *       transiciones inmutable, sin dependencias de Spring, determinista y
 *       facilmente verificable por pruebas de propiedad.</li>
 * </ul>
 *
 * <p><strong>Coordinacion (tarea 46.1):</strong> este paquete es la semilla
 * sobre la que la tarea 46.1 consolidara las maquinas de estado de todos los
 * modulos (Cotizacion, Orden_Fabricacion, Oportunidad, Permiso_Instalacion,
 * etc.) para eliminar duplicacion, y la tarea 46.2 la ejercera con la
 * <em>Property 5</em> (transiciones de estado validas). Por eso se mantiene
 * generico y libre de reglas especificas de un modulo.</p>
 */
package com.dessti.crm.platform.statemachine;

/**
 * Submodulo <strong>instalacion</strong> del modulo operacion-produccion (Req 19;
 * tarea 22.1). Establece la raiz de paquetes
 * {@code com.dessti.crm.vertical.anuncios.instalacion}, de forma coherente con la
 * organizacion de los submodulos {@code ordenfabricacion}, {@code levantamiento} y
 * {@code permiso}.
 *
 * <p>Gestiona {@code Orden_Trabajo_Instalacion} (OTI) creadas a partir de una
 * {@code Orden_Fabricacion} <em>terminada</em>: programacion condicionada a
 * precondiciones (Req 19.2, 19.3), estado inicial {@code programada} (Req 19.1),
 * maquina de estados con finales {@code completada}/{@code cancelada} (Req 19.5),
 * registro de avance con evidencia fotografica y Lista_Pendientes (Req 19.4),
 * guarda de cierre por pendientes sin resolver (Req 19.6), listado paginado con
 * filtros por estado, Cuadrilla y Cliente (Req 19.7) y auditoria de creacion y
 * cambio de estado (Req 19.8).</p>
 *
 * <h2>Precondiciones de programacion (Req 19.2, 19.3)</h2>
 * <p>Una OTI se programa <em>si y solo si</em> la Orden_Fabricacion esta
 * {@code terminada} (Req 19.2), el Sitio tiene un Levantamiento_Sitio
 * {@code completado} y un Permiso_Instalacion {@code aprobado} (Req 19.3). El
 * {@code ServicioOrdenesTrabajoInstalacion} aplica las precondiciones en orden
 * consumiendo los puertos {@code OrdenFabricacionTerminadaPort},
 * {@code LevantamientoCompletadoPort} y {@code PermisoAprobadoPort}, y devuelve las
 * excepciones correspondientes (404 / 422) sin crear ninguna OTI en caso de
 * rechazo.</p>
 *
 * <h2>Guarda de cierre (Req 19.6)</h2>
 * <p>La transicion a {@code completada} se rechaza (422, "existen pendientes por
 * resolver") si la Lista_Pendientes de la OTI tiene al menos un elemento sin
 * resolver. Esta guarda vive en la capa de aplicacion —no en el dominio puro—
 * porque requiere consultar la tabla hija {@code pendiente_instalacion}, del mismo
 * modo que las precondiciones de generacion viven en {@code ServicioOrdenesFabricacion}.</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code OrdenTrabajoInstalacion} (entidad y raiz de
 *       agregado), {@code PendienteInstalacion} y {@code EvidenciaInstalacion}
 *       (entidades hijas), {@code EstadoOrdenTrabajoInstalacion} (enum + maquina de
 *       estados pura) y su convertidor JPA.</li>
 *   <li>{@code application}: {@code ServicioOrdenesTrabajoInstalacion} (casos de
 *       uso), sus DTOs y comandos. Consume los puertos
 *       {@code OrdenFabricacionTerminadaPort} (modulo ordenfabricacion),
 *       {@code LevantamientoCompletadoPort} (modulo levantamiento) y
 *       {@code PermisoAprobadoPort} (modulo permiso).</li>
 *   <li>{@code adapter.in.rest}: {@code OrdenTrabajoInstalacionController} y sus
 *       DTOs de peticion.</li>
 *   <li>{@code adapter.out.persistence}: los repositorios de la OTI, de la
 *       Lista_Pendientes y de las evidencias, y el adaptador
 *       {@code InstalacionCompletadaAdapter}.</li>
 * </ul>
 *
 * <h2>Puerto de lectura para el avance del Proyecto (Req 21.3)</h2>
 * <p>Este submodulo publica {@code InstalacionCompletadaPort}, un puerto de
 * solo-lectura que el submodulo {@code proyecto} (tarea 22.2) consume para derivar,
 * por Sitio, dos fases del avance consolidado: la instalacion completada (existe una
 * OTI {@code completada}, Req 19.5) y la Orden_Fabricacion respaldada (existe alguna
 * OTI para el Sitio, lo que implica una OF terminada, Req 19.1/19.2). Lo implementa
 * {@code InstalacionCompletadaAdapter} sobre el {@code OrdenTrabajoInstalacionRepository}.</p>
 *
 * <p><strong>Multi-tenant (Req 23):</strong> las tres entidades extienden
 * {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V24). Las
 * columnas {@code sitio_id} y {@code cuadrilla_id} son referencias debiles (sin FK)
 * porque las tablas {@code sitio} (tarea 22.2) y {@code cuadrilla} aun no
 * existen.</p>
 */
package com.dessti.crm.vertical.anuncios.instalacion;

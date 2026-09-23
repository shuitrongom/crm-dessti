/**
 * Submodulo <strong>proyecto</strong> del Nucleo del modulo {@code operacion}
 * (Req 21; movido al Nucleo por la Decision D1 del spec operacion-produccion-enterprise,
 * como ya lo estan {@code inventario} y {@code produccion}). Establece la
 * raiz de paquetes {@code com.dessti.crm.operacion.proyecto}, coherente
 * con la organizacion de {@code com.dessti.crm.operacion.inventario} y
 * {@code com.dessti.crm.operacion.produccion} del Nucleo.
 *
 * <p>Gestiona {@code Proyecto} (agrupador asociado a un Cliente) y sus
 * {@code Sitio} (ubicaciones fisicas donde se ejecutan las fases de instalacion):
 * creacion del Proyecto asociado a un Cliente con nombre 1..200 (Req 21.1),
 * agregado de Sitios a un Proyecto (Req 21.2), avance individual de cada Sitio en
 * las 4 fases (Req 21.3), consulta con estado consolidado derivado (Req 21.4),
 * listado paginado 20/100 con filtro por Cliente (Req 21.5) y auditoria de
 * creacion/modificacion de Proyecto y Sitio (Req 21.6).</p>
 *
 * <h2>Avance y estado consolidado (Req 21.3, 21.4)</h2>
 * <p>El avance de cada Sitio en las fases Levantamiento_Sitio, Permiso_Instalacion,
 * Orden_Fabricacion y Orden_Trabajo_Instalacion vive en otros submodulos. Para
 * respetar los limites hexagonales, este submodulo NO duplica ese estado: lo
 * consulta en tiempo de lectura a traves del puerto de solo-lectura
 * {@code AvanceSitioPort}. Ese puerto tiene <strong>dos</strong> implementaciones
 * (Decision D5-b): {@code AvanceProduccionAdapter} (Nucleo) compone solo la fase de
 * produccion (via {@code SitioOrdenFabricacionTerminadaPort}) para giros genericos,
 * y {@code AvanceSitioAnunciosAdapter} (vertical de anuncios) compone las cuatro
 * fases —{@code LevantamientoCompletadoPort}, {@code PermisoAprobadoPort} e
 * {@code InstalacionCompletadaPort}— para el giro {@code anuncios-luminosos}.
 * {@code ServicioProyectos} elige el adaptador segun el {@code PerfilFasesGiro}
 * resuelto por {@code PerfilFasesGiroPort}. El estado consolidado del Proyecto es
 * una <strong>funcion pura</strong> del dominio
 * ({@code DerivacionEstadoProyecto}) sobre el avance de sus Sitios, plenamente
 * comprobable sin infraestructura.</p>
 *
 * <p><strong>Derivacion de la fase Orden_Fabricacion:</strong> como la
 * Orden_Fabricacion se vincula a una Cotizacion (no a un Sitio) y no existe
 * {@code of.sitio_id}, la fase "Orden_Fabricacion terminada para el Sitio" se
 * deriva de la existencia de una Orden_Trabajo_Instalacion para el Sitio (una OTI
 * solo se programa desde una OF terminada, Req 19.1/19.2). Es una decision de
 * modelado deliberada para no inventar una columna {@code of.sitio_id}.</p>
 *
 * <h2>Organizacion (arquitectura hexagonal)</h2>
 * <ul>
 *   <li>{@code domain}: {@code Proyecto} (raiz de agregado), {@code Sitio}
 *       (entidad hija), {@code AvanceFasesSitio} (objeto de valor puro),
 *       {@code EstadoConsolidadoProyecto} (enum) y {@code DerivacionEstadoProyecto}
 *       (funcion pura de derivacion, Req 21.4).</li>
 *   <li>{@code application}: {@code ServicioProyectos} (casos de uso), sus DTOs y
 *       comandos, y los puertos {@code AvanceSitioPort} y
 *       {@code PerfilFasesGiroPort}.</li>
 *   <li>{@code adapter.in.rest}: {@code ProyectoController} y sus DTOs de peticion.</li>
 *   <li>{@code adapter.out}: {@code PerfilFasesGiroAdapter} (resuelve el perfil via
 *       {@code GiroEmpresaPort}).</li>
 *   <li>{@code adapter.out.persistence}: {@code ProyectoRepository},
 *       {@code SitioRepository} y {@code AvanceProduccionAdapter} (avance del Nucleo,
 *       solo produccion). El adaptador de las cuatro fases
 *       ({@code AvanceSitioAnunciosAdapter}) vive en el vertical de anuncios.</li>
 * </ul>
 *
 * <h2>Autorizacion y multi-tenant</h2>
 * <p>Los permisos {@code proyecto:{crear,leer,listar,actualizar}} ya se sembraron
 * en V5 (roles ventas/supervisor/gerente). No existe recurso {@code sitio}: las
 * operaciones de Sitio se autorizan con los permisos de {@code proyecto} (el Sitio
 * se gestiona como parte del agregado Proyecto). {@code Proyecto} y {@code Sitio}
 * extienden {@code TenantScopedEntity}; el aislamiento se refuerza con RLS (V25).</p>
 */
package com.dessti.crm.operacion.proyecto;

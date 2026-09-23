/**
 * Modulo <strong>portalcliente</strong>: el Portal del Cliente, una fachada de
 * acceso <em>externo y restringido</em> para el rol {@code cliente_portal}
 * (Req 45). Da a un Cliente acceso de solo su propia relacion comercial
 * (Cotizaciones, Prueba_Diseno, avance de Proyectos/Sitios, Ticket_Servicio y
 * Facturas) y una unica accion de escritura acotada: aprobar o rechazar sus
 * propias Prueba_Diseno.
 *
 * <p>Sigue la arquitectura hexagonal del proyecto:</p>
 * <ul>
 *   <li>{@code application}: {@link com.dessti.crm.portalcliente.application.ServicioPortalCliente}
 *       (casos de uso del Portal, todos acotados al Cliente actual) y el puerto
 *       {@link com.dessti.crm.portalcliente.application.ClientePortalActualPort}
 *       que resuelve el Cliente del usuario del Portal.</li>
 *   <li>{@code adapter.out.security}:
 *       {@code ClientePortalActualDesdeAuthenticationAdapter}, implementacion por
 *       defecto ({@code @ConditionalOnMissingBean}) que resuelve el Cliente del
 *       principal autenticado a partir de una authority {@code cliente_id:<uuid>}.</li>
 *   <li>{@code adapter.in.rest}:
 *       {@link com.dessti.crm.portalcliente.adapter.in.rest.PortalClienteController}
 *       con las rutas {@code /portal/**}, protegidas con el rol
 *       {@code cliente_portal}.</li>
 * </ul>
 *
 * <h2>Alcance por Cliente, aislamiento y auditoria</h2>
 * <p>El Portal <em>nunca</em> acepta un {@code clienteId} de la peticion: cada
 * consulta se filtra por el Cliente resuelto del contexto de seguridad (Req 45.1,
 * 45.3). El {@code tenant_id} lo aporta el {@code TenantContext} y lo refuerza la
 * RLS (Req 45.4, 23). Cada accion del Cliente se registra en la bitacora de
 * auditoria (Req 45.5) y los listados son paginados (20/100, Req 45.6).</p>
 *
 * <h2>Reutilizacion de otros modulos por PUERTOS (tarea 45.1; refactor multigiro 8.1)</h2>
 * <p>Para evitar duplicar logica y respetar la independencia entre modulos, el
 * Portal consume la Cotizacion y la Factura del Nucleo por sus puertos
 * ({@code CotizacionConsultaPort} de {@code comercial.cotizacion} y el puerto de
 * facturacion), todos acotados por tenant. Con la conversion de anuncios en el
 * primer vertical enchufable, el Portal <strong>ya NO depende de las clases
 * concretas del vertical</strong> (Prueba_Diseno, Ticket_Servicio de
 * Mantenimiento y Proyecto/Sitio, hoy en {@code com.dessti.crm.vertical.anuncios}):
 * en su lugar define e invoca sus propios puertos
 * ({@code ResumenPruebasDisenoPort}, {@code ResumenTicketsPort} y
 * {@code ResumenProyectosPort}) que el vertical implementa, invirtiendo la
 * dependencia (Req 10.5). La aprobacion/rechazo de una Prueba_Diseno se delega en
 * el puerto {@code ResumenPruebasDisenoPort} tras verificar la propiedad de la
 * prueba por el Cliente. Este modulo NO depende del modulo {@code social}.</p>
 */
package com.dessti.crm.portalcliente;

/**
 * Limitacion de tasa por IP para la API (Req 2.4).
 *
 * <p>Contiene un limitador en memoria por ventana fija ({@code LimitadorTasaPorIp}),
 * el filtro que lo aplica a las rutas de autenticacion ({@code FiltroLimiteTasa}),
 * la resolucion de la IP de origen considerando el Proxy_Inverso
 * ({@code ResolvedorIpCliente}) y su configuracion. El exceso de peticiones se
 * traduce a HTTP 429 con formato Problem Details a traves del manejador global de
 * errores.</p>
 *
 * <p><strong>Alcance single-node:</strong> el estado es por instancia de la
 * aplicacion; en el despliegue on-premise inicial hay un unico nodo.</p>
 */
package com.dessti.crm.platform.security.ratelimit;

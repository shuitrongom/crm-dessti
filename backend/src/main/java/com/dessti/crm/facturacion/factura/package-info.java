/**
 * Submodulo <strong>factura</strong> del modulo facturacion-cfdi: emision de
 * Facturas (CFDI 4.0), calculo fiscal (IVA 16% y retenciones), timbrado y
 * cancelacion ante el PAC, y su maquina de estados (Req 34, 35). Sigue la
 * arquitectura hexagonal con los paquetes {@code domain} (raiz del agregado
 * {@code Factura}, {@code EstadoFactura} y su maquina de estados pura,
 * {@code CalculoFiscalCfdi} y {@code DatosFiscalesReceptor}), {@code application}
 * (casos de uso, DTO y puertos de lectura hacia Cotizacion/Orden_Fabricacion),
 * {@code adapter.out.persistence} (repositorio JPA y adaptadores de los puertos de
 * lectura) y {@code adapter.in.rest} (controlador REST).
 *
 * <p><em>Consume</em> el {@code PacPort} del paquete
 * {@code com.dessti.crm.facturacion.application} para timbrar/cancelar los CFDI de
 * forma desacoplada (Req 35.8), y lee la Cotizacion aprobada u Orden_Fabricacion de
 * origen a traves de puertos propios para preservar la arquitectura hexagonal.</p>
 */
package com.dessti.crm.facturacion.factura;

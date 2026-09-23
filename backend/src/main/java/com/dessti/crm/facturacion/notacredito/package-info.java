/**
 * Submodulo <strong>notacredito</strong> del modulo facturacion-cfdi: emision de
 * Notas de Credito (CFDI de egreso) que referencian una Factura timbrada, con su
 * monto acotado por el saldo disponible de la Factura y su timbrado ante el PAC
 * (Req 37). Sigue la arquitectura hexagonal con los paquetes {@code domain} (raiz
 * del agregado {@code NotaCredito}, {@code EstadoNotaCredito} y su maquina de
 * estados pura, y {@code NotaCreditoValidaciones}), {@code application} (casos de
 * uso, DTO y el puerto de lectura de la Factura referenciada),
 * {@code adapter.out.persistence} (repositorio JPA y adaptador del puerto) y
 * {@code adapter.in.rest} (controlador REST).
 *
 * <p>El tope del monto se calcula como {@code total - Σ notas previas no
 * canceladas} (Req 37.2); la disminucion efectiva de la Cuenta_Por_Cobrar (Req
 * 37.1) se completa en el bloque 29 (modulo contabilidad-finanzas). <em>Consume</em>
 * el {@code PacPort} para timbrar el CFDI de egreso (Req 35.8).</p>
 */
package com.dessti.crm.facturacion.notacredito;

/**
 * Capa de aplicacion del submodulo Cuentas_Por_Pagar (CxP) y Programacion_Pago
 * (Req 42).
 *
 * <p>Expone el servicio
 * {@link com.dessti.crm.contabilidad.cxp.application.ServicioCuentasPorPagar} que
 * registra la CxP al conciliar una Factura_Proveedor (idempotente), crea
 * Programaciones de Pago, aplica pagos acotados por el saldo (Property 15) marcando
 * la Factura_Proveedor como pagada al liquidar (Req 42.3), calcula la antiguedad de
 * saldos por Proveedor (Req 42.5) y lista con filtros (Req 42.6). Declara dos
 * puertos hexagonales:
 * {@link com.dessti.crm.contabilidad.cxp.application.CuentaPorPagarPort} (entrada,
 * que compras invoca al conciliar) y
 * {@link com.dessti.crm.contabilidad.cxp.application.FacturaProveedorPagablePort}
 * (salida, que compras implementa para marcar la factura pagada), manteniendo la
 * dependencia entre modulos aciclica a nivel de interfaz. Los comandos y DTOs son
 * distintos de las entidades de persistencia (Req 12.2).</p>
 */
package com.dessti.crm.contabilidad.cxp.application;

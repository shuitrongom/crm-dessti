/**
 * Submodulo <strong>canalventa</strong> del modulo comercial-crm: catalogo de
 * canales de venta y clasificacion comercial (Req 63; tarea 17.3). Sigue la
 * arquitectura hexagonal del proyecto con los paquetes {@code domain} (entidad
 * {@code CanalVenta} y sus validaciones), {@code application} (casos de uso, DTOs
 * y el puerto {@code CanalVentaExistentePort}), {@code adapter.out.persistence}
 * (repositorio JPA) y {@code adapter.in.rest} (controlador REST).
 *
 * <p>El Canal_Venta clasifica de forma OPCIONAL las Oportunidades (Req 14) y las
 * Cotizaciones (Req 6) mediante una FK nullable {@code canal_venta_id} (V15,
 * Req 63.1). La asignacion/modificacion del canal se realiza desde los servicios
 * de Oportunidad/Cotizacion y se audita (Req 63.3). El dato queda persistido e
 * indexado para que los reportes comerciales de los bloques 44/48 (Req 22, 48)
 * puedan segmentar por canal (Req 63.2).</p>
 */
package com.dessti.crm.comercial.canalventa;

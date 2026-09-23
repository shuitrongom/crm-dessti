/**
 * Paquete neutral del Núcleo que aloja los puertos de solo lectura relativos al
 * Cliente que consumen los submódulos de Núcleo de {@code operacion} (producción
 * y proyecto), sin acoplarlos entre sí ni a la persistencia del Cliente.
 *
 * <p>Contiene {@link com.dessti.crm.operacion.cliente.application.ClienteExistentePort},
 * consumido por {@code ServicioOrdenesFabricacion.crearDirecta} (producción) y por
 * {@code ServicioProyectos.crear} (proyecto). El adaptador que lo implementa vive
 * en el módulo <strong>comercial</strong> (donde reside la entidad Cliente),
 * respetando la arquitectura hexagonal: el puerto es una interfaz estable del
 * Núcleo y comercial provee la implementación de infraestructura (Req 4.1, 1.5,
 * 16.4).</p>
 */
package com.dessti.crm.operacion.cliente.application;

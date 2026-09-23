/**
 * Nucleo de verticales enchufables (plugins de giro) de la plataforma multigiro.
 *
 * <p>Este paquete es <strong>Nucleo Comun</strong> y define exclusivamente el
 * <em>contrato</em> por el que cada Modulo-Vertical se enchufa al Nucleo, mas el
 * componente de descubrimiento y registro. Traza la frontera hexagonal-pura del
 * spec {@code plataforma-multigiro}: el Nucleo depende unicamente del puerto y
 * nunca de una implementacion concreta de vertical (Req 4.1, 4.2).</p>
 *
 * <p>Contenido:</p>
 * <ul>
 *   <li>{@link com.dessti.crm.platform.vertical.ContratoVertical} - puerto de
 *       entrada que cada vertical implementa para declarar su Giro, los modulos
 *       y recursos RBAC que aporta y su metadato de navegacion.</li>
 *   <li>{@link com.dessti.crm.platform.vertical.ItemNavegacionVertical} - record
 *       de metadato de navegacion expuesto por un vertical.</li>
 * </ul>
 *
 * <p><strong>Regla de dependencias (verificada con ArchUnit, Req 4.2/5.4):</strong>
 * {@code platform.vertical} NO importa {@code com.dessti.crm.vertical.*}: solo
 * define el contrato. Son las implementaciones concretas (p. ej.
 * {@code vertical.anuncios}) las que dependen de este paquete, jamas al reves.</p>
 */
package com.dessti.crm.platform.vertical;

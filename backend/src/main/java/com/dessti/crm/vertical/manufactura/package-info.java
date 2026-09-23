/**
 * Modulo-Vertical de <strong>demostracion</strong> del giro
 * <strong>{@code manufactura}</strong> (Req 12.1, 12.2, 12.3).
 *
 * <p>Este paquete es la <strong>prueba del modelo enchufable</strong>: demuestra
 * que un <em>segundo</em> Giro encaja en el {@code ContratoVertical} del Nucleo
 * <strong>sin tocar el Vertical_Anuncios ni el Nucleo_Comun</strong> (Req 12.2).
 * Su unico proposito es evidenciar que la plataforma escala a N giros como
 * hermanos sobre el mismo Nucleo, no ofrecer un vertical funcional completo.</p>
 *
 * <p><strong>Esqueleto de demostracion, no vertical funcional (decision de
 * alcance).</strong> Se implementa deliberadamente como un ESQUELETO:</p>
 * <ul>
 *   <li>{@link com.dessti.crm.vertical.manufactura.ManufacturaVertical} implementa
 *       el {@code ContratoVertical} declarando el Giro {@code manufactura}, las
 *       claves de modulo, los recursos RBAC y la navegacion propios del giro, de
 *       modo que el {@code RegistroVerticales} lo descubre e indexa en el arranque
 *       exactamente igual que a {@code AnunciosVertical} (Req 12.1).</li>
 *   <li>Las entidades de dominio de demostracion
 *       ({@link com.dessti.crm.vertical.manufactura.domain.Bom} y
 *       {@link com.dessti.crm.vertical.manufactura.domain.OrdenProduccion}) son
 *       <strong>dominio puro</strong> (records inmutables), <strong>SIN</strong>
 *       anotacion {@code @Entity}, sin tabla ni migracion de negocio y sin
 *       repositorio JPA. Bastan para evidenciar la ESTRUCTURA del vertical (BOM,
 *       Orden_Produccion) sin exigir esquema de base de datos, ya que el objetivo
 *       es probar el encaje del contrato, no un CRUD completo.</li>
 * </ul>
 *
 * <p><strong>Consumo del Nucleo solo por puertos (Req 12.3).</strong> Igual que el
 * Vertical_Anuncios, este vertical consume las capacidades del Nucleo Comun
 * exclusivamente a traves de puertos del Nucleo (p. ej.
 * {@code com.dessti.crm.comercial.cotizacion.application.CotizacionConsultaPort}),
 * y <strong>NUNCA</strong> accede a las clases internas de persistencia del Nucleo
 * ni de otro vertical.</p>
 *
 * <p><strong>Independencia entre verticales (Req 12.2, 4.6).</strong> Este paquete
 * <strong>NO</strong> depende de {@code com.dessti.crm.vertical.anuncios} ni
 * viceversa; ambos son hermanos que solo dependen del Nucleo y del
 * {@code ContratoVertical}. La regla se verifica con ArchUnit (tarea 12.2).</p>
 *
 * <p>Trazabilidad: Req 12.1 (segundo Giro que encaja en el Contrato de Vertical
 * con entidades propias de demostracion), Req 12.2 (registro por el Giro
 * {@code manufactura} sin modificar anuncios ni el Nucleo), Req 12.3 (consumo del
 * Nucleo solo por puertos).</p>
 */
package com.dessti.crm.vertical.manufactura;

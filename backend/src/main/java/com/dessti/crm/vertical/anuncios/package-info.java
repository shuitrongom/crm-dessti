/**
 * Modulo-Vertical del giro <strong>{@code anuncios-luminosos}</strong>, primer
 * vertical enchufable de la plataforma multigiro (Req 10).
 *
 * <p>Este paquete agrupa el flujo y las entidades <em>especificos</em> del giro
 * de anuncios luminosos —Prueba_Diseno, Orden_Fabricacion, Levantamiento_Sitio,
 * Permiso_Instalacion, Orden_Trabajo_Instalacion, Cuadrilla y la parte de
 * Mantenimiento/Proyecto-Sitio propia del vertical (Req 10.1)—, dejando el
 * Nucleo Comun limpio de codigo especifico de anuncios (Req 10.5).</p>
 *
 * <p>El vertical se enchufa al Nucleo <strong>exclusivamente</strong> a traves
 * del puerto {@link com.dessti.crm.platform.vertical.ContratoVertical}: declara
 * su Giro, las claves de modulo que aporta, sus recursos RBAC atomicos y su
 * metadato de navegacion (Req 10.2, 4.1). El
 * {@code RegistroVerticales} descubre su implementacion
 * ({@link com.dessti.crm.vertical.anuncios.AnunciosVertical}) en el arranque y
 * la indexa por su clave de Giro.</p>
 *
 * <p>Conforme al caracter hexagonal-puro del contrato, el vertical
 * <strong>consume las capacidades del Nucleo (Cliente, Cotizacion, Facturacion,
 * Inventario base y demas) unicamente por puertos del Nucleo</strong> y no
 * accede a clases internas de persistencia del Nucleo ni de otro vertical
 * (Req 10.3, 4.5). Tampoco declara dependencias de compilacion hacia otro
 * Modulo-Vertical (Req 4.6).</p>
 *
 * <p><strong>Alcance actual:</strong> este paquete parte con el metadato del
 * vertical ({@code AnunciosVertical}). La <em>migracion fisica</em> de los
 * flujos concretos (Prueba_Diseno, Orden_Fabricacion, Levantamiento_Sitio,
 * Permiso_Instalacion, Orden_Trabajo_Instalacion, Cuadrilla, Mantenimiento y
 * Proyecto-Sitio) desde el Nucleo hacia aqui se realiza en las tareas 8.2 y
 * 8.3; hasta entonces dichos flujos permanecen en sus paquetes actuales del
 * Nucleo.</p>
 *
 * <p>Trazabilidad: Req 10 (extraccion del Vertical_Anuncios), Req 4 (Contrato
 * de Vertical), Req 5 (frontera Nucleo/Vertical).</p>
 */
package com.dessti.crm.vertical.anuncios;

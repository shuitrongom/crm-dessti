/**
 * Catalogo unificado de modulos de la plataforma multigiro. NUCLEO de plataforma.
 *
 * <p>Compone, en modo <strong>solo lectura</strong>, el conjunto de claves de
 * modulo que la plataforma reconoce, unificando dos fuentes de verdad reales y
 * sin inventar claves:</p>
 * <ul>
 *   <li>Los modulos del <strong>Nucleo Comun</strong>: las entradas del catalogo
 *       maestro {@code catalogo_modulo}
 *       ({@link com.dessti.crm.platform.monetizacion.domain.CatalogoModulo},
 *       sembrado en V22) que no pertenecen a ningun vertical; su Giro es
 *       {@code null} (transversales a todo Giro).</li>
 *   <li>Los modulos de <strong>vertical</strong>: las claves que declara cada
 *       {@code ContratoVertical} en su {@code modulos()}, atribuidas a su Giro por
 *       el {@link com.dessti.crm.platform.vertical.RegistroVerticales}.</li>
 * </ul>
 *
 * <p>Lo consume el frontend (editor de Planes y alta de Empresas) para pintar
 * checkboxes reales de modulos habilitados en lugar de un campo de texto libre.
 * Sigue el patron de {@code platform.giros}: servicio de aplicacion + DTO
 * inmutable + adaptador REST en el subpaquete {@code rest}.</p>
 */
package com.dessti.crm.platform.modulos;

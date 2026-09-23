package com.dessti.crm.platform.modulos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de salida de una entrada del catalogo de modulos de la plataforma, tal
 * como lo consume el editor de Planes y el alta de Empresas del frontend para
 * pintar los checkboxes de modulos habilitados y, ademas, cotizar el Plan
 * (suma de precios de modulo) en la moneda principal.
 *
 * <p>Es un {@code record} inmutable, distinto de cualquier entidad de
 * persistencia, siguiendo el patron de {@code platform.giros.GiroDto}.</p>
 *
 * @param clave            clave canonica del modulo (minusculas), la MISMA que se
 *                         persiste en {@code plan.modulos_habilitados} y
 *                         {@code suscripcion.modulos_habilitados}.
 * @param nombreVisible    etiqueta humana en espanol del modulo.
 * @param giro             clave del Giro (vertical) que aporta el modulo, o
 *                         {@code null} si el modulo es del <strong>Nucleo Comun</strong>
 *                         (transversal a todo Giro).
 * @param catalogoModuloId id del {@code catalogo_modulo} para esa clave cuando
 *                         existe en el catalogo maestro; {@code null} para claves
 *                         solo de vertical no presentes en {@code catalogo_modulo}
 *                         (p. ej. {@code produccion-industrial}). El frontend usa
 *                         este id para fijar el precio via
 *                         {@code PUT /precios-modulo/modulos/{moduloId}}.
 * @param precio           precio de LISTA del modulo en la moneda principal (de
 *                         {@code precio_modulo}), o {@code null} si aun no hay
 *                         precio definido (la UI muestra "sin precio").
 * @param monedaCodigo     codigo ISO 4217 de la moneda PRINCIPAL (el mismo para
 *                         todas las entradas), para que la UI muestre y sume en ella.
 */
public record ModuloCatalogoDto(String clave, String nombreVisible, String giro,
                                UUID catalogoModuloId, BigDecimal precio, String monedaCodigo) {
}

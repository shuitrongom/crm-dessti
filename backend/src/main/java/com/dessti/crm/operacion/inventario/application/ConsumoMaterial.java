package com.dessti.crm.operacion.inventario.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linea de consumo de un Material por una Orden_Fabricacion (Req 18.4): indica que
 * cantidad de un Material concreto debe descontarse del inventario al fabricar. Es un
 * objeto de valor de entrada del {@link ConsumoMaterialPort}.
 *
 * @param materialId identificador del Material a consumir; obligatorio.
 * @param cantidad   cantidad a descontar (positiva); obligatoria.
 */
public record ConsumoMaterial(UUID materialId, BigDecimal cantidad) {
}

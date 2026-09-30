package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de aplicacion para ajustar el inventario de un Material en un Almacen por conteo
 * fisico (Req 60). Transporta los datos de entrada del caso de uso, desacoplados del
 * contrato REST y de la entidad JPA.
 *
 * <p>El servicio calcula la diferencia entre {@code cantidadContada} y el saldo vigente y
 * genera una ENTRADA o SALIDA de tipo {@code ajuste} por esa diferencia (o ningun
 * movimiento si coinciden), conservando el costo promedio vigente.</p>
 *
 * @param almacenId       Almacen donde se realiza el conteo fisico; obligatorio.
 * @param materialId      Material a ajustar; obligatorio.
 * @param cantidadContada cantidad real contada; obligatoria y &gt;= 0.
 * @param motivo          nota/justificacion del ajuste; opcional.
 */
public record AjustarInventarioCommand(
        UUID almacenId,
        UUID materialId,
        BigDecimal cantidadContada,
        String motivo) {
}

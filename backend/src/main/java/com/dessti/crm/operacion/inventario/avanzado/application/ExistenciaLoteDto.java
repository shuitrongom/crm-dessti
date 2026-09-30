package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de salida de la EXISTENCIA VIVA por Lote de un Material (Req 60), derivada del Kardex
 * append-only por Almacen. Combina la identidad y caducidad del {@code Lote} con la cantidad
 * neta disponible en un Almacen dado. Es de SOLO LECTURA: no existe una tabla materializada
 * de existencia por lote; esta cifra se agrega en el servidor sobre {@code movimiento_almacen}.
 *
 * @param loteId           identificador del Lote.
 * @param codigo           codigo del Lote (identidad de negocio).
 * @param almacenId        Almacen donde se encuentra la existencia.
 * @param cantidad         cantidad neta disponible del Lote en el Almacen (entradas - salidas).
 * @param fechaCaducidad   fecha de caducidad del Lote, o {@code null} si no aplica.
 * @param fechaFabricacion fecha de fabricacion/recepcion, o {@code null} (V86).
 */
public record ExistenciaLoteDto(
        UUID loteId,
        String codigo,
        UUID almacenId,
        BigDecimal cantidad,
        LocalDate fechaCaducidad,
        LocalDate fechaFabricacion) {
}

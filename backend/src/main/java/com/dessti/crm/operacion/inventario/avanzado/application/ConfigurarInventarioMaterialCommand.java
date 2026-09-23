package com.dessti.crm.operacion.inventario.avanzado.application;

import java.math.BigDecimal;

/**
 * Comando de aplicacion para configurar (upsert) el inventario avanzado de un Material
 * (Req 60): metodo de costeo, stock maximo, control de lote y los parametros del punto
 * de reorden (consumo promedio, tiempo de entrega y stock de seguridad). Transporta los
 * datos de entrada del caso de uso, desacoplados del contrato REST y de la entidad JPA.
 * La validacion de dominio la refuerza {@code ConfigInventarioMaterial.actualizar}.
 *
 * @param metodoCosteo      etiqueta del metodo de costeo ({@code promedio}/{@code peps}); obligatoria.
 * @param stockMaximo       stock maximo; {@code null} (sin tope) o &gt;= 0.
 * @param controlLote       {@code true} si el Material controla lotes.
 * @param consumoPromedio   consumo promedio por dia; obligatorio y &gt;= 0.
 * @param tiempoEntregaDias tiempo de entrega en dias; &gt;= 0.
 * @param stockSeguridad    stock de seguridad; obligatorio y &gt;= 0.
 */
public record ConfigurarInventarioMaterialCommand(
        String metodoCosteo,
        BigDecimal stockMaximo,
        boolean controlLote,
        BigDecimal consumoPromedio,
        int tiempoEntregaDias,
        BigDecimal stockSeguridad) {
}

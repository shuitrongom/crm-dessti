package com.dessti.crm.operacion.produccion.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comando de aplicacion para crear una Orden_Fabricacion por el <strong>origen
 * generico</strong> (Req 1.1, 1.2, §A1): una OF asociada directamente a un Cliente
 * (sin Cotizacion) con su lista de partidas iniciales (Material + cantidad).
 *
 * <p>Es un DTO de entrada de la capa de aplicacion, distinto de los DTOs REST y de
 * las entidades JPA. El {@code tenant_id} y el actor no viajan aqui: se derivan del
 * {@link com.dessti.crm.platform.tenant.TenantContext}/contexto de seguridad
 * (Req 23.4).</p>
 *
 * <h2>Partidas iniciales y limite de esta tarea (1.8)</h2>
 * <p>El comando <strong>ya transporta</strong> las partidas para que el contrato del
 * endpoint {@code POST /ordenes-fabricacion/directa} sea estable, pero la
 * persistencia de la tabla {@code partida_orden_fabricacion} y la verificacion de
 * existencia de cada Material (404) pertenecen a la tarea 3.1 (§B1). En esta tarea
 * {@code ServicioOrdenesFabricacion.crearDirecta} persiste unicamente la
 * Orden_Fabricacion; el guardado de {@link #partidas()} es el punto de extension que
 * cableara 3.1 sin cambiar esta firma.</p>
 *
 * @param clienteId Cliente al que se asocia la OF; obligatorio (nulo &rarr; 422,
 *                  Req 1.4).
 * @param partidas  partidas iniciales de consumo (Material + cantidad); puede estar
 *                  vacia. Nunca {@code null} (el constructor la normaliza a lista
 *                  vacia inmutable).
 */
public record CrearOrdenDirectaCommand(UUID clienteId, List<PartidaInicial> partidas) {

    public CrearOrdenDirectaCommand {
        partidas = (partidas == null) ? List.of() : List.copyOf(partidas);
    }

    /**
     * Partida inicial de consumo de la OF directa: un Material y una cantidad
     * positiva. La validacion de cantidad &gt; 0 (422) y de accesibilidad del
     * Material (404) la aplicara la tarea 3.1 al persistir las partidas.
     *
     * @param materialId identificador del Material a consumir.
     * @param cantidad   cantidad a consumir; debe ser positiva (validado en 3.1).
     */
    public record PartidaInicial(UUID materialId, BigDecimal cantidad) {
    }
}

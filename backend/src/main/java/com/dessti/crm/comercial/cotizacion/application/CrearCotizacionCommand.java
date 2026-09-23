package com.dessti.crm.comercial.cotizacion.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comando de creacion de una {@link com.dessti.crm.comercial.cotizacion.domain.Cotizacion}
 * (Req 6.1, 6.2). Objeto de entrada de la capa de aplicacion, distinto de la
 * entidad.
 *
 * <p><strong>Regla de seguridad (Req 23.4):</strong> no incluye el
 * {@code tenant_id}; el tenant se deriva del contexto autenticado.</p>
 *
 * <p>Ademas de los datos basicos, transporta los datos descriptivos OPCIONALES de
 * la Cotizacion (V60): fecha de vigencia, condiciones, notas y moneda. El folio y
 * la fecha de emision NO se reciben del cliente: la aplicacion los asigna
 * (folio consecutivo por tenant/anio, fecha de emision = hoy segun el Clock).</p>
 *
 * @param clienteId   identificador del Cliente existente; obligatorio (Req 6.1).
 * @param partidas    partidas de la Cotizacion; entre 1 y 500 (Req 6.1, 6.2).
 * @param validoHasta fecha de vigencia; opcional, &ge; fecha de emision (V60).
 * @param condiciones terminos y condiciones; opcional (max. 2000, V60).
 * @param notas       notas libres; opcional (max. 2000, V60).
 * @param moneda      moneda ISO 4217; {@code null}/blanco usa {@code MXN} (V60).
 */
public record CrearCotizacionCommand(
        UUID clienteId,
        List<CrearPartidaCommand> partidas,
        LocalDate validoHasta,
        String condiciones,
        String notas,
        String moneda) {

    /**
     * Constructor de conveniencia sin datos descriptivos (todos ausentes): fija
     * la fecha de vigencia, condiciones, notas y moneda a sus valores por defecto
     * (moneda {@code MXN} la aplica el dominio). Preserva el contrato previo a V60.
     *
     * @param clienteId identificador del Cliente existente; obligatorio.
     * @param partidas  partidas de la Cotizacion; entre 1 y 500.
     */
    public CrearCotizacionCommand(UUID clienteId, List<CrearPartidaCommand> partidas) {
        this(clienteId, partidas, null, null, null, null);
    }
}

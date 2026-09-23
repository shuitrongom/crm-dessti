package com.dessti.crm.vertical.anuncios.mantenimiento.application;

import java.util.UUID;

/**
 * Comando de aplicacion para registrar un Contrato_Mantenimiento (Req 20.1).
 * Transporta los datos ya validados en el borde REST hacia
 * {@link ServicioMantenimiento#crearContrato(CrearContratoCommand)}; el tipo viaja
 * como etiqueta de negocio ({@code preventivo}/{@code correctivo}) que la capa de
 * aplicacion interpreta.
 *
 * @param clienteId          Cliente al que se asocia el contrato; obligatorio.
 * @param tipo               etiqueta del tipo ({@code preventivo}/{@code correctivo}).
 * @param slaRespuestaHoras  tiempo de respuesta del SLA en horas; debe ser > 0.
 * @param slaResolucionHoras tiempo de resolucion del SLA en horas; debe ser > 0.
 */
public record CrearContratoCommand(
        UUID clienteId,
        String tipo,
        int slaRespuestaHoras,
        int slaResolucionHoras) {
}

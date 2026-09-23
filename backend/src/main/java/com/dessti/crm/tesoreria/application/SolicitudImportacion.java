package com.dessti.crm.tesoreria.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Solicitud de importacion de un estado de cuenta bancario a traves del
 * {@link ImportacionBancariaPort} (Req 43.2). Record inmutable del contrato del
 * puerto, sin tipos de dominio ni de persistencia.
 *
 * <p>El {@code tenantId} viaja explicito para que un adaptador real (archivo/API)
 * lo use como contexto de la importacion; la aplicacion, no obstante, deriva el
 * tenant del {@code TenantContext} al persistir (Req 23.4). La
 * {@code referenciaArchivo} identifica el origen (nombre de archivo, URL o
 * identificador de la API); {@code movimientos} permite, para el stub y las pruebas,
 * proveer directamente las lineas a importar (puede ser {@code null} o vacio si el
 * adaptador las obtiene de la fuente).</p>
 *
 * @param tenantId          Empresa a la que pertenece la importacion.
 * @param cuentaBancariaId  Cuenta_Bancaria destino del estado de cuenta; obligatorio.
 * @param referenciaArchivo referencia del origen (archivo/API); opcional.
 * @param periodoInicio     inicio del periodo del estado de cuenta; obligatorio.
 * @param periodoFin        fin del periodo del estado de cuenta; obligatorio.
 * @param movimientos       lineas a importar provistas por el solicitante; opcional
 *                          (el adaptador real puede obtenerlas de la fuente).
 */
public record SolicitudImportacion(
        UUID tenantId,
        UUID cuentaBancariaId,
        String referenciaArchivo,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        List<MovimientoImportado> movimientos) {
}

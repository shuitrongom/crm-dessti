package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando de aplicacion para programar una Orden_Trabajo_Instalacion a partir de
 * una Orden_Fabricacion terminada (Req 19.1). Es un objeto de entrada de la capa
 * de aplicacion, independiente del contrato REST; el {@code clienteId} NO forma
 * parte del comando: la aplicacion lo denormaliza desde la Orden_Fabricacion (via
 * {@code OrdenFabricacionTerminadaPort}) para el filtro del Req 19.7.
 *
 * <p>Las precondiciones (Orden_Fabricacion terminada Req 19.2; Levantamiento_Sitio
 * completado y Permiso_Instalacion aprobado del Sitio Req 19.3) las verifica el
 * {@code ServicioOrdenesTrabajoInstalacion}.</p>
 *
 * @param ordenFabricacionId identificador de la Orden_Fabricacion terminada de
 *                           origen; obligatorio (Req 19.1).
 * @param sitioId            Sitio de la instalacion; obligatorio (Req 19.3).
 * @param cuadrillaId        Cuadrilla asignada; obligatoria (Req 19.1).
 * @param fechaProgramada    fecha programada de la instalacion; obligatoria (Req 19.1).
 */
public record ProgramarOrdenTrabajoInstalacionCommand(
        UUID ordenFabricacionId,
        UUID sitioId,
        UUID cuadrillaId,
        LocalDate fechaProgramada) {
}

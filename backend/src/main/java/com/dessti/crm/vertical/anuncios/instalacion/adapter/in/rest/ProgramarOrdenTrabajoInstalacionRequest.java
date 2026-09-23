package com.dessti.crm.vertical.anuncios.instalacion.adapter.in.rest;

import java.time.LocalDate;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.application.ProgramarOrdenTrabajoInstalacionCommand;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de la peticion para programar una Orden_Trabajo_Instalacion a partir de
 * una Orden_Fabricacion terminada (Req 19.1). DTO de entrada del contrato REST,
 * distinto de la entidad JPA. Las precondiciones (Orden_Fabricacion terminada
 * Req 19.2; Levantamiento_Sitio completado y Permiso_Instalacion aprobado Req 19.3)
 * las aplica la capa de aplicacion. El Cliente NO se recibe: se denormaliza desde
 * la Orden_Fabricacion (Req 19.7).
 *
 * @param ordenFabricacionId identificador de la Orden_Fabricacion terminada de
 *                           origen; obligatorio.
 * @param sitioId            Sitio de la instalacion; obligatorio (Req 19.3).
 * @param cuadrillaId        Cuadrilla asignada; obligatoria (Req 19.1).
 * @param fechaProgramada    fecha programada de la instalacion; obligatoria (Req 19.1).
 */
public record ProgramarOrdenTrabajoInstalacionRequest(
        @NotNull UUID ordenFabricacionId,
        @NotNull UUID sitioId,
        @NotNull UUID cuadrillaId,
        @NotNull LocalDate fechaProgramada) {

    /**
     * Convierte esta peticion en el comando de aplicacion correspondiente.
     *
     * @return el {@link ProgramarOrdenTrabajoInstalacionCommand} equivalente.
     */
    public ProgramarOrdenTrabajoInstalacionCommand aComando() {
        return new ProgramarOrdenTrabajoInstalacionCommand(
                ordenFabricacionId, sitioId, cuadrillaId, fechaProgramada);
    }
}

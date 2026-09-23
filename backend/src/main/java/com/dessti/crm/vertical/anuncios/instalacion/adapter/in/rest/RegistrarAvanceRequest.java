package com.dessti.crm.vertical.anuncios.instalacion.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.dessti.crm.vertical.anuncios.instalacion.application.RegistrarAvanceCommand;

/**
 * Cuerpo de la peticion para registrar el avance de una Orden_Trabajo_Instalacion
 * (Req 19.4): agregar entradas de Lista_Pendientes, adjuntar evidencias
 * fotograficas y/o marcar pendientes como resueltos. DTO de entrada del contrato
 * REST, distinto de la entidad JPA. Todas las listas son opcionales; una lista nula
 * o vacia significa "sin cambios en ese eje".
 *
 * @param nuevosPendientes    descripciones de nuevas entradas de Lista_Pendientes
 *                            a agregar (Req 19.4); opcional.
 * @param evidencias          referencias (URL/clave) de evidencias fotograficas a
 *                            adjuntar (Req 19.4); opcional.
 * @param pendientesResueltos identificadores de pendientes a marcar como resueltos
 *                            (Req 19.4, 19.6); opcional.
 */
public record RegistrarAvanceRequest(
        List<String> nuevosPendientes,
        List<String> evidencias,
        List<UUID> pendientesResueltos) {

    /**
     * Convierte esta peticion en el comando de aplicacion correspondiente.
     *
     * @return el {@link RegistrarAvanceCommand} equivalente.
     */
    public RegistrarAvanceCommand aComando() {
        return new RegistrarAvanceCommand(nuevosPendientes, evidencias, pendientesResueltos);
    }
}

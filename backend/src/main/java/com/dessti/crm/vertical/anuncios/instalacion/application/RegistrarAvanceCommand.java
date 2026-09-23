package com.dessti.crm.vertical.anuncios.instalacion.application;

import java.util.List;
import java.util.UUID;

/**
 * Comando de aplicacion para registrar el avance de una Orden_Trabajo_Instalacion
 * (Req 19.4): agregar nuevas entradas de Lista_Pendientes, adjuntar evidencias
 * fotograficas y/o marcar pendientes como resueltos. Es un objeto de entrada de la
 * capa de aplicacion, independiente del contrato REST.
 *
 * <p>Todas las listas son opcionales: una lista nula o vacia significa "sin
 * cambios en ese eje". El servicio normaliza las listas nulas a listas vacias.</p>
 *
 * @param nuevosPendientes   descripciones de nuevas entradas de Lista_Pendientes a
 *                           agregar (Req 19.4); puede ser {@code null} o vacia.
 * @param evidencias         referencias (URL/clave) de evidencias fotograficas a
 *                           adjuntar (Req 19.4); puede ser {@code null} o vacia.
 * @param pendientesResueltos identificadores de pendientes a marcar como resueltos
 *                           (Req 19.4, 19.6); puede ser {@code null} o vacia.
 */
public record RegistrarAvanceCommand(
        List<String> nuevosPendientes,
        List<String> evidencias,
        List<UUID> pendientesResueltos) {
}

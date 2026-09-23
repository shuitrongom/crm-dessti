package com.dessti.crm.portalcliente.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto que el <strong>Portal del Cliente</strong> (Nucleo) publica para consumir
 * el avance de Proyectos/Sitios del Modulo-Vertical de anuncios <em>por puerto</em>,
 * sin depender de sus clases concretas (servicio ni DTO del vertical). Invierte la
 * dependencia Nucleo&rarr;vertical: el Portal define el puerto y el vertical lo
 * implementa (Req 10.5, 4.5).
 */
public interface ResumenProyectosPort {

    /**
     * Lista de forma paginada los Proyectos del Cliente con la proyeccion de
     * resumen (sin derivar el avance por Proyecto, Req 45.1, 21.5).
     *
     * @param clienteId Cliente cuyos Proyectos se listan; obligatorio.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de resumenes de Proyecto del Cliente.
     */
    Page<ProyectoResumen> listarPorCliente(UUID clienteId, Pageable pageable);

    /**
     * Consulta el avance consolidado de un Proyecto del tenant vigente, incluyendo
     * sus Sitios con el avance por fase (Req 45.1, 21.4). El Portal usa el
     * {@code clienteId} del resultado como guarda de propiedad (Req 45.3).
     *
     * @param proyectoId identificador del Proyecto.
     * @return el resumen detallado del Proyecto (estado consolidado + Sitios).
     * @throws com.dessti.crm.platform.error.RecursoNoEncontradoException si el
     *         Proyecto no es accesible en el tenant (404, Req 23.3).
     */
    ProyectoResumen consultar(UUID proyectoId);
}

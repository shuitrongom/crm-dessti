package com.dessti.crm.portalcliente.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto que el <strong>Portal del Cliente</strong> (Nucleo) publica para consumir
 * el flujo de Prueba_Diseno del Modulo-Vertical de anuncios <em>por puerto</em>,
 * sin depender de sus clases concretas (repositorio, servicio, entidad ni DTO del
 * vertical). Invierte la dependencia Nucleo&rarr;vertical: el Portal define el
 * puerto y el vertical lo implementa (Req 10.5, 4.5).
 *
 * <p>Reune exactamente las capacidades que el Portal necesita hoy de las
 * Prueba_Diseno del Cliente (Req 45.1, 45.2): listar las del Cliente, resolver la
 * Cotizacion de una prueba (para la guarda de propiedad, Req 45.3) y decidirla
 * (aprobar/rechazar) delegando en la maquina de estados del vertical (Req 15).</p>
 */
public interface ResumenPruebasDisenoPort {

    /**
     * Lista de forma paginada las Prueba_Diseno del Cliente (las de sus
     * Cotizaciones), mas reciente primero (Req 45.1).
     *
     * @param clienteId Cliente cuyas pruebas se listan; obligatorio.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de resumenes de Prueba_Diseno del Cliente.
     */
    Page<PruebaDisenoResumen> listarPorCliente(UUID clienteId, Pageable pageable);

    /**
     * Resuelve la Cotizacion a la que pertenece una Prueba_Diseno del tenant
     * vigente, para la guarda de propiedad del Portal (Req 45.3).
     *
     * @param pruebaId identificador de la Prueba_Diseno; puede ser nulo.
     * @return el identificador de la Cotizacion de la prueba, o vacio si la prueba
     *         es nula, no existe o pertenece a otro tenant.
     */
    Optional<UUID> cotizacionDePrueba(UUID pruebaId);

    /**
     * Aprueba una Prueba_Diseno (Req 45.2). La guarda de propiedad ya la aplico el
     * Portal antes de invocar; aqui se delega en la maquina de estados del vertical
     * (409 si ya esta decidida, Req 15.4).
     *
     * @param pruebaId identificador de la Prueba_Diseno.
     * @return el resumen de la Prueba_Diseno aprobada.
     */
    PruebaDisenoResumen aprobar(UUID pruebaId);

    /**
     * Rechaza una Prueba_Diseno y genera automaticamente la siguiente version
     * pendiente (Req 45.2, 15.3). La guarda de propiedad ya la aplico el Portal.
     *
     * @param pruebaId identificador de la Prueba_Diseno a rechazar.
     * @return el resultado con la version rechazada y la nueva version pendiente.
     */
    ResultadoRechazoPruebaResumen rechazar(UUID pruebaId);
}

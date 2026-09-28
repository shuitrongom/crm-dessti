package com.dessti.crm.portalcliente.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de salida que el <strong>Portal del Cliente</strong> define para que un
 * Cliente pueda <em>levantar</em> una Queja_Cliente con origen {@code PORTAL} y
 * <em>consultar</em> sus propias quejas (Req 45, 70.1, 70.8). Lo implementa el modulo
 * {@code calidad}, invirtiendo la dependencia: el Portal (Nucleo) no conoce la
 * persistencia ni la maquina de estados del Sistema de Gestion de Calidad.
 *
 * <p>El {@code clienteId} lo resuelve siempre {@link ServicioPortalCliente} desde el
 * usuario del Portal (nunca de la peticion), de modo que un Cliente solo registra y
 * ve <strong>sus</strong> quejas.</p>
 */
public interface RegistroQuejaPortalPort {

    /**
     * Registra una Queja_Cliente con origen {@code PORTAL} para el Cliente indicado
     * (Req 70.1, 70.8). El estado inicial es {@code registrada}.
     *
     * @param clienteId   Cliente del Portal; obligatorio.
     * @param descripcion descripcion de la queja; obligatoria.
     * @return el resumen de la queja registrada.
     */
    QuejaPortalResumen registrarDesdePortal(UUID clienteId, String descripcion);

    /**
     * Lista de forma paginada las Queja_Cliente del Cliente indicado (Req 45.1, 45.6).
     *
     * @param clienteId Cliente del Portal; obligatorio.
     * @param pageable  parametros de paginacion ya acotados (20/100).
     * @return la pagina de quejas del Cliente como resumenes.
     */
    Page<QuejaPortalResumen> listarPorCliente(UUID clienteId, Pageable pageable);
}

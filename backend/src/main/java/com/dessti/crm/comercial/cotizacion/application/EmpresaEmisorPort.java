package com.dessti.crm.comercial.cotizacion.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que resuelve los <strong>datos fiscales del emisor</strong>
 * de una Cotizacion a partir de la Empresa (tenant) del contexto (V60, Req 1).
 *
 * <p>Se separa de la persistencia de plataforma para no acoplar el submodulo de
 * Cotizaciones a la entidad {@code Empresa}: el adaptador {@code EmpresaEmisorAdapter}
 * lee la Empresa por su id (= {@code tenant_id}) y la proyecta a un
 * {@link DatosEmisor}, sin exponer la entidad. Sigue el mismo patron hexagonal
 * que {@link DatosClientePort}.</p>
 */
public interface EmpresaEmisorPort {

    /**
     * Resuelve los datos del emisor (la Empresa) por su {@code tenant_id}, que el
     * llamador deriva SIEMPRE del contexto autenticado (Req 1.7), nunca de la
     * peticion.
     *
     * @param tenantId identificador del tenant (= id de la Empresa); puede ser
     *                 {@code null}.
     * @return los datos del emisor, o {@link Optional#empty()} si la Empresa no
     *         existe.
     */
    Optional<DatosEmisor> emisorDeTenant(UUID tenantId);
}

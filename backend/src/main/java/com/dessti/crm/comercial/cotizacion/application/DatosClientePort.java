package com.dessti.crm.comercial.cotizacion.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida que resuelve los <strong>datos visibles del Cliente</strong>
 * (nombre, RFC, correo) necesarios para enriquecer la Cotizacion: el DTO de
 * salida, el PDF PREMIUM y el envio por correo (V60, Req 5, 6).
 *
 * <p>Se separa de {@link ClienteExistentePort} (que solo comprueba existencia)
 * para mantener cada puerto enfocado, sin acoplar el submodulo de Cotizaciones a
 * la persistencia del de Clientes. El adaptador {@code DatosClienteAdapter}
 * delega en el {@code ClienteRepository}, acotado al tenant vigente por el filtro
 * global de Hibernate y por la RLS (Req 23).</p>
 */
public interface DatosClientePort {

    /**
     * Datos minimos visibles de un Cliente para su uso en la Cotizacion.
     *
     * @param id     identificador del Cliente.
     * @param nombre nombre/razon social del Cliente.
     * @param rfc    RFC del Cliente.
     * @param email  correo del Cliente; {@code null} si no tiene.
     */
    record DatosCliente(UUID id, String nombre, String rfc, String email) {
    }

    /**
     * Busca los datos visibles de un Cliente activo del tenant vigente (Req 23).
     *
     * @param clienteId identificador del Cliente; puede ser {@code null}.
     * @return los datos del Cliente, o {@link Optional#empty()} si no es accesible.
     */
    Optional<DatosCliente> buscarPorId(UUID clienteId);
}

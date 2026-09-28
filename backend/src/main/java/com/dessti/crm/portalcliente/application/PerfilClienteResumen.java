package com.dessti.crm.portalcliente.application;

import java.util.UUID;

/**
 * Proyeccion de solo lectura de los datos del propio Cliente para el Portal
 * (Req 45.1). Expone los datos comerciales/de contacto que el Cliente puede ver de
 * si mismo (identificacion, contacto y direccion), sin datos internos de la Empresa.
 *
 * @param clienteId        identificador del Cliente.
 * @param nombre           nombre/razon social.
 * @param nombreComercial  nombre comercial; {@code null} si no aplica.
 * @param rfc              RFC.
 * @param email            correo de contacto; {@code null} si no aplica.
 * @param telefono         telefono de contacto; {@code null} si no aplica.
 * @param direccionCiudad  ciudad de la direccion; {@code null} si no aplica.
 * @param direccionEstado  estado/provincia; {@code null} si no aplica.
 * @param direccionCp      codigo postal; {@code null} si no aplica.
 */
public record PerfilClienteResumen(
        UUID clienteId,
        String nombre,
        String nombreComercial,
        String rfc,
        String email,
        String telefono,
        String direccionCiudad,
        String direccionEstado,
        String direccionCp) {
}

package com.dessti.crm.platform.security.roles;

import com.dessti.crm.platform.error.NoAutorizadoException;

/**
 * Se lanza cuando se intenta modificar o eliminar un rol predefinido del
 * Sistema (Req 28.6). Los roles predefinidos son inmutables: solo pueden
 * gestionarse los {@code Rol_Personalizado} de una Empresa.
 *
 * <p>Deriva de {@link NoAutorizadoException} para mapearse a <strong>HTTP
 * 403</strong> mediante el manejador global de errores: la operacion no esta
 * permitida sobre un recurso de Sistema, con independencia de los permisos del
 * actor.</p>
 */
public class RolPredefinidoInmutableException extends NoAutorizadoException {

    public RolPredefinidoInmutableException(String mensaje) {
        super(mensaje);
    }
}

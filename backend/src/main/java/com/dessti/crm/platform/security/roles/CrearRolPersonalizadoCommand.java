package com.dessti.crm.platform.security.roles;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Comando de aplicacion para crear un {@code Rol_Personalizado} de una Empresa
 * (Req 28.2), desacoplado de las entidades JPA.
 *
 * <p>El {@code tenant_id} NO forma parte del comando: se deriva del contexto
 * autenticado en {@code ServicioRoles} (Req 23.4), nunca de la peticion. El
 * comando solo transporta el nombre elegido y los identificadores de los
 * permisos existentes que el rol combina.</p>
 *
 * @param nombre     nombre del rol dentro de la Empresa; obligatorio.
 * @param permisoIds identificadores de los {@code permiso} existentes a
 *                   combinar; no vacio.
 */
public record CrearRolPersonalizadoCommand(String nombre, Set<UUID> permisoIds) {

    public CrearRolPersonalizadoCommand {
        // Copia defensiva preservando el orden de insercion para mensajes estables.
        permisoIds = (permisoIds == null)
                ? Set.of()
                : new LinkedHashSet<>(permisoIds);
    }
}

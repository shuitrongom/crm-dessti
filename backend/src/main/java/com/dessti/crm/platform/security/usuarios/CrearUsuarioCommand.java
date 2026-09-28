package com.dessti.crm.platform.security.usuarios;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Comando de aplicacion para crear una cuenta de Usuario (Req 4.1),
 * desacoplado de las entidades JPA.
 *
 * <p>El {@code tenant_id} NO forma parte del comando: se deriva del contexto
 * autenticado en {@link ServicioUsuarios} (Req 23.4), nunca de la peticion. La
 * contrasena viaja en claro <em>solo</em> en este comando de entrada y el
 * servicio la cifra de inmediato con {@code PasswordEncoder}; jamas se persiste
 * ni se registra en claro (Req 1.2, 11.3).</p>
 *
 * @param identificadorAcceso identificador de acceso (login); obligatorio.
 * @param password            contrasena en claro; obligatorio. Se cifra en el
 *                            servicio y no se conserva.
 * @param nombreVisible       nombre para mostrar (Req 4); opcional. Se normaliza
 *                            y acota (max 200) en el dominio.
 * @param rolIds              identificadores de los Roles iniciales; al menos
 *                            uno (Req 4.1).
 * @param clienteId           Cliente asociado para el Portal del Cliente (Req 45);
 *                            obligatorio si se asigna el rol {@code cliente_portal},
 *                            y debe ser {@code null} en cualquier otro caso. El
 *                            servicio valida la coherencia y que el Cliente
 *                            pertenezca al tenant.
 */
public record CrearUsuarioCommand(String identificadorAcceso, String password,
                                  String nombreVisible, Set<UUID> rolIds, UUID clienteId) {

    public CrearUsuarioCommand {
        // Copia defensiva preservando el orden de insercion para mensajes estables.
        rolIds = (rolIds == null) ? Set.of() : new LinkedHashSet<>(rolIds);
    }

    /** Compatibilidad: alta de usuario de staff (sin Cliente de portal). */
    public CrearUsuarioCommand(String identificadorAcceso, String password,
                               String nombreVisible, Set<UUID> rolIds) {
        this(identificadorAcceso, password, nombreVisible, rolIds, null);
    }
}

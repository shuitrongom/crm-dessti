package com.dessti.crm.platform.security.roles;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio del catalogo de {@link PermisoEntity} (Req 3.1, 28.1).
 *
 * <p>El catalogo de permisos es de plataforma (comun a todas las Empresas), por
 * lo que las consultas no se filtran por tenant. Lo usa {@code ServicioRoles}
 * para resolver los permisos seleccionados al crear un {@code Rol_Personalizado}
 * y para detectar permisos inexistentes (Req 28.5).</p>
 */
public interface PermisoRepository extends JpaRepository<PermisoEntity, UUID> {

    /**
     * Recupera los permisos cuyo id se encuentra en la coleccion dada.
     *
     * <p>Si algun id no existe, simplemente no aparece en el resultado; el
     * llamador compara tamanos para detectar permisos inexistentes (Req 28.5).</p>
     *
     * @param ids identificadores de permiso solicitados.
     * @return los permisos existentes correspondientes a esos ids.
     */
    List<PermisoEntity> findByIdIn(Collection<UUID> ids);
}

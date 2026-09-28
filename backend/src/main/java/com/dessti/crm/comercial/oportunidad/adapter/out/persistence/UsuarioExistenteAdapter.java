package com.dessti.crm.comercial.oportunidad.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.oportunidad.application.UsuarioExistentePort;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Adaptador de salida que implementa {@link UsuarioExistentePort} delegando en el
 * {@link UsuarioRepository} de plataforma (V81).
 *
 * <p>La entidad {@code Usuario} NO es tenant-scoped por RLS (su {@code tenant_id}
 * es nullable para el super_admin de plataforma), de modo que el aislamiento por
 * Empresa se aplica <strong>explicitamente</strong> pasando el tenant vigente
 * ({@link TenantContext#require()}) a {@code findByIdAndTenantIdAndActivoTrue}: un
 * Usuario de otra Empresa o desactivado no se considera existente. Este adaptador
 * aisla la dependencia hacia la gestion de Usuarios en la capa de infraestructura,
 * dejando la aplicacion de Oportunidades libre de acoplamiento, del mismo modo que
 * {@code ClienteExistenteAdapter}.</p>
 */
@Component
public class UsuarioExistenteAdapter implements UsuarioExistentePort {

    private final UsuarioRepository usuarioRepository;

    public UsuarioExistenteAdapter(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean existeUsuarioActivo(UUID usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        return usuarioRepository
                .findByIdAndTenantIdAndActivoTrue(usuarioId, TenantContext.require())
                .isPresent();
    }
}

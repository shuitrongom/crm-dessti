package com.dessti.crm.comercial.cliente.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.application.UsuarioExistentePort;
import com.dessti.crm.platform.security.usuarios.UsuarioRepository;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Adaptador de salida que implementa {@link UsuarioExistentePort} del submodulo
 * de Clientes delegando en el {@link UsuarioRepository} de plataforma (V81).
 *
 * <p>El aislamiento por Empresa se aplica explicitamente pasando el tenant vigente
 * ({@link TenantContext#require()}) a {@code findByIdAndTenantIdAndActivoTrue}: un
 * Usuario de otra Empresa o desactivado no se considera existente. Se registra con
 * un nombre de bean propio para no colisionar con el adaptador homonimo del
 * submodulo de Oportunidades.</p>
 */
@Component("usuarioExistenteClienteAdapter")
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

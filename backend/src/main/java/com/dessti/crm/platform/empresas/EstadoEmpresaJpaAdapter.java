package com.dessti.crm.platform.empresas;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JPA de {@link EstadoEmpresaPort} que resuelve el estado de
 * suspension de una Empresa consultando su {@code estado} (Req 24.4).
 *
 * <p>Es de solo lectura y tolerante a la ausencia: si la Empresa no existe,
 * devuelve {@code false} (no se bloquea el login por ausencia; las demas reglas
 * de autenticacion ya rechazan credenciales invalidas de forma generica,
 * Req 1.3). Se apoya en {@link EmpresaRepository}, que no esta filtrado por
 * tenant (la Empresa ES el tenant), lo que permite la consulta durante el login
 * antes de establecer contexto de negocio.</p>
 */
@Component
public class EstadoEmpresaJpaAdapter implements EstadoEmpresaPort {

    private final EmpresaRepository empresaRepository;

    public EstadoEmpresaJpaAdapter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaSuspendida(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return empresaRepository.findById(tenantId)
                .map(empresa -> empresa.getEstado() == EstadoEmpresa.SUSPENDIDA)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accesoBloqueado(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return empresaRepository.findById(tenantId)
                .map(empresa -> {
                    EstadoEmpresa estado = empresa.getEstado();
                    return estado == EstadoEmpresa.SUSPENDIDA
                            || estado == EstadoEmpresa.CANCELADA;
                })
                .orElse(false);
    }
}

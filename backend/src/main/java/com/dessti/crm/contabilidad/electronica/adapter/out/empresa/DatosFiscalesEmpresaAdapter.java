package com.dessti.crm.contabilidad.electronica.adapter.out.empresa;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.contabilidad.electronica.application.DatosFiscalesEmpresaPort;
import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.error.RecursoNoEncontradoException;

/**
 * Adaptador de salida que implementa {@link DatosFiscalesEmpresaPort} resolviendo el
 * RFC de la Empresa desde el {@link EmpresaRepository} de plataforma.
 *
 * <p>La Empresa NO es tenant-scoped (es la propia unidad tenant), por lo que la
 * consulta por id no pasa por el filtro de tenant; el {@code tenantId} recibido
 * proviene del {@code TenantContext} de la peticion, garantizando que cada Empresa
 * solo obtiene su propio RFC.</p>
 */
@Component
public class DatosFiscalesEmpresaAdapter implements DatosFiscalesEmpresaPort {

    private final EmpresaRepository empresaRepository;

    public DatosFiscalesEmpresaAdapter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    public String rfcDe(UUID tenantId) {
        if (tenantId == null) {
            throw new RecursoNoEncontradoException("No se encontro la Empresa del tenant actual.");
        }
        Empresa empresa = empresaRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la Empresa del tenant actual."));
        return empresa.getRfc();
    }
}

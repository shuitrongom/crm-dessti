package com.dessti.crm.vertical.anuncios.permiso.adapter.out;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;
import com.dessti.crm.platform.empresas.EstadoEmpresa;
import com.dessti.crm.vertical.anuncios.permiso.application.EmpresasActivasPort;

/**
 * Adaptador real del {@link EmpresasActivasPort} (Decisión D8): enumera los
 * {@code tenantId} de las Empresas <strong>activas</strong> delegando en el
 * {@link EmpresaRepository} de plataforma (misma fuente que
 * {@code ServicioEmpresas.listarEmpresas}, que usa {@code findByEstado}).
 *
 * <p>La tabla {@code empresa} NO es tenant-scoped (es la propia unidad tenant),
 * por lo que esta lectura opera a nivel de plataforma sin fijar
 * {@code app.current_tenant}. Devuelve la PK de cada Empresa
 * ({@link Empresa#getId()}), que es exactamente el {@code tenant_id} usado por
 * la Row-Level Security.</p>
 *
 * <p><strong>Paginación:</strong> {@code findByEstado} es paginado; para obtener
 * el conjunto completo de tenants activos se itera sobre las páginas con un
 * tamaño amplio ({@value #TAMANO_PAGINA}), acumulando los identificadores. Un
 * orden estable por {@code id} garantiza un barrido determinista y sin
 * duplicados/omisiones entre páginas.</p>
 */
@Component
public class EmpresasActivasAdapter implements EmpresasActivasPort {

    /** Tamaño de página amplio para recorrer las Empresas activas en pocos lotes. */
    private static final int TAMANO_PAGINA = 500;

    private final EmpresaRepository empresaRepository;

    public EmpresasActivasAdapter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Recorre todas las páginas de Empresas en estado {@link EstadoEmpresa#ACTIVA}
     * y devuelve sus identificadores de tenant.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<UUID> tenantsActivos() {
        List<UUID> tenants = new ArrayList<>();
        int pagina = 0;
        org.springframework.data.domain.Page<Empresa> lote;
        do {
            lote = empresaRepository.findByEstado(
                    EstadoEmpresa.ACTIVA,
                    PageRequest.of(pagina, TAMANO_PAGINA, Sort.by("id").ascending()));
            for (Empresa empresa : lote.getContent()) {
                tenants.add(empresa.getId());
            }
            pagina++;
        } while (lote.hasNext());
        return tenants;
    }
}

package com.dessti.crm.platform.empresas;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.giros.application.ConteoEmpresasPorGiroPort;

/**
 * Adaptador REAL de {@link ConteoEmpresasPorGiroPort} respaldado por la
 * persistencia de Empresas (Req 1.5). Cierra el TODO que dejaron el puerto y su
 * placeholder ({@code platform.giros.application.ConteoEmpresasPorGiroPorDefecto}):
 * ahora que la migracion V51 creo {@code empresa.giro_id} y la entidad
 * {@link Empresa} la expone, el conteo real por Giro es posible.
 *
 * <p>Vive en {@code platform.empresas} —y no en {@code platform.giros}— para
 * respetar la frontera hexagonal del Nucleo: es {@code platform.empresas} quien
 * posee la persistencia de Empresas, de modo que {@code platform.giros} depende
 * solo de la abstraccion {@link ConteoEmpresasPorGiroPort}. Replica el patron de
 * {@code PlanModulosPlanAdapter}.</p>
 *
 * <h2>Cesion del placeholder (via {@code @ConditionalOnMissingBean})</h2>
 * <p>Al existir este {@code @Component}, el placeholder permisivo
 * {@code ConteoEmpresasPorGiroPorDefecto} —anotado con
 * {@code @ConditionalOnMissingBean(ConteoEmpresasPorGiroPort.class)}— NO registra
 * su bean y cede su lugar a este adaptador, quedando como fallback para
 * escenarios sin este componente (p. ej. slices de test que no carguen
 * {@code platform.empresas}), igual que {@code PlanModulosPermisivoPorDefecto}
 * convive con {@code PlanModulosPlanAdapter}. No hay colision de nombres de bean:
 * este es un {@code @Component} llamado {@code conteoEmpresasPorGiroAdapter},
 * mientras que el placeholder registra un {@code @Bean} llamado
 * {@code conteoEmpresasPorGiroPlaceholder}.</p>
 */
@Component
public class ConteoEmpresasPorGiroAdapter implements ConteoEmpresasPorGiroPort {

    private final EmpresaRepository empresaRepository;

    public ConteoEmpresasPorGiroAdapter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long contarEmpresasPorGiro(UUID giroId) {
        if (giroId == null) {
            return 0L;
        }
        return empresaRepository.countByGiroId(giroId);
    }
}

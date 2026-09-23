package com.dessti.crm.platform.empresas;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.platform.giros.adapter.out.persistence.GiroRepository;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;

/**
 * Adaptador REAL de {@link GiroEmpresaPort} respaldado por la Empresa (tenant) y
 * el catalogo de Giros (Req 2.4, 8.1, 8.2).
 *
 * <p>Replica el patron de {@code PlanModulosPlanAdapter}: el puerto vive en
 * {@code platform.security.rbac} (junto al {@code Autorizador} que lo consume) y
 * su adaptador vive en {@code platform.empresas}, donde estan las dependencias
 * de persistencia. Asi la capa de seguridad depende solo de la abstraccion y no
 * se acopla a la persistencia de Empresas ni de Giros.</p>
 *
 * <h2>Resolucion clave del Giro: giro_id (UUID) → clave (String) (Req 2.4)</h2>
 * <p>El {@code tenantId} recibido es el {@code Empresa.id}. La resolucion es:</p>
 * <ol>
 *   <li>cargar la Empresa por su id ({@link EmpresaRepository#findById(Object)});</li>
 *   <li>obtener su {@link Empresa#getGiroId() giro_id} (UUID; siempre presente,
 *       la columna es {@code NOT NULL} desde V51);</li>
 *   <li>resolver la <strong>clave</strong> del Giro consultando el catalogo
 *       ({@link GiroRepository#findById(Object)}) y devolviendo
 *       {@link Giro#getClave()}.</li>
 * </ol>
 *
 * <p>Se traduce a la clave (y no se devuelve el UUID) porque el gating por Giro
 * (tarea 6.1) compara contra la clave que declara {@code ContratoVertical.giro()};
 * ver la decision documentada en {@link GiroEmpresaPort}.</p>
 *
 * <h2>Decision: deny-safe (Req 8.1, 8.2)</h2>
 * <p>Ante cualquier situacion anomala —tenant nulo, Empresa inexistente o Giro
 * inexistente (integridad rota pese al FK de V51)— se devuelve
 * {@link Optional#empty()}. En el gating por Giro la ausencia de clave se traduce
 * en denegacion, de modo que un tenant que no resuelve Giro nunca obtiene acceso
 * a un vertical.</p>
 *
 * <h2>Sin placeholder</h2>
 * <p>A diferencia de {@code PlanModulosPort} (que convivia con
 * {@code PlanModulosPermisivoPorDefecto} por llegar su adaptador real mas tarde),
 * {@link GiroEmpresaPort} NO tiene placeholder: este adaptador es la unica
 * implementacion y sus dependencias ya existen, por lo que siempre esta
 * disponible en el contexto. Ver {@link GiroEmpresaPort} para el detalle.</p>
 */
@Component
public class GiroEmpresaAdapter implements GiroEmpresaPort {

    private final EmpresaRepository empresaRepository;
    private final GiroRepository giroRepository;

    public GiroEmpresaAdapter(EmpresaRepository empresaRepository,
                              GiroRepository giroRepository) {
        this.empresaRepository = empresaRepository;
        this.giroRepository = giroRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> giroDeTenant(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return empresaRepository.findById(tenantId)
                .map(Empresa::getGiroId)
                .flatMap(giroRepository::findById)
                .map(Giro::getClave);
    }
}

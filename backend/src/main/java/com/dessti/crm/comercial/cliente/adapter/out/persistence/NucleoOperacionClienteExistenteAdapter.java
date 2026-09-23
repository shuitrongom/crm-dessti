package com.dessti.crm.comercial.cliente.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.cliente.application.ClienteExistentePort;

/**
 * Adaptador de salida que implementa el puerto de Núcleo
 * {@link ClienteExistentePort} delegando en el {@link ClienteRepository} del
 * submódulo de Clientes. Vive en el módulo <strong>comercial</strong> (donde
 * reside la entidad Cliente), de modo que la entidad no se expone fuera de
 * comercial: los consumidores del Núcleo ({@code ServicioOrdenesFabricacion} de
 * producción y {@code ServicioProyectos} de proyecto) solo dependen de la interfaz
 * estable {@code ClienteExistentePort} (Req 4.1, 1.5, 16.4).
 *
 * <p><strong>Aislamiento multi-tenant (RLS + {@code TenantContext}):</strong> la
 * consulta {@code findByIdAndActivoTrue} ya está acotada al tenant vigente por el
 * filtro global de Hibernate (Capa 1) y por la Row-Level Security de PostgreSQL
 * (Capa 2), de modo que un Cliente de otro tenant no se considera existente. El
 * método devuelve un {@code boolean} de solo lectura, sin exponer la entidad
 * Cliente.</p>
 *
 * <p>Se registra con un nombre de bean cualificado propio para convivir sin
 * colisión con los otros adaptadores de existencia de Cliente del módulo comercial
 * (submódulos de Cotizaciones y Oportunidades), que implementan puertos homónimos
 * distintos.</p>
 */
@Component("nucleoOperacionClienteExistenteAdapter")
public class NucleoOperacionClienteExistenteAdapter implements ClienteExistentePort {

    private final ClienteRepository clienteRepository;

    public NucleoOperacionClienteExistenteAdapter(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public boolean existeEnTenant(UUID clienteId) {
        if (clienteId == null) {
            return false;
        }
        return clienteRepository.findByIdAndActivoTrue(clienteId).isPresent();
    }
}

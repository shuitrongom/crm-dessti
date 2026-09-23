package com.dessti.crm.compras.ordencompra.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.compras.ordencompra.application.ProveedorExistentePort;
import com.dessti.crm.compras.proveedor.adapter.out.persistence.ProveedorRepository;

/**
 * Adaptador de salida que implementa {@link ProveedorExistentePort} delegando en
 * el {@link ProveedorRepository} del submodulo de Proveedores (ambos dentro del
 * modulo compras, Req 31.1, 29). Replica {@code ClienteExistenteAdapter} del
 * submodulo de Cotizaciones.
 *
 * <p>La consulta {@code existsByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate y por la RLS (Req 23), de modo que un Proveedor
 * de otro tenant no se considera existente. Este adaptador aisla la dependencia
 * hacia el submodulo de Proveedores en la capa de infraestructura.</p>
 */
@Component("ordenCompraProveedorExistenteAdapter")
public class ProveedorExistenteAdapter implements ProveedorExistentePort {

    private final ProveedorRepository proveedorRepository;

    public ProveedorExistenteAdapter(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @Override
    public boolean existeProveedorActivo(UUID proveedorId) {
        if (proveedorId == null) {
            return false;
        }
        return proveedorRepository.existsByIdAndActivoTrue(proveedorId);
    }
}

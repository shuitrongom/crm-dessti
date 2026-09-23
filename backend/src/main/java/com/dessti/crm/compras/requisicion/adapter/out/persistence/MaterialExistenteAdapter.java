package com.dessti.crm.compras.requisicion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.compras.requisicion.application.MaterialExistentePort;
import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;

/**
 * Adaptador de salida que implementa {@link MaterialExistentePort} delegando en el
 * {@link MaterialRepository} del modulo de inventario de Materiales (Req 30.1, 18).
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate y por la RLS (Req 23), de modo que un Material
 * de otro tenant no se considera existente. Este adaptador aisla la dependencia
 * hacia el modulo de inventario en la capa de infraestructura.</p>
 */
@Component("requisicionMaterialExistenteAdapter")
public class MaterialExistenteAdapter implements MaterialExistentePort {

    private final MaterialRepository materialRepository;

    public MaterialExistenteAdapter(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    public boolean existeMaterialActivo(UUID materialId) {
        if (materialId == null) {
            return false;
        }
        return materialRepository.findByIdAndActivoTrue(materialId).isPresent();
    }
}

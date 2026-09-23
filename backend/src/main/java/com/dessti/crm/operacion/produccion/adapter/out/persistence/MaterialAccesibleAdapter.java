package com.dessti.crm.operacion.produccion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.inventario.adapter.out.persistence.MaterialRepository;
import com.dessti.crm.operacion.produccion.application.MaterialAccesiblePort;

/**
 * Adaptador de salida que implementa {@link MaterialAccesiblePort} delegando en el
 * {@link MaterialRepository} del modulo de inventario de Materiales (Req 5.3, 18),
 * siguiendo el mismo patron que {@code MaterialExistenteAdapter} de Requisiciones.
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente por
 * el filtro global de Hibernate y por la RLS (Req 23), de modo que un Material de
 * otro tenant no se considera accesible. Este adaptador aisla la dependencia hacia
 * el modulo de inventario en la capa de infraestructura.</p>
 */
@Component("produccionMaterialAccesibleAdapter")
public class MaterialAccesibleAdapter implements MaterialAccesiblePort {

    private final MaterialRepository materialRepository;

    public MaterialAccesibleAdapter(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    public boolean esAccesible(UUID materialId) {
        if (materialId == null) {
            return false;
        }
        return materialRepository.findByIdAndActivoTrue(materialId).isPresent();
    }
}

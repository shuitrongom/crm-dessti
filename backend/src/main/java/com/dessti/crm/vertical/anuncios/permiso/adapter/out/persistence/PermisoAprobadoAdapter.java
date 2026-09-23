package com.dessti.crm.vertical.anuncios.permiso.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;
import com.dessti.crm.vertical.anuncios.permiso.domain.EstadoPermisoInstalacion;

/**
 * Adaptador de salida que implementa {@link PermisoAprobadoPort} delegando en el
 * {@link PermisoInstalacionRepository}. Expone la guarda de programacion de
 * instalacion del Req 17.4: el Sitio (o el permiso concreto) tiene un
 * Permiso_Instalacion {@code aprobado}. Lo consumira el bloque 22 (tarea 22.1).
 *
 * <p>Las consultas {@code existsBySitioIdAndEstado} / {@code existsByIdAndEstado}
 * ya estan acotadas al tenant vigente por el filtro global de Hibernate y por la
 * RLS (Req 23), de modo que un permiso de otro tenant no se considera accesible.
 * Sigue el patron de {@code LevantamientoCompletadoAdapter}.</p>
 */
@Component("permisoAprobadoAdapter")
public class PermisoAprobadoAdapter implements PermisoAprobadoPort {

    private final PermisoInstalacionRepository permisoRepository;

    public PermisoAprobadoAdapter(PermisoInstalacionRepository permisoRepository) {
        this.permisoRepository = permisoRepository;
    }

    @Override
    public boolean sitioTienePermisoAprobado(UUID sitioId) {
        if (sitioId == null) {
            return false;
        }
        return permisoRepository.existsBySitioIdAndEstado(
                sitioId, EstadoPermisoInstalacion.APROBADO);
    }

    @Override
    public boolean estaAprobado(UUID permisoId) {
        if (permisoId == null) {
            return false;
        }
        return permisoRepository.existsByIdAndEstado(
                permisoId, EstadoPermisoInstalacion.APROBADO);
    }
}

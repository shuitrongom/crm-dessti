package com.dessti.crm.vertical.anuncios.instalacion.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.vertical.anuncios.instalacion.application.InstalacionCompletadaPort;
import com.dessti.crm.vertical.anuncios.instalacion.domain.EstadoOrdenTrabajoInstalacion;

/**
 * Adaptador de salida que implementa {@link InstalacionCompletadaPort} delegando
 * en el {@link OrdenTrabajoInstalacionRepository}. Expone, por Sitio, dos hechos
 * derivados de las Ordenes de Trabajo de Instalacion necesarios para el avance
 * consolidado del Proyecto (Req 21.3, 21.4). Lo consume el submodulo proyecto.
 *
 * <p>Las consultas {@code existsBySitioIdAndEstado} / {@code existsBySitioId} ya
 * estan acotadas al tenant vigente por el filtro global de Hibernate y por la RLS
 * (Req 23), de modo que una OTI de otro tenant no se considera accesible. Sigue el
 * patron de {@code PermisoAprobadoAdapter} y {@code OrdenFabricacionTerminadaAdapter}.</p>
 */
@Component("instalacionCompletadaAdapter")
public class InstalacionCompletadaAdapter implements InstalacionCompletadaPort {

    private final OrdenTrabajoInstalacionRepository ordenTrabajoRepository;

    public InstalacionCompletadaAdapter(OrdenTrabajoInstalacionRepository ordenTrabajoRepository) {
        this.ordenTrabajoRepository = ordenTrabajoRepository;
    }

    @Override
    public boolean sitioTieneInstalacionCompletada(UUID sitioId) {
        if (sitioId == null) {
            return false;
        }
        return ordenTrabajoRepository.existsBySitioIdAndEstado(
                sitioId, EstadoOrdenTrabajoInstalacion.COMPLETADA);
    }

    @Override
    public boolean sitioTieneOrdenFabricacionRespaldada(UUID sitioId) {
        if (sitioId == null) {
            return false;
        }
        // La existencia de una OTI para el Sitio implica una Orden_Fabricacion
        // terminada que la respalda (Req 19.1/19.2): ver InstalacionCompletadaPort.
        return ordenTrabajoRepository.existsBySitioId(sitioId);
    }
}
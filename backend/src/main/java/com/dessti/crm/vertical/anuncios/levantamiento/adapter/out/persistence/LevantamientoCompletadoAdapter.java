package com.dessti.crm.vertical.anuncios.levantamiento.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.vertical.anuncios.levantamiento.domain.EstadoLevantamiento;

/**
 * Adaptador de salida que implementa {@link LevantamientoCompletadoPort} delegando
 * en el {@link LevantamientoSitioRepository}. Expone la guarda de programacion de
 * instalacion del Req 16.5: el Sitio (o el Levantamiento concreto) esta
 * {@code completado}. Lo consumira el bloque 22 (tarea 22.1).
 *
 * <p>Las consultas {@code existsBySitioIdAndEstado} / {@code existsByIdAndEstado}
 * ya estan acotadas al tenant vigente por el filtro global de Hibernate y por la
 * RLS (Req 23), de modo que un Levantamiento de otro tenant no se considera
 * accesible.</p>
 */
@Component("levantamientoCompletadoAdapter")
public class LevantamientoCompletadoAdapter implements LevantamientoCompletadoPort {

    private final LevantamientoSitioRepository levantamientoSitioRepository;

    public LevantamientoCompletadoAdapter(LevantamientoSitioRepository levantamientoSitioRepository) {
        this.levantamientoSitioRepository = levantamientoSitioRepository;
    }

    @Override
    public boolean sitioTieneLevantamientoCompletado(UUID sitioId) {
        if (sitioId == null) {
            return false;
        }
        return levantamientoSitioRepository.existsBySitioIdAndEstado(
                sitioId, EstadoLevantamiento.COMPLETADO);
    }

    @Override
    public boolean estaCompletado(UUID levantamientoId) {
        if (levantamientoId == null) {
            return false;
        }
        return levantamientoSitioRepository.existsByIdAndEstado(
                levantamientoId, EstadoLevantamiento.COMPLETADO);
    }
}

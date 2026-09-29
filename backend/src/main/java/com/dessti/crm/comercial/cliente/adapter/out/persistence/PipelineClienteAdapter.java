package com.dessti.crm.comercial.cliente.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cliente.application.PipelineClientePort;
import com.dessti.crm.comercial.cotizacion.adapter.out.persistence.CotizacionRepository;
import com.dessti.crm.comercial.oportunidad.adapter.out.persistence.OportunidadRepository;

/**
 * Adaptador de salida que implementa {@link PipelineClientePort} (Req 5.10)
 * componiendo las consultas de solo lectura de los repositorios de Oportunidad y
 * Cotizacion para determinar si un Cliente tiene actividad comercial abierta.
 *
 * <p>Vive en el submodulo de Clientes (que consume el puerto) y depende de los
 * repositorios de los submodulos de Oportunidad y Cotizacion; los tres pertenecen
 * al mismo modulo {@code comercial-crm}, del mismo modo que
 * {@code OportunidadDeClienteAdapter} cruza submodulos dentro del bloque comercial.
 * El aislamiento por tenant lo garantizan el filtro global de Hibernate y la RLS de
 * PostgreSQL (Req 23), por lo que estas consultas nunca ven datos de otro tenant.</p>
 */
@Component
public class PipelineClienteAdapter implements PipelineClientePort {

    private final OportunidadRepository oportunidadRepository;
    private final CotizacionRepository cotizacionRepository;

    public PipelineClienteAdapter(OportunidadRepository oportunidadRepository,
                                  CotizacionRepository cotizacionRepository) {
        this.oportunidadRepository = oportunidadRepository;
        this.cotizacionRepository = cotizacionRepository;
    }

    @Override
    public boolean clienteTieneOportunidadesAbiertas(UUID clienteId) {
        if (clienteId == null) {
            return false;
        }
        return oportunidadRepository.existeOportunidadAbiertaDeCliente(clienteId);
    }

    @Override
    public boolean clienteTieneCotizacionesAbiertas(UUID clienteId) {
        if (clienteId == null) {
            return false;
        }
        return cotizacionRepository.existeCotizacionAbiertaDeCliente(clienteId);
    }
}

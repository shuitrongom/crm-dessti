package com.dessti.crm.comercial.oportunidad.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.canalventa.adapter.out.persistence.CanalVentaRepository;
import com.dessti.crm.comercial.oportunidad.application.CanalVentaExistentePort;

/**
 * Adaptador de salida que implementa {@link CanalVentaExistentePort} delegando en
 * el {@link CanalVentaRepository} del submodulo de Canales de venta (ambos dentro
 * del modulo comercial-crm, Req 63.1).
 *
 * <p>La consulta {@code findByIdAndActivoTrue} ya esta acotada al tenant vigente
 * por el filtro global de Hibernate y por la RLS (Req 23), de modo que un canal
 * de otro tenant no se considera existente. Este adaptador aisla la dependencia
 * hacia el submodulo de Canales en la capa de infraestructura, dejando la
 * aplicacion de Oportunidades libre de acoplamiento a su persistencia, del mismo
 * modo que {@code ClienteExistenteAdapter}.</p>
 */
@Component("canalVentaExistenteOportunidadAdapter")
public class CanalVentaExistenteAdapter implements CanalVentaExistentePort {

    private final CanalVentaRepository canalVentaRepository;

    public CanalVentaExistenteAdapter(CanalVentaRepository canalVentaRepository) {
        this.canalVentaRepository = canalVentaRepository;
    }

    @Override
    public boolean existeCanalVentaActivo(UUID canalVentaId) {
        if (canalVentaId == null) {
            return false;
        }
        return canalVentaRepository.findByIdAndActivoTrue(canalVentaId).isPresent();
    }
}

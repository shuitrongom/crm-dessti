package com.dessti.crm.vertical.anuncios.pruebadiseno.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.vertical.anuncios.pruebadiseno.application.PruebaDisenoDto;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.ResultadoRechazoPruebaDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.application.ServicioPruebasDiseno;
import com.dessti.crm.vertical.anuncios.pruebadiseno.domain.PruebaDiseno;
import com.dessti.crm.portalcliente.application.PruebaDisenoResumen;
import com.dessti.crm.portalcliente.application.ResultadoRechazoPruebaResumen;
import com.dessti.crm.portalcliente.application.ResumenPruebasDisenoPort;

/**
 * Adaptador que implementa el puerto {@link ResumenPruebasDisenoPort} <em>definido
 * por el Nucleo (el Portal del Cliente)</em>, delegando en la persistencia y el
 * caso de uso del flujo de Prueba_Diseno del vertical de anuncios. Invierte la
 * dependencia Nucleo&rarr;vertical (Req 10.5): el Portal ya no conoce las clases
 * concretas del vertical; es este adaptador —que reside con el vertical— quien las
 * conoce y las traduce a la forma que el Portal publica.
 *
 * <p>Las consultas quedan acotadas al tenant vigente por el filtro global de
 * Hibernate y por la RLS (Req 23). La aprobacion/rechazo reaplican la maquina de
 * estados del caso de uso existente (409 si la prueba ya esta decidida, Req 15.4).</p>
 */
@Component("pruebaDisenoResumenPortalAdapter")
public class ResumenPruebasDisenoPortalAdapter implements ResumenPruebasDisenoPort {

    private final PruebaDisenoRepository pruebaDisenoRepository;
    private final ServicioPruebasDiseno servicioPruebasDiseno;

    public ResumenPruebasDisenoPortalAdapter(PruebaDisenoRepository pruebaDisenoRepository,
                                             ServicioPruebasDiseno servicioPruebasDiseno) {
        this.pruebaDisenoRepository = pruebaDisenoRepository;
        this.servicioPruebasDiseno = servicioPruebasDiseno;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PruebaDisenoResumen> listarPorCliente(UUID clienteId, Pageable pageable) {
        return pruebaDisenoRepository.buscarPorClienteOrdenReciente(clienteId, pageable)
                .map(ResumenPruebasDisenoPortalAdapter::proyectar);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> cotizacionDePrueba(UUID pruebaId) {
        if (pruebaId == null) {
            return Optional.empty();
        }
        return pruebaDisenoRepository.findById(pruebaId)
                .map(PruebaDiseno::getCotizacionId);
    }

    @Override
    @Transactional
    public PruebaDisenoResumen aprobar(UUID pruebaId) {
        return proyectar(servicioPruebasDiseno.aprobar(pruebaId));
    }

    @Override
    @Transactional
    public ResultadoRechazoPruebaResumen rechazar(UUID pruebaId) {
        ResultadoRechazoPruebaDiseno resultado = servicioPruebasDiseno.rechazar(pruebaId);
        return new ResultadoRechazoPruebaResumen(
                proyectar(resultado.rechazada()),
                proyectar(resultado.nuevaVersion()));
    }

    private static PruebaDisenoResumen proyectar(PruebaDiseno prueba) {
        return new PruebaDisenoResumen(
                prueba.getId(),
                prueba.getCotizacionId(),
                prueba.getNumeroVersion(),
                prueba.getEstado().valorBd(),
                prueba.getAprobadaPor(),
                prueba.getRechazadaPor(),
                prueba.getDecididaEn(),
                prueba.getVersion(),
                prueba.getCreatedAt(),
                prueba.getUpdatedAt());
    }

    private static PruebaDisenoResumen proyectar(PruebaDisenoDto dto) {
        return new PruebaDisenoResumen(
                dto.id(),
                dto.cotizacionId(),
                dto.numeroVersion(),
                dto.estado(),
                dto.aprobadaPor(),
                dto.rechazadaPor(),
                dto.decididaEn(),
                dto.version(),
                dto.createdAt(),
                dto.updatedAt());
    }
}

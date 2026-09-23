package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.produccion.application.SitioOrdenFabricacionTerminadaPort;
import com.dessti.crm.operacion.proyecto.application.AvanceSitioPort;
import com.dessti.crm.operacion.proyecto.domain.AvanceFasesSitio;

/**
 * Adaptador de salida <strong>del Nucleo</strong> que implementa
 * {@link AvanceSitioPort} computando <strong>solo</strong> la fase de produccion del
 * avance de un Sitio (Decision D5-b, &sect;A3). Es el adaptador que
 * {@code ServicioProyectos} usa para los giros genericos
 * ({@link com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro#GENERICO}).
 *
 * <p>Delega unicamente en {@link SitioOrdenFabricacionTerminadaPort} (puerto de
 * produccion por Sitio) para la bandera {@code tieneOrdenFabricacionTerminada}; las
 * otras tres banderas (levantamiento, permiso e instalacion) quedan en
 * {@code false}, porque esas fases no aplican al giro generico y no se consultan los
 * puertos del vertical de anuncios. De este modo un Proyecto del Nucleo
 * <strong>no depende</strong> de los tres puertos de anuncios (mitigacion del riesgo
 * "Proyecto generico dependiendo de los 3 puertos de anuncios", &sect;A3).</p>
 */
@Component("avanceProduccionAdapter")
public class AvanceProduccionAdapter implements AvanceSitioPort {

    private final SitioOrdenFabricacionTerminadaPort sitioOrdenFabricacionTerminada;

    public AvanceProduccionAdapter(
            SitioOrdenFabricacionTerminadaPort sitioOrdenFabricacionTerminada) {
        this.sitioOrdenFabricacionTerminada = sitioOrdenFabricacionTerminada;
    }

    @Override
    public AvanceFasesSitio avanceDe(UUID sitioId) {
        if (sitioId == null) {
            return new AvanceFasesSitio(false, false, false, false);
        }
        // Solo produccion: las fases de anuncios (levantamiento/permiso/instalacion)
        // no aplican al giro generico y quedan en false.
        return new AvanceFasesSitio(
                false,
                false,
                sitioOrdenFabricacionTerminada.sitioTieneOrdenFabricacionTerminada(sitioId),
                false);
    }
}

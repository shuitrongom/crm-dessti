package com.dessti.crm.vertical.anuncios.proyecto.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.proyecto.application.AvanceSitioPort;
import com.dessti.crm.operacion.proyecto.domain.AvanceFasesSitio;
import com.dessti.crm.vertical.anuncios.instalacion.application.InstalacionCompletadaPort;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;

/**
 * Adaptador de salida <strong>del vertical de anuncios</strong> que implementa
 * {@link AvanceSitioPort} componiendo las <strong>cuatro fases</strong> de
 * instalacion del flujo de anuncios (Decision D5-b, &sect;A3), acotadas al tenant
 * vigente (Req 23):
 * <ul>
 *   <li>{@link LevantamientoCompletadoPort} — fase Levantamiento_Sitio (Req 16.4).</li>
 *   <li>{@link PermisoAprobadoPort} — fase Permiso_Instalacion (Req 17.4).</li>
 *   <li>{@link InstalacionCompletadaPort} — fases Orden_Fabricacion respaldada
 *       (Req 19.1/19.2, derivada de la existencia de una OTI) y
 *       Orden_Trabajo_Instalacion completada (Req 19.5).</li>
 * </ul>
 *
 * <p>Es el adaptador que {@code ServicioProyectos} usa para el giro
 * {@link com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro#ANUNCIOS}. Vive en
 * el paquete del vertical de anuncios (no en el Nucleo) precisamente porque compone
 * puertos de anuncios: asi el Nucleo {@code proyecto} no depende de los verticales
 * (regla de dependencias hexagonal). El puerto {@link AvanceSitioPort} permanece en
 * el Nucleo ({@code operacion.proyecto.application}).</p>
 *
 * <p><strong>Historico:</strong> este adaptador es la refactorizacion del antiguo
 * {@code AvanceSitioAdapter} del Nucleo (tarea 1.2); se traslado al vertical de
 * anuncios en la tarea 1.14 para dividir el computo del avance por giro.</p>
 */
@Component("avanceSitioAnunciosAdapter")
public class AvanceSitioAnunciosAdapter implements AvanceSitioPort {

    private final LevantamientoCompletadoPort levantamientoCompletado;
    private final PermisoAprobadoPort permisoAprobado;
    private final InstalacionCompletadaPort instalacionCompletada;

    public AvanceSitioAnunciosAdapter(LevantamientoCompletadoPort levantamientoCompletado,
                                      PermisoAprobadoPort permisoAprobado,
                                      InstalacionCompletadaPort instalacionCompletada) {
        this.levantamientoCompletado = levantamientoCompletado;
        this.permisoAprobado = permisoAprobado;
        this.instalacionCompletada = instalacionCompletada;
    }

    @Override
    public AvanceFasesSitio avanceDe(UUID sitioId) {
        if (sitioId == null) {
            return new AvanceFasesSitio(false, false, false, false);
        }
        return new AvanceFasesSitio(
                levantamientoCompletado.sitioTieneLevantamientoCompletado(sitioId),
                permisoAprobado.sitioTienePermisoAprobado(sitioId),
                instalacionCompletada.sitioTieneOrdenFabricacionRespaldada(sitioId),
                instalacionCompletada.sitioTieneInstalacionCompletada(sitioId));
    }
}

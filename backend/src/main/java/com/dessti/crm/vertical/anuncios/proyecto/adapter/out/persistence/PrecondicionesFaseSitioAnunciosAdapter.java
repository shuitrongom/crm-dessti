package com.dessti.crm.vertical.anuncios.proyecto.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.proyecto.application.PrecondicionesFaseSitioPort;
import com.dessti.crm.vertical.anuncios.levantamiento.application.LevantamientoCompletadoPort;
import com.dessti.crm.vertical.anuncios.permiso.application.PermisoAprobadoPort;

/**
 * Adaptador de salida <strong>del vertical de anuncios</strong> que implementa
 * {@link PrecondicionesFaseSitioPort} para el giro
 * {@link com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro#ANUNCIOS},
 * componiendo las precondiciones reales de las fases de instalacion (Req 3-bis,
 * 16.5, 17.4, 19.3), acotadas al tenant vigente (Req 23):
 * <ul>
 *   <li>{@link LevantamientoCompletadoPort#sitioTieneLevantamientoCompletado(UUID)}
 *       — el Sitio tiene un Levantamiento_Sitio completado.</li>
 *   <li>{@link PermisoAprobadoPort#sitioTienePermisoVigente(UUID)} — el Sitio tiene
 *       un Permiso_Instalacion aprobado <strong>y vigente</strong> (no vencido); un
 *       permiso aprobado pero vencido no autoriza instalar.</li>
 * </ul>
 *
 * <p>Vive en el paquete del vertical de anuncios (no en el Nucleo) precisamente
 * porque compone puertos de anuncios: asi el Nucleo {@code proyecto} depende solo de
 * la interfaz {@link PrecondicionesFaseSitioPort} y no de los verticales (regla de
 * dependencias hexagonal), igual que {@code AvanceSitioAnunciosAdapter}. Se registra
 * como {@code precondicionesFaseSitioAnunciosAdapter} y {@code ServicioProyectos} lo
 * selecciona cuando el {@code PerfilFasesGiro} del tenant es {@code ANUNCIOS}.</p>
 */
@Component("precondicionesFaseSitioAnunciosAdapter")
public class PrecondicionesFaseSitioAnunciosAdapter implements PrecondicionesFaseSitioPort {

    private final LevantamientoCompletadoPort levantamientoCompletado;
    private final PermisoAprobadoPort permisoAprobado;

    public PrecondicionesFaseSitioAnunciosAdapter(
            LevantamientoCompletadoPort levantamientoCompletado,
            PermisoAprobadoPort permisoAprobado) {
        this.levantamientoCompletado = levantamientoCompletado;
        this.permisoAprobado = permisoAprobado;
    }

    @Override
    public boolean sitioTieneLevantamientoCompletado(UUID sitioId) {
        if (sitioId == null) {
            return false;
        }
        return levantamientoCompletado.sitioTieneLevantamientoCompletado(sitioId);
    }

    @Override
    public boolean sitioTienePermisoVigente(UUID sitioId) {
        if (sitioId == null) {
            return false;
        }
        return permisoAprobado.sitioTienePermisoVigente(sitioId);
    }
}

package com.dessti.crm.operacion.proyecto.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.proyecto.application.PrecondicionesFaseSitioPort;

/**
 * Adaptador de salida <strong>del Nucleo</strong> que implementa
 * {@link PrecondicionesFaseSitioPort} para los giros <strong>genericos</strong>
 * ({@link com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro#GENERICO}).
 *
 * <p>Los giros genericos no habilitan las fases de Levantamiento_Sitio ni
 * Permiso_Instalacion, de modo que el gating de esas precondiciones <strong>no
 * aplica</strong>: ambas consultas devuelven {@code true} para no bloquear el avance
 * lineal de fase del tablero (Req 3-bis.4). El unico control que rige en el giro
 * generico es la maquina de estados lineal de {@code FaseSitioGenerica}.</p>
 *
 * <p>Se separa del adaptador de anuncios (que vive en el vertical y compone los
 * puertos de anuncios) para que un Proyecto del Nucleo no dependa de los puertos del
 * vertical, siguiendo el mismo patron D5-b que {@code AvanceProduccionAdapter}.</p>
 */
@Component("precondicionesFaseSitioGenericoAdapter")
public class PrecondicionesFaseSitioGenericoAdapter implements PrecondicionesFaseSitioPort {

    @Override
    public boolean sitioTieneLevantamientoCompletado(UUID sitioId) {
        // El giro generico no tiene fase de levantamiento: no bloquea el avance.
        return true;
    }

    @Override
    public boolean sitioTienePermisoVigente(UUID sitioId) {
        // El giro generico no tiene fase de permiso: no bloquea el avance.
        return true;
    }
}

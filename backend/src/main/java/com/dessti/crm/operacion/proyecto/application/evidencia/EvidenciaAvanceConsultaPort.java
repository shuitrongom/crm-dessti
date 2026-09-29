package com.dessti.crm.operacion.proyecto.application.evidencia;

import java.util.UUID;

import com.dessti.crm.operacion.proyecto.domain.FaseSitioGenerica;

/**
 * Puerto de consulta de evidencias para la GUARDA DE NEGOCIO del avance de fase
 * (Req 3.2). Lo usa {@code ServicioProyectos} para preguntar, sin acoplarse a la
 * implementacion, si un avance de Sitio tiene al menos una evidencia
 * <strong>aprobada</strong> de una fase dada; con eso decide si permite la
 * transicion a {@code entregado}. Lo implementa {@code ServicioEvidenciasAvance}.
 */
public interface EvidenciaAvanceConsultaPort {

    /**
     * Indica si el avance de Sitio tiene al menos una evidencia APROBADA que
     * respalde la fase indicada.
     *
     * @param avanceSitioId avance de Sitio.
     * @param fase          fase cuya evidencia aprobada se exige.
     * @return {@code true} si existe al menos una evidencia aprobada de esa fase.
     */
    boolean tieneEvidenciaAprobada(UUID avanceSitioId, FaseSitioGenerica fase);
}

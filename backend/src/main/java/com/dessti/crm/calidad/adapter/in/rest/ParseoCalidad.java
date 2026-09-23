package com.dessti.crm.calidad.adapter.in.rest;

import com.dessti.crm.calidad.domain.EstadoAccionCorrectiva;
import com.dessti.crm.calidad.domain.EstadoCambioSgc;
import com.dessti.crm.calidad.domain.EstadoNoConformidad;
import com.dessti.crm.calidad.domain.EstadoOportunidadCalidad;
import com.dessti.crm.calidad.domain.EstadoQuejaCliente;
import com.dessti.crm.calidad.domain.EstadoRiesgo;
import com.dessti.crm.calidad.domain.Impacto;
import com.dessti.crm.calidad.domain.NivelRiesgo;
import com.dessti.crm.calidad.domain.OrigenNoConformidad;
import com.dessti.crm.calidad.domain.OrigenQueja;
import com.dessti.crm.calidad.domain.Probabilidad;
import com.dessti.crm.calidad.domain.TipoContexto;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de parseo de etiquetas de negocio de los controladores del modulo
 * {@code calidad} hacia sus enums de dominio. Una etiqueta nula, en blanco o desconocida
 * se traduce a {@link ReglaNegocioException} (HTTP 422), coherente con el resto del
 * contrato REST del sistema.
 */
final class ParseoCalidad {

    private ParseoCalidad() {
        // Utilidad estatica: no instanciable.
    }

    // ---- Queja_Cliente ------------------------------------------------

    static OrigenQueja origenQuejaRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El origen de la Queja_Cliente es obligatorio.");
        }
        try {
            return OrigenQueja.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Origen de Queja_Cliente desconocido: " + etiqueta);
        }
    }

    static OrigenQueja origenQuejaOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : origenQuejaRequerido(etiqueta);
    }

    static EstadoQuejaCliente estadoQuejaOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return EstadoQuejaCliente.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Queja_Cliente desconocido: " + etiqueta);
        }
    }

    // ---- No_Conformidad ----------------------------------------------

    static OrigenNoConformidad origenNoConformidadRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El origen de la No_Conformidad es obligatorio.");
        }
        try {
            return OrigenNoConformidad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Origen de No_Conformidad desconocido: " + etiqueta);
        }
    }

    static OrigenNoConformidad origenNoConformidadOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : origenNoConformidadRequerido(etiqueta);
    }

    static EstadoNoConformidad estadoNoConformidadRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la No_Conformidad es obligatorio.");
        }
        try {
            return EstadoNoConformidad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de No_Conformidad desconocido: " + etiqueta);
        }
    }

    static EstadoNoConformidad estadoNoConformidadOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : estadoNoConformidadRequerido(etiqueta);
    }

    // ---- Accion_Correctiva -------------------------------------------

    static EstadoAccionCorrectiva estadoAccionCorrectivaRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Accion_Correctiva es obligatorio.");
        }
        try {
            return EstadoAccionCorrectiva.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Accion_Correctiva desconocido: " + etiqueta);
        }
    }

    static EstadoAccionCorrectiva estadoAccionCorrectivaOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : estadoAccionCorrectivaRequerido(etiqueta);
    }

    // ---- Riesgo -------------------------------------------------------

    static Probabilidad probabilidadRequerida(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("La probabilidad del Riesgo es obligatoria.");
        }
        try {
            return Probabilidad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Probabilidad de Riesgo desconocida: " + etiqueta);
        }
    }

    static Impacto impactoRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El impacto del Riesgo es obligatorio.");
        }
        try {
            return Impacto.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Impacto de Riesgo desconocido: " + etiqueta);
        }
    }

    static Probabilidad probabilidadOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : probabilidadRequerida(etiqueta);
    }

    static Impacto impactoOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : impactoRequerido(etiqueta);
    }

    static EstadoRiesgo estadoRiesgoRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado del Riesgo es obligatorio.");
        }
        try {
            return EstadoRiesgo.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Riesgo desconocido: " + etiqueta);
        }
    }

    static EstadoRiesgo estadoRiesgoOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : estadoRiesgoRequerido(etiqueta);
    }

    static NivelRiesgo nivelRiesgoOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return NivelRiesgo.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Nivel de Riesgo desconocido: " + etiqueta);
        }
    }

    // ---- Oportunidad_Calidad -----------------------------------------

    static EstadoOportunidadCalidad estadoOportunidadRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El estado de la Oportunidad_Calidad es obligatorio.");
        }
        try {
            return EstadoOportunidadCalidad.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Oportunidad_Calidad desconocido: " + etiqueta);
        }
    }

    static EstadoOportunidadCalidad estadoOportunidadOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : estadoOportunidadRequerido(etiqueta);
    }

    // ---- Cambio_SGC ---------------------------------------------------

    static EstadoCambioSgc estadoCambioSgcOpcional(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        try {
            return EstadoCambioSgc.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado de Cambio_SGC desconocido: " + etiqueta);
        }
    }

    // ---- Contexto_Organizacion ---------------------------------------

    static TipoContexto tipoContextoRequerido(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("El tipo del Contexto_Organizacion es obligatorio.");
        }
        try {
            return TipoContexto.desdeValorBd(etiqueta);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Tipo de Contexto_Organizacion desconocido: " + etiqueta);
        }
    }

    static TipoContexto tipoContextoOpcional(String etiqueta) {
        return (etiqueta == null || etiqueta.isBlank()) ? null : tipoContextoRequerido(etiqueta);
    }
}

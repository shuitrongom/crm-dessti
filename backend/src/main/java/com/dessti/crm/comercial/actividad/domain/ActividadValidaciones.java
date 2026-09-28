package com.dessti.crm.comercial.actividad.domain;

import java.time.Instant;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion de la {@link Actividad}. Centraliza
 * las reglas de formato del asunto y la descripcion y la coherencia de fechas
 * para garantizar un comportamiento consistente entre la fabrica y la edicion.
 *
 * <h2>Reglas</h2>
 * <ul>
 *   <li><strong>Asunto:</strong> obligatorio, entre 1 y 200 caracteres tras
 *       recortar (coincide con VARCHAR(200) de V79).</li>
 *   <li><strong>Descripcion:</strong> opcional, hasta 4000 caracteres tras
 *       recortar (coincide con VARCHAR(4000) de V79); un valor en blanco se
 *       normaliza a {@code null}.</li>
 *   <li><strong>Fecha programada:</strong> obligatoria.</li>
 *   <li><strong>Vencimiento:</strong> opcional; si se indica, no puede ser
 *       anterior a la fecha programada (coincide con {@code ck_actividad_vencimiento}).</li>
 * </ul>
 *
 * <p>Las violaciones se senalan con {@link ReglaNegocioException} (HTTP 422),
 * coherente con el resto del dominio comercial. Clase de utilidad no instanciable.</p>
 */
public final class ActividadValidaciones {

    /** Longitud maxima del asunto (coincide con VARCHAR(200) de V79). */
    public static final int LONGITUD_MAXIMA_ASUNTO = 200;

    /** Longitud maxima de la descripcion (coincide con VARCHAR(4000) de V79). */
    public static final int LONGITUD_MAXIMA_DESCRIPCION = 4000;

    private ActividadValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza el asunto obligatorio de la Actividad.
     *
     * @param valor asunto a normalizar.
     * @return el asunto recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede
     *         {@link #LONGITUD_MAXIMA_ASUNTO}.
     */
    public static String normalizarAsunto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El asunto de la Actividad es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_ASUNTO) {
            throw new ReglaNegocioException(
                    "El asunto no puede exceder " + LONGITUD_MAXIMA_ASUNTO + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza la descripcion opcional de la Actividad. Un valor nulo o en
     * blanco se convierte en {@code null}; en otro caso se recorta y se valida su
     * longitud maxima.
     *
     * @param valor descripcion a normalizar; puede ser nula.
     * @return la descripcion recortada, o {@code null} si venia vacia.
     * @throws ReglaNegocioException si excede {@link #LONGITUD_MAXIMA_DESCRIPCION}.
     */
    public static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new ReglaNegocioException(
                    "La descripcion no puede exceder " + LONGITUD_MAXIMA_DESCRIPCION + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Valida que la fecha programada sea obligatoria.
     *
     * @param fechaProgramada instante planificado de la interaccion; obligatorio.
     * @return la misma fecha si es valida.
     * @throws ReglaNegocioException si es nula.
     */
    public static Instant validarFechaProgramada(Instant fechaProgramada) {
        if (fechaProgramada == null) {
            throw new ReglaNegocioException("La fecha programada de la Actividad es obligatoria.");
        }
        return fechaProgramada;
    }

    /**
     * Valida la coherencia del vencimiento opcional respecto a la fecha
     * programada: si se indica, no puede ser anterior a esta (coincide con el
     * CHECK {@code ck_actividad_vencimiento} de V79).
     *
     * @param fechaProgramada fecha programada ya validada (no nula).
     * @param vencimiento     fecha limite; puede ser nula.
     * @return el vencimiento (posiblemente nulo) si es coherente.
     * @throws ReglaNegocioException si el vencimiento es anterior a la fecha
     *         programada (422).
     */
    public static Instant validarVencimiento(Instant fechaProgramada, Instant vencimiento) {
        if (vencimiento != null && vencimiento.isBefore(fechaProgramada)) {
            throw new ReglaNegocioException(
                    "El vencimiento no puede ser anterior a la fecha programada.");
        }
        return vencimiento;
    }
}

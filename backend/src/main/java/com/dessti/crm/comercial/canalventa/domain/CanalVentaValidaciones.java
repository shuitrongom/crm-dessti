package com.dessti.crm.comercial.canalventa.domain;

import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Utilidades de validacion y normalizacion del {@link CanalVenta} (Req 63.1).
 * Centraliza las reglas de formato del nombre y de la descripcion para evitar
 * dispersarlas y garantizar un comportamiento consistente, replicando el estilo
 * de {@code CatalogoValidaciones} del submodulo de Productos.
 *
 * <h2>Reglas (Req 63.1)</h2>
 * <ul>
 *   <li><strong>Nombre:</strong> obligatorio, entre 1 y 100 caracteres tras
 *       recortar (coincide con VARCHAR(100) de V15).</li>
 *   <li><strong>Descripcion:</strong> opcional, hasta 500 caracteres (coincide
 *       con VARCHAR(500) de V15). Un valor nulo/en blanco se interpreta como
 *       ausencia y devuelve {@code null}.</li>
 * </ul>
 *
 * <p>Clase de utilidad no instanciable. Las violaciones se senalan con
 * {@link ReglaNegocioException} (HTTP 422), coherente con el resto del dominio.</p>
 */
public final class CanalVentaValidaciones {

    /** Longitud maxima del nombre (coincide con VARCHAR(100) de V15). */
    public static final int LONGITUD_MAXIMA_NOMBRE = 100;

    /** Longitud maxima de la descripcion (coincide con VARCHAR(500) de V15). */
    public static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

    private CanalVentaValidaciones() {
        // Utilidad estatica: no instanciable.
    }

    /**
     * Valida y normaliza el nombre obligatorio del Canal_Venta (Req 63.1).
     *
     * @param valor nombre a normalizar.
     * @return el nombre recortado.
     * @throws ReglaNegocioException si es nulo/vacio o excede
     *         {@link #LONGITUD_MAXIMA_NOMBRE}.
     */
    public static String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El nombre del canal de venta es obligatorio.");
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ReglaNegocioException(
                    "El nombre del canal de venta no puede exceder "
                            + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return normalizado;
    }

    /**
     * Normaliza la descripcion opcional del Canal_Venta (Req 63.1). Un valor
     * nulo/en blanco se interpreta como ausencia y devuelve {@code null}.
     *
     * @param valor descripcion a normalizar; puede ser {@code null}.
     * @return la descripcion recortada, o {@code null} si no se proporciono.
     * @throws ReglaNegocioException si excede {@link #LONGITUD_MAXIMA_DESCRIPCION}.
     */
    public static String normalizarDescripcion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.strip();
        if (normalizado.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new ReglaNegocioException(
                    "La descripcion del canal de venta no puede exceder "
                            + LONGITUD_MAXIMA_DESCRIPCION + " caracteres.");
        }
        return normalizado;
    }
}

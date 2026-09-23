package com.dessti.crm.vertical.anuncios.permiso.domain;

import java.util.Locale;

/**
 * Tipos de {@link PermisoInstalacion} admitidos por el submodulo de permisos del
 * Req 17.1: {@code municipal} (autorizacion de la autoridad municipal) y
 * {@code arrendador} (autorizacion del propietario/arrendador del inmueble). Cada
 * constante conoce su etiqueta ASCII persistida en la columna
 * {@code permiso_instalacion.tipo} (VARCHAR con CHECK {@code IN ('municipal',
 * 'arrendador')} de la migracion V20), coherente con la convencion de etiquetas de
 * V14/V16/V17/V18/V19.
 */
public enum TipoPermisoInstalacion {

    /** Autorizacion emitida por la autoridad municipal (Req 17.1). */
    MUNICIPAL("municipal"),

    /** Autorizacion emitida por el propietario o arrendador del inmueble (Req 17.1). */
    ARRENDADOR("arrendador");

    private final String valorBd;

    TipoPermisoInstalacion(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta ASCII persistida en la BD (coincide con el CHECK de V20).
     *
     * @return la etiqueta de base de datos (por ejemplo {@code "municipal"}).
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Resuelve el tipo a partir de su etiqueta de base de datos (inversa de
     * {@link #valorBd()}). La comparacion es insensible a mayusculas y recorta
     * espacios.
     *
     * @param valor etiqueta persistida ({@code municipal}, {@code arrendador}).
     * @return el tipo correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o no corresponde a
     *         ningun tipo conocido.
     */
    public static TipoPermisoInstalacion desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de Permiso_Instalacion no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (TipoPermisoInstalacion tipo : values()) {
            if (tipo.valorBd.equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de Permiso_Instalacion desconocido: " + valor);
    }
}

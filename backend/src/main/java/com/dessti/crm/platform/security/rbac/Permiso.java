package com.dessti.crm.platform.security.rbac;

import java.util.Objects;

/**
 * Permiso atomico del modelo RBAC, expresado como la tupla
 * {@code (recurso, operacion)} (Req 3).
 *
 * <p>Un permiso atomico concede la capacidad de ejecutar una unica
 * {@code operacion} sobre un unico {@code recurso}, p. ej.
 * {@code cliente:crear} o {@code factura:timbrar}. La convencion textual es
 * {@code "<recurso>:<operacion>"} y coincide con el valor que se almacena como
 * {@code authority} en el {@link org.springframework.security.core.Authentication}
 * del Usuario (poblado a partir del JWT en la tarea 9.1 y de los roles en la
 * tarea 10.2).</p>
 *
 * <p>Este value object es inmutable y normaliza sus componentes a minusculas y
 * sin espacios para que la comparacion sea estable e insensible a mayusculas.</p>
 *
 * @param recurso   nombre del recurso protegido (p. ej. {@code cliente}); no
 *                  nulo ni en blanco.
 * @param operacion nombre de la operacion sobre el recurso (p. ej.
 *                  {@code crear}); no nulo ni en blanco.
 */
public record Permiso(String recurso, String operacion) {

    /** Separador de la representacion textual {@code recurso:operacion}. */
    public static final String SEPARADOR = ":";

    public Permiso {
        recurso = normalizar(recurso, "recurso");
        operacion = normalizar(operacion, "operacion");
    }

    /**
     * Crea un {@link Permiso} a partir de recurso y operacion.
     *
     * @param recurso   recurso protegido.
     * @param operacion operacion sobre el recurso.
     * @return el permiso atomico normalizado.
     */
    public static Permiso de(String recurso, String operacion) {
        return new Permiso(recurso, operacion);
    }

    /**
     * Crea un {@link Permiso} a partir de su representacion textual
     * {@code "recurso:operacion"}.
     *
     * @param authority cadena con formato {@code recurso:operacion}.
     * @return el permiso atomico correspondiente.
     * @throws IllegalArgumentException si el formato es invalido.
     */
    public static Permiso desdeAuthority(String authority) {
        if (authority == null) {
            throw new IllegalArgumentException("La authority del permiso no puede ser nula");
        }
        String[] partes = authority.split(SEPARADOR, -1);
        if (partes.length != 2) {
            throw new IllegalArgumentException(
                    "Formato de permiso invalido, se espera 'recurso:operacion': " + authority);
        }
        return new Permiso(partes[0], partes[1]);
    }

    /**
     * Devuelve la representacion textual {@code "recurso:operacion"} usada como
     * {@code authority} de Spring Security.
     *
     * @return la authority equivalente a este permiso.
     */
    public String authority() {
        return recurso + SEPARADOR + operacion;
    }

    @Override
    public String toString() {
        return authority();
    }

    private static String normalizar(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El " + campo + " del permiso no puede estar vacio");
        }
        String limpio = valor.strip().toLowerCase();
        if (limpio.contains(SEPARADOR)) {
            throw new IllegalArgumentException(
                    "El " + campo + " del permiso no puede contener '" + SEPARADOR + "': " + valor);
        }
        Objects.requireNonNull(limpio);
        return limpio;
    }
}

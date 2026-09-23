package com.dessti.crm.calidad.domain;

import java.util.Locale;

/**
 * Origen de una {@link NoConformidad} (Req 70.2, clausula 10.2). Etiquetas ASCII en
 * minusculas persistidas en {@code no_conformidad.origen} conforme al CHECK de V47.
 */
public enum OrigenNoConformidad {

    /** La No_Conformidad se origina en una Queja_Cliente (Req 70.1). */
    QUEJA("queja"),

    /** Detectada en una auditoria interna del SGC. */
    AUDITORIA_INTERNA("auditoria_interna"),

    /** Detectada en un proceso interno. */
    PROCESO("proceso"),

    /** Atribuible a un proveedor. */
    PROVEEDOR("proveedor"),

    /** Otro origen no clasificado. */
    OTRO("otro");

    private final String valorBd;

    OrigenNoConformidad(String valorBd) {
        this.valorBd = valorBd;
    }

    /**
     * Etiqueta persistida en {@code no_conformidad.origen}, en minusculas ASCII.
     *
     * @return la etiqueta de base de datos del origen.
     */
    public String valorBd() {
        return valorBd;
    }

    /**
     * Reconstruye el origen a partir de su etiqueta de base de datos.
     *
     * @param valor etiqueta almacenada (por ejemplo {@code 'queja'}).
     * @return el origen correspondiente.
     * @throws IllegalArgumentException si el valor es nulo o desconocido.
     */
    public static OrigenNoConformidad desdeValorBd(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El origen de la No_Conformidad no puede ser nulo");
        }
        String normalizado = valor.strip().toLowerCase(Locale.ROOT);
        for (OrigenNoConformidad origen : values()) {
            if (origen.valorBd.equals(normalizado)) {
                return origen;
            }
        }
        throw new IllegalArgumentException("Origen de No_Conformidad desconocido: " + valor);
    }
}
